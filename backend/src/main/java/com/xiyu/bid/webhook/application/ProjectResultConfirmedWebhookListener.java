// Input: ProjectResultConfirmedEvent
// Output: 入队 WebhookDeliveryTask（复用 §4.1 的 1min/5min/15min 重试机制）
// Pos: webhook/application/
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.webhook.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.crm.infrastructure.dto.ProjectResultCallbackPayload;
import com.xiyu.bid.project.domain.ProjectResultConfirmedEvent;
import com.xiyu.bid.project.service.ProjectResultPayloadAssembler;
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

/**
 * §4.2 项目结果确认回调监听器。
 * <p>监听 {@link ProjectResultConfirmedEvent}，组装 §4.2 载荷后入队 {@link WebhookDeliveryTask}。
 * <p>复用 §4.1 的重试机制：1min/5min/15min 重试 3 次，仍失败进入死信队列。
 * <p>统一配置项 {@code webhook.crm.url}（与 §4.1 共用），不再单独配置回调地址。
 * <p>event_type 区分 §4.1/§4.2：{@code project.result_confirmed} vs {@code tender.status_changed}。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectResultConfirmedWebhookListener {

    private static final String EVENT_TYPE = "project.result_confirmed";

    private final WebhookDeliveryTaskRepository taskRepository;
    private final ProjectResultPayloadAssembler payloadAssembler;
    private final ObjectMapper objectMapper;

    @Value("${webhook.crm.url:}")
    private String crmWebhookUrl;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onProjectResultConfirmed(ProjectResultConfirmedEvent event) {
        log.info("Received ProjectResultConfirmedEvent: projectId={}, tenderId={}, resultType={}, resultId={}",
                event.projectId(), event.tenderId(), event.resultType(), event.resultId());
        if (crmWebhookUrl == null || crmWebhookUrl.isBlank()) {
            log.warn("CRM webhook URL not configured, skipping enqueue for project {}", event.projectId());
            return;
        }
        ProjectResultCallbackPayload payload = payloadAssembler.assemble(event);
        if (payload == null) {
            log.warn("Payload assembly returned null, skip enqueue for project {}", event.projectId());
            return;
        }
        taskRepository.save(WebhookDeliveryTask.builder()
                .tenderId(event.tenderId())
                .externalId(null)
                .targetUrl(crmWebhookUrl)
                .eventType(EVENT_TYPE)
                .businessKey(buildBusinessKey(event))
                .payload(serialize(payload))
                .status(WebhookDeliveryTaskStatus.PENDING)
                .build());
        log.info("Webhook delivery task enqueued for project {}, resultType={}, url={}",
                event.projectId(), event.resultType(), crmWebhookUrl);
    }

    private String buildBusinessKey(ProjectResultConfirmedEvent event) {
        return "%s:%s:%s".formatted(event.projectId(), event.resultType().name(), event.occurredAt());
    }

    private String serialize(ProjectResultCallbackPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize project.result_confirmed payload", ex);
        }
    }
}
