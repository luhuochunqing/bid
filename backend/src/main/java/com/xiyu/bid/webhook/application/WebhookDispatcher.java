package com.xiyu.bid.webhook.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.webhook.domain.TenderStatusChangedEvent;
import com.xiyu.bid.webhook.infrastructure.WebhookDeliveryLog;
import com.xiyu.bid.webhook.infrastructure.WebhookDeliveryLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Slf4j
public class WebhookDispatcher {

    private static final int MAX_RETRIES = 3;
    private static final Duration INITIAL_BACKOFF = Duration.ofSeconds(2);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final WebhookDeliveryLogRepository deliveryLogRepository;
    private final String crmWebhookUrl;
    private final String crmWebhookSecret;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public WebhookDispatcher(
            WebhookDeliveryLogRepository deliveryLogRepository,
            @Value("${webhook.crm.url:}") String crmWebhookUrl,
            @Value("${webhook.crm.secret:}") String crmWebhookSecret,
            ObjectMapper objectMapper) {
        this.deliveryLogRepository = deliveryLogRepository;
        this.crmWebhookUrl = crmWebhookUrl;
        this.crmWebhookSecret = crmWebhookSecret;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTenderStatusChanged(TenderStatusChangedEvent event) {
        if (crmWebhookUrl == null || crmWebhookUrl.isBlank()) {
            log.debug("CRM webhook URL not configured, skipping delivery for tender {}", event.tenderId());
            return;
        }
        deliver(event, 0);
    }

    private void deliver(TenderStatusChangedEvent event, int retryCount) {
        String payload;
        try {
            payload = buildPayload(event);
        } catch (Exception e) {
            log.error("Failed to serialize webhook payload for tender {}", event.tenderId(), e);
            return;
        }

        WebhookDeliveryLog logEntry = WebhookDeliveryLog.builder()
                .tenderId(event.tenderId())
                .targetUrl(crmWebhookUrl)
                .retryCount(retryCount)
                .status("PENDING")
                .build();

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(crmWebhookUrl))
                    .header("Content-Type", "application/json")
                    .timeout(REQUEST_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));

            if (crmWebhookSecret != null && !crmWebhookSecret.isBlank()) {
                String signature = hmacSha256(payload, crmWebhookSecret);
                builder.header("X-Webhook-Signature", "HMAC-SHA256=" + signature);
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            logEntry.setStatusCode(response.statusCode());
            logEntry.setResponseBody(truncate(response.body(), 1000));
            logEntry.setStatus(response.statusCode() >= 200 && response.statusCode() < 300 ? "DELIVERED" : "FAILED");

            log.info("Webhook delivered to {} for tender {}: status={}", crmWebhookUrl, event.tenderId(), response.statusCode());
        } catch (Exception e) {
            log.warn("Webhook delivery failed for tender {} (retry {}): {}", event.tenderId(), retryCount, e.getMessage());
            logEntry.setStatus("FAILED");
            logEntry.setResponseBody(truncate(e.getMessage(), 500));

            if (retryCount < MAX_RETRIES) {
                try {
                    long backoffMs = INITIAL_BACKOFF.toMillis() * (1L << retryCount);
                    Thread.sleep(backoffMs);
                    deliver(event, retryCount + 1);
                    return;
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        deliveryLogRepository.save(logEntry);
    }

    private String buildPayload(TenderStatusChangedEvent event) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "tender.status_changed");
        payload.put("tenderId", event.tenderId());
        payload.put("externalId", event.externalId() == null ? "" : event.externalId());
        payload.put("oldStatus", event.oldStatus().name());
        payload.put("newStatus", event.newStatus().name());
        payload.put("title", event.title());
        payload.put("occurredAt", event.occurredAt().toString());
        // 弃标时包含弃标原因
        if (event.newStatus() == Tender.Status.ABANDONED && event.abandonReason() != null) {
            payload.put("abandonReason", event.abandonReason());
        }
        return objectMapper.writeValueAsString(payload);
    }

    private String hmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("HMAC computation failed", e);
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}
