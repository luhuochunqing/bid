package com.xiyu.bid.webhook.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.webhook.domain.TenderStatusChangedEvent;
import com.xiyu.bid.webhook.infrastructure.WebhookDeliveryTask;
import com.xiyu.bid.webhook.infrastructure.WebhookDeliveryTaskRepository;
import com.xiyu.bid.webhook.infrastructure.WebhookDeliveryTaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebhookEventListener {
    private final WebhookDeliveryTaskRepository taskRepository;
    private final ObjectMapper objectMapper;

    @Value("${webhook.crm.url:}")
    private String crmWebhookUrl;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTenderStatusChanged(TenderStatusChangedEvent event) {
        if (crmWebhookUrl == null || crmWebhookUrl.isBlank()) {
            log.debug("CRM webhook URL not configured, skipping enqueue for tender {}", event.tenderId());
            return;
        }
        taskRepository.save(WebhookDeliveryTask.builder()
                .tenderId(event.tenderId())
                .externalId(event.externalId())
                .targetUrl(crmWebhookUrl)
                .eventType("tender.status_changed")
                .businessKey(buildBusinessKey(event))
                .payload(buildPayload(event))
                .status(WebhookDeliveryTaskStatus.PENDING_RETRY)
                .build());
    }

    private String buildBusinessKey(TenderStatusChangedEvent event) {
        return "%s:%s:%s".formatted(event.tenderId(), event.newStatus().name(), event.occurredAt());
    }

    private String buildPayload(TenderStatusChangedEvent event) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("event", "tender.status_changed");
            payload.put("tenderId", event.tenderId());
            payload.put("externalId", event.externalId() == null ? "" : event.externalId());
            payload.put("oldStatus", event.oldStatus().name());
            payload.put("newStatus", event.newStatus().name());
            payload.put("title", event.title());
            payload.put("occurredAt", event.occurredAt().toString());
            if (event.newStatus() == com.xiyu.bid.entity.Tender.Status.ABANDONED && event.abandonReason() != null) {
                payload.put("abandonReason", event.abandonReason());
            }
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize webhook payload", ex);
        }
    }
}
