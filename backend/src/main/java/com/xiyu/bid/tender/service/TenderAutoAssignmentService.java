package com.xiyu.bid.tender.service;

import com.xiyu.bid.crm.application.CustomerManagerResult;
import com.xiyu.bid.crm.application.CompanySearchResult;
import com.xiyu.bid.crm.domain.CrmProjectMappingRepository;
import com.xiyu.bid.crm.domain.CrmProjectMapping;
import com.xiyu.bid.crm.domain.AssignmentResult;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.task.service.UserEnabledStatusService;
import com.xiyu.bid.tender.crm.CachedCrmLookupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * 标讯自动分配：本地映射优先，再 CRM 两步反查（招标主体 → 公司 → 集团项目经理）。
 *
 * <p><b>契约（D1）</b>：调用方必须显式传入 {@code operatorUsername}。
 * CRM 反查依赖操作人 OSS token 换票；无操作人时仅尝试本地映射，不假装可调 CRM。
 *
 * <p><b>批量导入</b>：导入循环外包一层 {@link CachedCrmLookupService#openBatch()}，
 * 同一招标主体只打一次 CRM（031 R-007）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenderAutoAssignmentService {

    private final CrmProjectMappingRepository mappingRepository;
    private final CachedCrmLookupService crmLookupService;
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

    /**
     * 尝试自动分配：先本地映射，再 CRM 反查。
     *
     * @param tender           已落库标讯（purchaserName = 招标主体）
     * @param operatorUsername 操作人 username（OSS 缓存键）。批量导入/人工录入必须传；
     *                         外部无操作人上下文时显式传 {@code null}（仅本地映射，不调 CRM）
     */
    @Transactional
    public AssignmentResult autoAssignIfPossible(final Tender tender, final String operatorUsername) {
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

        // D2：无操作人则不进 CRM（避免 TokenUnavailable 空跑）
        if (!StringUtils.hasText(operatorUsername)) {
            log.warn("Tender {} CRM auto-assign skipped: operator username is empty "
                    + "(only local mapping tried; pass login user for OSS→CRM token)",
                    tender.getId());
            return AssignmentResult.noMatch();
        }

        AssignmentResult crmResult = tryAutoAssignFromCrm(tender, operatorUsername);
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
    public AssignmentResult tryAutoAssignFromCrm(final Tender tender, String username) {
        if (tender == null || !StringUtils.hasText(tender.getPurchaserName())) {
            log.debug("tryAutoAssignFromCrm skipped: tender or purchaserName is null/blank");
            return AssignmentResult.noMatch();
        }
        if (!StringUtils.hasText(username)) {
            log.debug("tryAutoAssignFromCrm skipped: operator username empty");
            return AssignmentResult.noMatch();
        }

        String purchaserName = tender.getPurchaserName().trim();
        log.debug("Attempting CRM auto-assignment for: {}", purchaserName);

        try {
            Optional<CompanySearchResult> company =
                    crmLookupService.searchByName(purchaserName, username);
            if (company.isEmpty()) {
                log.debug("CRM step1 no exact company match for: {}", purchaserName);
                return AssignmentResult.noMatch();
            }

            Optional<CustomerManagerResult> manager =
                    crmLookupService.findByCompanyId(company.get().id(), username);
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
            // CO-441：managerName 为空（停用/未匹配）不推进状态
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
     */
    public String resolveManagerNameBySaleNo(final String saleNo) {
        if (!StringUtils.hasText(saleNo)) {
            return null;
        }
        Optional<User> userOpt = userRepository.findByEmployeeNumber(saleNo);
        if (userOpt.isEmpty()) {
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
     */
    @Deprecated
    public String resolveManagerNameByEmployeeNumber(final String employeeNumber) {
        return resolveManagerNameBySaleNo(employeeNumber);
    }
}
