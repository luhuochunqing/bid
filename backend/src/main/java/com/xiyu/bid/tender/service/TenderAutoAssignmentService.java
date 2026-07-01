package com.xiyu.bid.tender.service;

import com.xiyu.bid.crm.application.CustomerManagerResult;
import com.xiyu.bid.crm.application.CompanySearchResult;
import com.xiyu.bid.crm.application.CrmCustomerManagerLookupService;
import com.xiyu.bid.crm.application.CrmCompanySearchService;
import com.xiyu.bid.crm.domain.CrmProjectMappingRepository;
import com.xiyu.bid.crm.domain.CrmProjectMapping;
import com.xiyu.bid.crm.domain.AssignmentResult;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.task.service.UserEnabledStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenderAutoAssignmentService {

    private final CrmProjectMappingRepository mappingRepository;
    private final CrmCompanySearchService companySearchService;
    private final CrmCustomerManagerLookupService customerManagerLookupService;
    private final UserRepository userRepository;
    private final UserEnabledStatusService userEnabledStatusService;

    @Transactional(readOnly = true)
    public AssignmentResult tryAutoAssign(final Tender tender) {
        if (tender == null || !StringUtils.hasText(tender.getPurchaserName())) {
            log.debug("Skip: tender or purchaserName is null/blank");
            return AssignmentResult.noMatch();
        }

        String purchaserName = tender.getPurchaserName().trim();
        log.debug("Attempting auto-assignment for: {}", purchaserName);

        Optional<CrmProjectMapping> mapping =
                mappingRepository.findByPurchaserName(purchaserName);

        if (mapping.isPresent()) {
            CrmProjectMapping m = mapping.get();
            if (!StringUtils.hasText(m.getProjectManagerName())) {
                log.warn("Local mapping has no projectManagerName for purchaser={}, skipping", purchaserName);
                return AssignmentResult.noMatch();
            }
            log.info("Auto-assignment matched: tender={}, purchaser={}, "
                    + "manager={}, dept={}",
                    tender.getId(), purchaserName,
                    m.getProjectManagerName(), m.getDepartmentName());
            return AssignmentResult.success(
                    m.getCrmProjectId(),
                    m.getProjectManagerId(),
                    m.getProjectManagerName(),
                    m.getDepartmentId(),
                    m.getDepartmentName());
        }

        log.debug("No mapping found for: {}", purchaserName);
        return AssignmentResult.noMatch();
    }

    @Transactional
    public AssignmentResult autoAssignIfPossible(final Tender tender) {
        if (tender == null) {
            log.warn("Auto-assignment skipped: tender is null");
            return AssignmentResult.noMatch();
        }

        AssignmentResult result = tryAutoAssign(tender);

        if (result.isMatched()) {
            log.info("Tender {} assigned to manager {} ({}) from local mapping",
                    tender.getId(),
                    result.projectManagerName(),
                    result.projectManagerId());
            return result;
        }

        AssignmentResult crmResult = tryAutoAssignFromCrm(tender);
        if (crmResult.isMatched()) {
            log.info("Tender {} assigned to manager {} ({}) from CRM",
                    tender.getId(),
                    crmResult.projectManagerName(),
                    crmResult.projectManagerId());
            return crmResult;
        }

        log.debug("Tender {} remains PENDING (no local or CRM mapping)",
                tender.getId());
        return AssignmentResult.noMatch();
    }

    @Transactional(readOnly = true)
    public AssignmentResult tryAutoAssignFromCrm(final Tender tender) {
        if (tender == null || !StringUtils.hasText(tender.getPurchaserName())) {
            log.debug("tryAutoAssignFromCrm skipped: tender or purchaserName is null/blank");
            return AssignmentResult.noMatch();
        }

        String purchaserName = tender.getPurchaserName().trim();
        log.debug("Attempting CRM auto-assignment for: {}", purchaserName);

        try {
            Optional<CompanySearchResult> company =
                    companySearchService.searchByName(purchaserName);
            if (company.isEmpty()) {
                log.debug("CRM step1 no exact company match for: {}", purchaserName);
                return AssignmentResult.noMatch();
            }

            Optional<CustomerManagerResult> manager =
                    customerManagerLookupService.findByCompanyId(company.get().id());
            if (manager.isEmpty()) {
                log.debug("CRM step2 no manager for companyId={}", company.get().id());
                return AssignmentResult.noMatch();
            }

            String saleNo = manager.get().saleNo();
            if (!StringUtils.hasText(saleNo)) {
                log.debug("CRM step2 manager has no saleNo for companyId={}", company.get().id());
                return AssignmentResult.noMatch();
            }
            log.info("CRM auto-assignment matched: tender={}, purchaser={}, companyId={}, saleNo={}",
                    tender.getId(), purchaserName, company.get().id(), saleNo);

            String managerName = resolveManagerNameBySaleNo(saleNo);
            // CO-441 回归修复：如果 managerName 为 null（用户已停用/未匹配），不推进状态
            // 返回 noMatch() 保持 PENDING_ASSIGNMENT，等待人工分配
            if (!StringUtils.hasText(managerName)) {
                log.warn("CRM 返回的工号 {} 在本地 User 表中无匹配或已停用，返回 noMatch 保持 PENDING_ASSIGNMENT", saleNo);
                return AssignmentResult.noMatch();
            }

            return AssignmentResult.success(
                    null,
                    saleNo,
                    managerName,
                    null,
                    null
            );
        } catch (RuntimeException e) {
            log.warn("CRM auto-assignment failed for tender {}: {}",
                    tender.getId(), e.getMessage());
            return AssignmentResult.noMatch();
        }
    }

    /**
     * CO-441: 按 CRM 返回的工号（saleNo）反查本地 User 表的负责人姓名。
     * <p>查询顺序：
     * <ol>
     *   <li>先按 {@code employee_number} 字段查询（标准路径，B 修复后新同步的 OSS 用户会填充此字段）</li>
     *   <li>未命中时 fallback 到 {@code username} 字段查询（止血路径，覆盖历史 OSS 用户
     *       在 V1126 迁移前的场景，以及 employee_number 字段未填充的边缘情况）</li>
     * </ol>
     * <p>查到后通过 {@link UserEnabledStatusService#isEnabled} 判断用户启用状态，
     * 未启用用户返回 null（不分配给已停用账号）。OSS 用户以认证成功为准视为启用
     * （参考 PR !1382 统一 enabled 判断逻辑）。
     *
     * @param saleNo CRM 返回的工号
     * @return 启用状态的用户姓名，或 null（未匹配/已停用）
     */
    public String resolveManagerNameBySaleNo(final String saleNo) {
        if (!StringUtils.hasText(saleNo)) {
            return null;
        }
        Optional<User> userOpt = userRepository.findByEmployeeNumber(saleNo);
        if (userOpt.isEmpty()) {
            // CO-441 止血：fallback 到 username 字段查询（OSS 同步用户工号存在 username）
            userOpt = userRepository.findByUsername(saleNo);
        }
        if (userOpt.isEmpty()) {
            return null;
        }
        User user = userOpt.get();
        if (!userEnabledStatusService.isEnabled(user)) {
            log.warn("工号 {} 对应用户 {}（id={}）已停用，跳过自动分配", saleNo, user.getFullName(), user.getId());
            return null;
        }
        return user.getFullName();
    }

    /**
     * @deprecated 使用 {@link #resolveManagerNameBySaleNo(String)} 替代。
     * CO-441 前 OSS 同步用户 employee_number 为 NULL，按此方法查询会返回 null。
     */
    @Deprecated
    public String resolveManagerNameByEmployeeNumber(final String employeeNumber) {
        return resolveManagerNameBySaleNo(employeeNumber);
    }
}
