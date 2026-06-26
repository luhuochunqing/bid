package com.xiyu.bid.integration.external;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.tender.service.TenderAssignmentNotifier;
import com.xiyu.bid.tender.service.TenderAutoAssignmentService;
import com.xiyu.bid.crm.domain.AssignmentResult;
import com.xiyu.bid.batch.core.TenderStatusTransitionPolicy;
import com.xiyu.bid.webhook.domain.TenderStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    /**
     * CO-302: 尝试自动分配标讯负责人.
     * <p>匹配策略：先查本地 CrmProjectMapping 映射表，失败后调 CRM 商机接口实时查询。
     * <p>降级策略：匹配失败保持 PENDING_ASSIGNMENT 状态，不影响标讯入库。
     * <p>优先级：如果标讯已有 CRM 商机负责人（projectManagerId 或 projectManagerName），
     * 不再自动分配，避免覆盖商机负责人。
     */
    void tryAutoAssign(Tender tender) {
        if (tender.getProjectManagerId() != null || hasText(tender.getProjectManagerName())) {
            log.info("Tender {} already has project manager (id={}, name={}), skip auto-assignment",
                    tender.getId(), tender.getProjectManagerId(), tender.getProjectManagerName());
            return;
        }
        try {
            AssignmentResult result = autoAssignmentService.autoAssignIfPossible(tender);
            if (result.isMatched()) {
                applyAssignmentResult(tender, result);
                try {
                    TenderStatusTransitionPolicy.assertTransition(tender.getStatus(), Tender.Status.TRACKING);
                    tender.setStatus(Tender.Status.TRACKING);
                    eventPublisher.publishEvent(TenderStatusChangedEvent.of(
                            tender.getId(), tender.getExternalId(),
                            Tender.Status.PENDING_ASSIGNMENT, Tender.Status.TRACKING,
                            tender.getTitle()));
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
        tender.setDepartment(result.departmentName());
    }

    /**
     * CRM 推送时如果商机 ID 为空，做兜底关联（按 externalId 反查 chanceId）。
     */
    void applyCrmFallback(Tender tender, String crmId, String crmOpportunityName) {
        if (crmId == null || crmId.isBlank()) {
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
        if (tender.getCrmOpportunityId() == null || tender.getCrmOpportunityId().isBlank()) {
            // CO-277: 纯数字 crmId 不存入，保持 null 让外层 linkByChanceIdIfPresent 兜底
            if (crmId == null || crmId.isBlank() || !crmId.trim().matches("\\d+")) {
                tender.setCrmOpportunityId(crmId);
            }
        }
        if (crmOpportunityName != null && !crmOpportunityName.isBlank()
                && (tender.getCrmOpportunityName() == null || tender.getCrmOpportunityName().isBlank())) {
            tender.setCrmOpportunityName(crmOpportunityName);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
