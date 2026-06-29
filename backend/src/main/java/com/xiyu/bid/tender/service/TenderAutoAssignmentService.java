package com.xiyu.bid.tender.service;

import com.xiyu.bid.crm.application.CrmCompanySearchService;
import com.xiyu.bid.crm.application.CrmCustomerManagerLookupService;
import com.xiyu.bid.crm.application.CompanySearchResult;
import com.xiyu.bid.crm.application.CustomerManagerResult;
import com.xiyu.bid.crm.domain.CrmProjectMapping;
import com.xiyu.bid.crm.domain.CrmProjectMappingRepository;
import com.xiyu.bid.crm.domain.AssignmentResult;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 标讯自动分配服务.
 *
 * <p>职责：在标讯创建后，根据业主单位名称自动匹配 CRM 项目负责人，
 * 实现自动分配。匹配失败时保持 PENDING_ASSIGNMENT 状态，等待手动分配。
 *
 * <p>集成点：
 * <ul>
 *   <li>查询 CrmProjectMapping 表进行本地匹配</li>
 *   <li>调用 CRM 两步反查链路（接口 25338 → 接口 25259）实时查询</li>
 *   <li>记录分配日志（INFO/DEBUG 级别，不影响业务流程）</li>
 * </ul>
 *
 * <p>副作用边界：
 * <ul>
 *   <li>Repository 查询（CrmProjectMappingRepository）</li>
 *   <li>CRM HTTP 调用（CrmCompanySearchService + CrmCustomerManagerLookupService）</li>
 *   <li>日志记录</li>
 * </ul>
 */
@Service
public class TenderAutoAssignmentService {

    /** 日志. */
    private static final Logger LOG = LoggerFactory.getLogger(
            TenderAutoAssignmentService.class);

    /** 映射仓储. */
    private final CrmProjectMappingRepository mappingRepository;

    /** CRM 公司查询服务（接口 25338，反查第一步）. */
    private final CrmCompanySearchService companySearchService;

    /** CRM 客户负责人查询服务（接口 25259，反查第二步）. */
    private final CrmCustomerManagerLookupService customerManagerLookupService;

    /** 本地用户仓储，用于按工号反查姓名（接口 25259 只返回工号）. */
    private final UserRepository userRepository;

    /**
     * 构造器.
     *
     * @param repo 映射仓储
     * @param companySearchService CRM 公司查询服务（接口 25338）
     * @param customerManagerLookupService CRM 客户负责人查询服务（接口 25259）
     * @param userRepository 本地用户仓储（按工号反查姓名）
     */
    public TenderAutoAssignmentService(
            final CrmProjectMappingRepository repo,
            final CrmCompanySearchService companySearchService,
            final CrmCustomerManagerLookupService customerManagerLookupService,
            final UserRepository userRepository) {
        this.mappingRepository = repo;
        this.companySearchService = companySearchService;
        this.customerManagerLookupService = customerManagerLookupService;
        this.userRepository = userRepository;
    }

    /**
     * 根据标讯的业主单位名称尝试自动分配.
     *
     * @param tender 标讯实体
     * @return 分配结果
     */
    @Transactional(readOnly = true)
    public AssignmentResult tryAutoAssign(final Tender tender) {
        if (tender == null || !hasText(tender.getPurchaserName())) {
            LOG.debug("Skip: tender or purchaserName is null/blank");
            return AssignmentResult.noMatch();
        }

        String purchaserName = tender.getPurchaserName().trim();
        LOG.debug("Attempting auto-assignment for: {}", purchaserName);

        Optional<CrmProjectMapping> mapping =
                mappingRepository.findByPurchaserName(purchaserName);

        if (mapping.isPresent()) {
            CrmProjectMapping m = mapping.get();
            if (!hasText(m.getProjectManagerName())) {
                LOG.warn("Local mapping has no projectManagerName for purchaser={}, skipping", purchaserName);
                return AssignmentResult.noMatch();
            }
            LOG.info("Auto-assignment matched: tender={}, purchaser={}, "
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

        LOG.debug("No mapping found for: {}", purchaserName);
        return AssignmentResult.noMatch();
    }

    /**
     * 根据标讯创建后自动尝试分配.
     *
     * <p>此方法应在标讯保存后调用。
     * 分配成功后由调用方更新标讯状态为 TRACKING。
     *
     * <p>匹配策略：
     * <ol>
     *   <li>先查本地 CrmProjectMapping 映射表</li>
     *   <li>本地匹配失败时，调用 CRM 两步反查链路实时查询</li>
     * </ol>
     *
     * @param tender 标讯实体（已保存，带 ID）
     * @return 分配结果；{@code isMatched()} 为 true 表示成功匹配并分配
     */
    @Transactional
    public AssignmentResult autoAssignIfPossible(final Tender tender) {
        if (tender == null) {
            LOG.warn("Auto-assignment skipped: tender is null");
            return AssignmentResult.noMatch();
        }

        // 1. 先尝试本地映射表匹配
        AssignmentResult result = tryAutoAssign(tender);

        if (result.isMatched()) {
            LOG.info("Tender {} assigned to manager {} ({}) from local mapping",
                    tender.getId(),
                    result.projectManagerName(),
                    result.projectManagerId());
            return result;
        }

        // 2. 本地匹配失败，尝试 CRM 两步反查链路
        AssignmentResult crmResult = tryAutoAssignFromCrm(tender);
        if (crmResult.isMatched()) {
            LOG.info("Tender {} assigned to manager {} ({}) from CRM",
                    tender.getId(),
                    crmResult.projectManagerName(),
                    crmResult.projectManagerId());
            return crmResult;
        }

        LOG.debug("Tender {} remains PENDING (no local or CRM mapping)",
                tender.getId());
        return AssignmentResult.noMatch();
    }

    /**
     * 通过 CRM 两步反查链路实时查询客户负责人.
     *
     * <p>对应 CO-302 issue 5.3「CRM 反查的查询路径」：
     * <ol>
     *   <li>用"招标主体名称"调接口 25338 查 CRM 公司，精确匹配优先，获取客户 ID</li>
     *   <li>用客户 ID 调接口 25259 查客户负责人列表</li>
     *   <li>取第一条有效负责人（saleNo 非空）作为标讯的项目负责人</li>
     * </ol>
     * 任一环节查不到 → noMatch，不阻塞主流程。
     *
     * <p>注意：接口 25259 只返回 saleNo（工号），不返回姓名，
     * 因此 {@code projectManagerName} 为 null，用工号匹配即可。
     *
     * @param tender 标讯实体
     * @return 分配结果；{@code isMatched()} 为 false 表示未找到
     */
    @Transactional(readOnly = true)
    public AssignmentResult tryAutoAssignFromCrm(final Tender tender) {
        if (tender == null || !hasText(tender.getPurchaserName())) {
            LOG.debug("tryAutoAssignFromCrm skipped: tender or purchaserName is null/blank");
            return AssignmentResult.noMatch();
        }

        String purchaserName = tender.getPurchaserName().trim();
        LOG.debug("Attempting CRM auto-assignment for: {}", purchaserName);

        try {
            // 第一步：按招标主体名称查公司，精确匹配优先
            Optional<CompanySearchResult> company =
                    companySearchService.searchByName(purchaserName);
            if (company.isEmpty()) {
                LOG.debug("CRM step1 no exact company match for: {}", purchaserName);
                return AssignmentResult.noMatch();
            }

            // 第二步：按公司 ID 查客户负责人
            Optional<CustomerManagerResult> manager =
                    customerManagerLookupService.findByCompanyId(company.get().id());
            if (manager.isEmpty()) {
                LOG.debug("CRM step2 no manager for companyId={}", company.get().id());
                return AssignmentResult.noMatch();
            }

            String saleNo = manager.get().saleNo();
            if (!hasText(saleNo)) {
                LOG.debug("CRM step2 manager has no saleNo for companyId={}", company.get().id());
                return AssignmentResult.noMatch();
            }
            LOG.info("CRM auto-assignment matched: tender={}, purchaser={}, companyId={}, saleNo={}",
                    tender.getId(), purchaserName, company.get().id(), saleNo);

            // 接口 25259 只返回工号，按工号查本地 User 表补齐姓名
            String managerName = resolveManagerNameByEmployeeNumber(saleNo);
            if (!hasText(managerName)) {
                LOG.debug("CRM auto-assignment: no local user found for saleNo={}, keeping PENDING", saleNo);
                return AssignmentResult.noMatch();
            }

            return AssignmentResult.success(
                    null, // crmProjectId 不需要
                    saleNo,
                    managerName, // projectManagerName：本地 User 表按工号反查
                    null, // departmentId 不需要
                    null  // departmentName 不需要
            );
        } catch (RuntimeException e) {
            LOG.warn("CRM auto-assignment failed for tender {}: {}",
                    tender.getId(), e.getMessage());
            return AssignmentResult.noMatch();
        }
    }

    private boolean hasText(final String value) {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * 按工号查本地 User 表反查姓名.
     *
     * <p>接口 25259 只返回 saleNo（工号），不返回姓名。
     * 本方法用工号查本地 User 表补齐 projectManagerName。
     * 查不到时返回 null（工号仍是有效匹配，姓名非必需）。
     *
     * @param employeeNumber 工号（来自 CRM 接口 25259 的 saleNo）
     * @return 用户姓名；null 表示本地无此工号
     */
    private String resolveManagerNameByEmployeeNumber(final String employeeNumber) {
        if (!hasText(employeeNumber)) {
            return null;
        }
        return userRepository.findByEmployeeNumber(employeeNumber)
                .map(User::getFullName)
                .orElseGet(() -> {
                    LOG.warn("CRM 返回的工号 {} 在本地 User 表中无匹配，projectManagerName 为 null", employeeNumber);
                    return null;
                });
    }
}
