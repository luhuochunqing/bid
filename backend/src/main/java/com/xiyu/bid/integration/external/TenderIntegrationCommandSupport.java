package com.xiyu.bid.integration.external;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.project.service.ProjectManagerDepartmentEnricher;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.tender.service.TenderAssignmentNotifier;
import com.xiyu.bid.tender.service.TenderAutoAssignmentService;
import com.xiyu.bid.crm.domain.AssignmentResult;
import com.xiyu.bid.batch.core.TenderStatusTransitionPolicy;
import com.xiyu.bid.webhook.domain.TenderStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * CO-305: 提取 TenderIntegrationCommandService 的辅助方法，
 * 保持主服务在 300 行以内。
 */
@Component
@RequiredArgsConstructor
@Slf4j
class TenderIntegrationCommandSupport {

    private final CrmTenderLinkService crmTenderLinkService;
    private final TenderAutoAssignmentService autoAssignmentService;
    private final TenderAssignmentNotifier assignmentNotifier;
    private final ApplicationEventPublisher eventPublisher;
    private final TenderRepository tenderRepository;
    private final ProjectManagerIdResolver projectManagerIdResolver;
    /** 部门名反查：复用 ProjectManagerDepartmentEnricher（与 TenderCommandService 保持一致）。 */
    private final ProjectManagerDepartmentEnricher departmentEnricher;
    private final UserRepository userRepository;

    /**
     * CO-302: 尝试自动分配标讯负责人.
     * <p>匹配策略：先查本地 CrmProjectMapping 映射表，失败后调 CRM 商机接口实时查询。
     * <p>降级策略：匹配失败保持 PENDING_ASSIGNMENT 状态，不影响标讯入库。
     * <p>优先级：如果标讯已有 CRM 商机负责人（projectManagerId 或 projectManagerName），
     * 不再自动分配，避免覆盖商机负责人。
     *
     * @param userId 外部推送的 API Key 创建者或标讯创建者；用于写入 webhook 事件
     */
    void tryAutoAssign(Tender tender, Long userId) {
        if (tender.getProjectManagerId() != null || hasText(tender.getProjectManagerName())) {
            log.info("Tender {} already has project manager (id={}, name={}), skip auto-assignment",
                    tender.getId(), tender.getProjectManagerId(), tender.getProjectManagerName());
            return;
        }
        try {
            // 外部推送无登录操作人：显式传 null → 仅本地映射，不调 CRM 反查（D1 契约）
            AssignmentResult result = autoAssignmentService.autoAssignIfPossible(tender, null);
            if (result.isMatched()) {
                applyAssignmentResult(tender, result);
                try {
                    TenderStatusTransitionPolicy.assertTransition(tender.getStatus(), Tender.Status.TRACKING);
                    tender.setStatus(Tender.Status.TRACKING);
                    String operatorName = userId != null
                            ? userRepository.findById(userId).map(User::getFullName).orElse(null)
                            : null;
                    eventPublisher.publishEvent(TenderStatusChangedEvent.of(
                            tender.getId(), tender.getExternalId(),
                            Tender.Status.PENDING_ASSIGNMENT, Tender.Status.TRACKING,
                            tender.getTitle(), null, userId, operatorName, null, null));
                    log.info("Tender {} auto-assigned from external platform, status changed to TRACKING", tender.getId());
                    assignmentNotifier.notifyAutoAssigned(tender);
                } catch (RuntimeException e) {
                    log.warn("Tender {} status transition failed (current status={}), but project manager is still updated: {}",
                            tender.getId(), tender.getStatus(), e.getMessage());
                }
                tenderRepository.save(tender);
            }
        } catch (RuntimeException e) {
            log.warn("Auto-assignment failed for external tender {}, keeping PENDING_ASSIGNMENT: {}",
                    tender.getId(), e.getMessage());
        }
    }

    void applyAssignmentResult(Tender tender, AssignmentResult result) {
        tender.setProjectManagerName(result.projectManagerName());
        if (result.projectManagerName() != null) {
            Long resolvedId = projectManagerIdResolver.resolveByFullName(result.projectManagerName());
            if (resolvedId != null) {
                tender.setProjectManagerId(resolvedId);
            } else {
                log.warn("External assignment: projectManagerName '{}' cannot be resolved to a user id, "
                                + "projectManagerId remains null for tender {}",
                        result.projectManagerName(), tender.getId());
            }
        }
        // 部门名：优先用 CRM/mapping 返回的，为空时从 projectManagerId 反查 user 部门
        // 与 TenderCommandService.applyAssignmentResult 保持一致逻辑
        String departmentName = result.departmentName();
        if (StringUtils.isBlank(departmentName) && tender.getProjectManagerId() != null) {
            departmentName = departmentEnricher.resolveDepartmentNameByUserId(tender.getProjectManagerId());
        }
        tender.setDepartment(departmentName);
    }

    /**
     * CRM 推送兜底：当 crmId 和 crmOpportunityCode 均为空时，用 externalId 反查 chanceId。
     */
    void applyCrmFallback(Tender tender, String crmId, String crmOpportunityCode, String crmOpportunityName) {
        boolean hasCrmId = crmId != null && !crmId.isBlank();
        boolean hasCode = crmOpportunityCode != null && !crmOpportunityCode.isBlank();
        if (!hasCrmId && !hasCode) {
            if (tender.getCrmOpportunityId() == null || tender.getCrmOpportunityId().isBlank()) {
                boolean linked = crmTenderLinkService.linkByChanceIdIfPresent(
                        tender,
                        tender.getExternalId() != null ? tender.getExternalId().split(":")[0] : null,
                        tender.getExternalId() != null && tender.getExternalId().contains(":")
                                ? tender.getExternalId().split(":")[1] : null);
                if (linked) {
                    tender.setSourceType(Tender.SourceType.CRM_OPPORTUNITY);
                    tender.setSource(Tender.SourceType.CRM_OPPORTUNITY.getLabel());
                }
            }
            return;
        }

        tender.setEvaluationSource(Tender.EvaluationSource.CRM_PUSH);
        tender.setStatus(Tender.Status.EVALUATED);
        // 仅当 code 是 CC 格式编号（非纯数字）时才直接存入
        // 纯数字是 CRM 推送误传的主键 id（CO-277），需通过 applyCrmLinkAndAssignment 反查 CC 编号
        if (hasCode && !crmOpportunityCode.trim().matches("\\d+")
                && (tender.getCrmOpportunityId() == null || tender.getCrmOpportunityId().isBlank())) {
            tender.setCrmOpportunityId(crmOpportunityCode);
        }
        // 仅当 crmOpportunityId 已设置时才存入 name，避免"半关联"状态（code=null 但 name 有值）导致去重校验失效
        if (crmOpportunityName != null && !crmOpportunityName.isBlank()
                && (tender.getCrmOpportunityName() == null || tender.getCrmOpportunityName().isBlank())
                && (tender.getCrmOpportunityId() != null && !tender.getCrmOpportunityId().isBlank())) {
            tender.setCrmOpportunityName(crmOpportunityName);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
