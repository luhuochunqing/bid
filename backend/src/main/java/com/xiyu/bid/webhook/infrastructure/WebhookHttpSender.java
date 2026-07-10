package com.xiyu.bid.webhook.infrastructure;

import com.xiyu.bid.crm.application.WebhookCrmTokenResolver;
import com.xiyu.bid.webhook.application.WebhookSendResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * CRM webhook HTTP 发送。
 * <p>有 operator_username → 用户 OSS→CRM JWT；无 operator → <strong>系统集成账号</strong>（显式）。
 */
@Component
public class WebhookHttpSender {
    private static final Logger log = LoggerFactory.getLogger(WebhookHttpSender.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final WebhookCrmTokenResolver webhookCrmTokenResolver;
    private final HttpClient httpClient;

    public WebhookHttpSender(WebhookCrmTokenResolver webhookCrmTokenResolver) {
        this.webhookCrmTokenResolver = webhookCrmTokenResolver;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public WebhookSendResult send(String targetUrl, String payload) throws IOException, InterruptedException {
        return send(targetUrl, payload, null);
    }

    public WebhookSendResult send(String targetUrl, String payload, String username)
            throws IOException, InterruptedException {
        // blank username → 系统集成账号（CrmAuthService.getValidTokenForCaller）
        if (username == null || username.isBlank()) {
            log.info("WebhookHttpSender: no operator username, using system integration account");
        }
        String token = webhookCrmTokenResolver.resolveToken(username);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        LocalDateTime now = LocalDateTime.now();
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return WebhookSendResult.success(response.statusCode(), truncate(response.body(), 1000), now);
        }
        if (response.statusCode() == 401) {
            webhookCrmTokenResolver.handleUnauthorizedForUser(username);
        }
        return WebhookSendResult.failure(response.statusCode(), truncate(response.body(), 1000),
                "HTTP_" + response.statusCode(), now);
    }

    private String truncate(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }
}
