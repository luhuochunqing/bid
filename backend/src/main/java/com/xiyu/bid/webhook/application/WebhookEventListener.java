package com.xiyu.bid.webhook.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.integration.external.ExternalIdParser;
import com.xiyu.bid.webhook.domain.RecommendationPayload;
import com.xiyu.bid.webhook.domain.TenderStatusChangedEvent;
import com.xiyu.bid.webhook.domain.TenderStatusChangedPayload;
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

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/**
 * 标讯状态变更回调监听器（接口文档 §4.1）。
 * <p>本平台在标讯状态发生变化时，向外部系统推送回调通知。
 * <p>触发时机（v3.8）：标讯提交投标（PENDING → BIDDING）或弃标（→ ABANDONED）时。
 * <p>⚠️ v3.8 变更：WON/LOST 不再走本回调，改由 §4.2 项目结果确认回调承担（含 competitors 和 evidenceFiles）。
 * 旧版 bidInfoSync 契约已废弃，改为 tender.status_changed 事件契约。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebhookEventListener {

    private static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter OCCURRED_AT_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
    private static final String EVENT_TYPE = "tender.status_changed";

    /**
     * 需要触发 §4.1 回调的终态集合（v3.8）。
     * <p>仅 BIDDING（提交投标）和 ABANDONED（弃标）触发。
     * <p>WON/LOST 改由 §4.2 项目结果确认回调承担（含竞争对手和凭证文件）。
     */
    private static final Set<Tender.Status> TRIGGER_STATES = Set.of(
            Tender.Status.BIDDING, Tender.Status.ABANDONED);

    private final WebhookDeliveryTaskRepository taskRepository;
    private final ObjectMapper objectMapper;

    @Value("${webhook.crm.url:}")
    private String crmWebhookUrl;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTenderStatusChanged(TenderStatusChangedEvent event) {
        log.info("WebhookEventListener received TenderStatusChangedEvent: tenderId={}, oldStatus={}, newStatus={}, externalId={}",
                event.tenderId(), event.oldStatus(), event.newStatus(), event.externalId());
        if (crmWebhookUrl == null || crmWebhookUrl.isBlank()) {
            log.warn("CRM webhook URL not configured, skipping enqueue for tender {}", event.tenderId());
            return;
        }
        if (!TRIGGER_STATES.contains(event.newStatus())) {
            log.info("Tender status {} not in trigger states {} (v3.8), skip webhook for tender {}",
                    event.newStatus(), TRIGGER_STATES, event.tenderId());
            return;
        }
        taskRepository.save(WebhookDeliveryTask.builder()
                .tenderId(event.tenderId())
                .externalId(event.externalId())
                .targetUrl(crmWebhookUrl)
                .eventType(EVENT_TYPE)
                .businessKey(buildBusinessKey(event))
                .payload(buildPayload(event))
                .status(WebhookDeliveryTaskStatus.PENDING)
                .build());
        log.info("Webhook delivery task enqueued for tender {}, newStatus={}, url={}",
                event.tenderId(), event.newStatus(), crmWebhookUrl);
    }

    private String buildBusinessKey(TenderStatusChangedEvent event) {
        return "%s:%s:%s".formatted(event.tenderId(), event.newStatus().name(), event.occurredAt());
    }

    /**
     * 构造符合 §4.1 契约的请求体（tender.status_changed 事件）。
     * <p>sourceId 从 externalId 提取（格式 {sourceSystem}:{sourceId}），与标讯推送接口一致。
     * <p>abandonReason 仅弃标时填值，其他状态省略（@JsonInclude.NON_NULL）。
     * <p>recommendation 仅有时填值，无评估时省略。
     */
    private String buildPayload(TenderStatusChangedEvent event) {
        try {
            String occurredAt = event.occurredAt().atZone(ZONE_SHANGHAI).format(OCCURRED_AT_FORMAT);
            RecommendationPayload recommendation = buildRecommendation(event);
            TenderStatusChangedPayload payload = new TenderStatusChangedPayload(
                    EVENT_TYPE,
                    event.tenderId(),
                    ExternalIdParser.extractSourceId(event.externalId()),
                    event.oldStatus() != null ? event.oldStatus().name() : null,
                    event.newStatus().name(),
                    event.title() != null ? event.title() : "",
                    occurredAt,
                    event.operatorId(),
                    event.operatorName(),
                    event.abandonReason(),
                    recommendation);
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize tender.status_changed payload", ex);
        }
    }

    /**
     * 构造评估建议：仅当 recommendationShouldBid 非 null 时填值，否则返回 null（JSON 省略）。
     */
    private RecommendationPayload buildRecommendation(TenderStatusChangedEvent event) {
        if (event.recommendationShouldBid() == null) return null;
        return new RecommendationPayload(
                event.recommendationShouldBid(),
                event.recommendationReason());
    }
}
