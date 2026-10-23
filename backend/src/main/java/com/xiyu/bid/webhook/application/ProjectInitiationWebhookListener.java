// Input: 监听 ProjectInitiationChangedEvent
// Output: 入队 WebhookDeliveryTask（bidInfoList 格式 + operator_username）
// Pos: webhook/application/
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.webhook.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.crm.infrastructure.dto.BidInfoInnerDTO;
import com.xiyu.bid.crm.infrastructure.dto.BidInfoSyncDTO;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.project.domain.ProjectInitiationChangedEvent;
import com.xiyu.bid.project.entity.ProjectInitiationDetails;
import com.xiyu.bid.project.repository.ProjectInitiationDetailsRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.webhook.infrastructure.WebhookDeliveryTask;
import com.xiyu.bid.webhook.infrastructure.WebhookDeliveryTaskRepository;
import com.xiyu.bid.webhook.infrastructure.WebhookDeliveryTaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 监听 {@link ProjectInitiationChangedEvent}，组装 CRM 原生 bidInfoList 格式后入队 {@link WebhookDeliveryTask}。
 * <p>立项审核通过或立项后修改"计划入围供应商数量"和"招标文件不利项"时，将这两个字段实时同步至 CRM。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectInitiationWebhookListener {

    private static final DateTimeFormatter STATUS_EDIT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String EVENT_TYPE = "project.initiation_updated";
    private static final String SYSTEM_NAME = "西域数智化投标管理平台";

    private final WebhookDeliveryTaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final TenderRepository tenderRepository;
    private final ProjectInitiationDetailsRepository initiationDetailsRepository;
    private final ObjectMapper objectMapper;
    private final TenderCrmOpportunityCodeResolver tenderCrmOpportunityCodeResolver;
    private final OperatorUsernameResolver operatorUsernameResolver;
    private final UserRepository userRepository;

    @Value("${webhook.crm.url:}")
    private String crmWebhookUrl;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onInitiationChanged(ProjectInitiationChangedEvent event) {
        log.info("ProjectInitiationWebhookListener received ProjectInitiationChangedEvent: projectId={}, operatorUserId={}",
                event.projectId(), event.operatorUserId());
        if (crmWebhookUrl == null || crmWebhookUrl.isBlank()) {
            log.warn("CRM webhook URL not configured, skipping enqueue for project {}", event.projectId());
            return;
        }

        Project project = projectRepository.findById(event.projectId()).orElse(null);
        if (project == null || project.getTenderId() == null) {
            log.info("Project {} has no associated tender, skipping initiation webhook", event.projectId());
            return;
        }

        Tender tender = tenderRepository.findById(project.getTenderId()).orElse(null);
        if (tender == null) {
            log.warn("Tender {} not found for project {}, skipping initiation webhook", project.getTenderId(), event.projectId());
            return;
        }

        String operatorUsername = operatorUsernameResolver.resolveForCrmLookup(tender, event.operatorUserId());
        if (operatorUsername == null || operatorUsername.isBlank()) {
            log.error("Webhook 入队失败：无可用 OSS username，跳过入队。projectId={}, tenderId={}, operatorUserId={}",
                    event.projectId(), tender.getId(), event.operatorUserId());
            return;
        }

        String crmOpportunityCode = tenderCrmOpportunityCodeResolver.resolveForTender(tender, operatorUsername);
        if (crmOpportunityCode == null || crmOpportunityCode.isBlank()) {
            log.info("Tender {} CRM opportunity code resolution failed or not bound, skipping initiation webhook", tender.getId());
            return;
        }

        ProjectInitiationDetails initiation = initiationDetailsRepository.findByProjectId(event.projectId()).orElse(null);
        String crmOpportunityName = tender.getCrmOpportunityName() != null ? tender.getCrmOpportunityName() : "";
        String operatorDisplayName = resolveOperatorDisplayName(event.operatorUserId(), operatorUsername);

        String payload = buildPayload(event, tender.getId(), crmOpportunityCode, crmOpportunityName,
                operatorDisplayName, initiation);

        taskRepository.save(WebhookDeliveryTask.builder()
                .tenderId(tender.getId())
                .externalId(tender.getExternalId())
                .operatorUsername(operatorUsername)
                .targetUrl(crmWebhookUrl)
                .eventType(EVENT_TYPE)
                .businessKey(buildBusinessKey(event))
                .payload(payload)
                .status(WebhookDeliveryTaskStatus.PENDING)
                .build());

        log.info("Webhook delivery task enqueued for initiation update: projectId={}, tenderId={}, crmOpportunityCode={}, operatorUsername={}",
                event.projectId(), tender.getId(), crmOpportunityCode, operatorUsername);
    }

    private String buildBusinessKey(ProjectInitiationChangedEvent event) {
        return "%s:%s:%s".formatted(event.projectId(), "INITIATION_UPDATED", event.occurredAt());
    }

    private String resolveOperatorDisplayName(Long operatorUserId, String fallback) {
        if (operatorUserId == null) return fallback;
        return userRepository.findById(operatorUserId)
                .map(u -> u.getFullName() != null && !u.getFullName().isBlank() ? u.getFullName() : u.getUsername())
                .orElse(fallback);
    }

    private String buildPayload(ProjectInitiationChangedEvent event, Long tenderId,
                                String crmOpportunityCode, String crmOpportunityName,
                                String operatorDisplayName, ProjectInitiationDetails initiation) {
        try {
            String statusEditTime = event.occurredAt().format(STATUS_EDIT_TIME_FORMAT);
            BidInfoInnerDTO inner = new BidInfoInnerDTO(
                    crmOpportunityName,
                    crmOpportunityCode != null ? crmOpportunityCode : "",
                    null, // status 为 null，保证不意外修改 CRM 商机状态
                    operatorDisplayName,
                    statusEditTime,
                    buildFeedback(event, operatorDisplayName, initiation),
                    tenderId
            );
            BidInfoSyncDTO dto = new BidInfoSyncDTO(List.of(inner));
            return objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize bidInfoSync payload for initiation", ex);
        }
    }

    private String buildFeedback(ProjectInitiationChangedEvent event, String operator,
                                  ProjectInitiationDetails initiation) {
        Map<String, Object> fb = new LinkedHashMap<>();
        fb.put("reason", "");
        fb.put("vendor", "");
        fb.put("paymentTerm", "");
        fb.put("remark", "");
        fb.put("operator", operator);
        fb.put("operateTime", event.occurredAt().format(STATUS_EDIT_TIME_FORMAT));
        fb.put("systemName", SYSTEM_NAME);
        fb.put("planSupplierCount", initiation != null ? initiation.getExpectedBidders() : null);
        fb.put("bidDocumentDisadvantage", initiation != null && initiation.getTenderAdverseItems() != null
                ? initiation.getTenderAdverseItems() : "");
        try {
            return objectMapper.writeValueAsString(fb);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize feedback", ex);
        }
    }
}
