package com.xiyu.bid.tender.service;

import com.xiyu.bid.crm.application.CrmCustomerLeaderQueryService;
import com.xiyu.bid.crm.application.CustomerLeaderResult;
import com.xiyu.bid.crm.domain.CrmProjectMapping;
import com.xiyu.bid.crm.domain.CrmProjectMappingRepository;
import com.xiyu.bid.crm.domain.AssignmentResult;
import com.xiyu.bid.entity.Tender;
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
 *   <li>调用 CRM 客户负责人查询服务实时查询</li>
 *   <li>记录分配日志（INFO/DEBUG 级别，不影响业务流程）</li>
 * </ul>
 *
 * <p>副作用边界：
 * <ul>
 *   <li>Repository 查询（CrmProjectMappingRepository）</li>
 *   <li>CRM HTTP 调用（CrmCustomerLeaderQueryService）</li>
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

    /** CRM 客户负责人查询服务. */
    private final CrmCustomerLeaderQueryService customerLeaderQueryService;

    /**
     * 构造器.
     *
     * @param repo 映射仓储
     * @param customerLeaderQueryService CRM 客户负责人查询服务
     */
    public TenderAutoAssignmentService(
            final CrmProjectMappingRepository repo,
            final CrmCustomerLeaderQueryService customerLeaderQueryService) {
        this.mappingRepository = repo;
        this.customerLeaderQueryService = customerLeaderQueryService;
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
     *   <li>本地匹配失败时，调用 CRM 商机接口实时查询</li>
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

        // 2. 本地匹配失败，尝试 CRM 实时查询
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
     * 通过 CRM 商机接口实时查询客户负责人.
     *
     * <p>按标讯的 purchaserName（招标主体）作为 groupName 查询 CRM 商机，
     * 取出第一条商机的项目负责人信息。
     *
     * <p>降级策略：查询失败或未找到返回 noMatch，不影响主流程。
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
            CustomerLeaderResult leader =
                    customerLeaderQueryService.findLeaderByGroupName(purchaserName);

            if (leader == null) {
                LOG.debug("No CRM leader found for: {}", purchaserName);
                return AssignmentResult.noMatch();
            }

            LOG.info("CRM auto-assignment matched: tender={}, purchaser={}, leader={}, leaderNo={}",
                    tender.getId(), purchaserName,
                    leader.projectLeaderName(), leader.projectLeaderNo());

            return AssignmentResult.success(
                    null, // crmProjectId 不需要
                    leader.projectLeaderNo(),
                    leader.projectLeaderName(),
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
}
