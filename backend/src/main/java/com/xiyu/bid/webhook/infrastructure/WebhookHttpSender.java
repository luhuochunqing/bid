package com.xiyu.bid.webhook.infrastructure;

import com.xiyu.bid.crm.application.TokenUnavailableException;
import com.xiyu.bid.crm.application.WebhookCrmTokenResolver;
import com.xiyu.bid.webhook.application.WebhookSendResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * CRM webhook HTTP 发送（CO-152：仅用户身份，无全局 03595 回退）。
 */
@Component
public class WebhookHttpSender {
    private static final Logger log = LoggerFactory.getLogger(WebhookHttpSender.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final WebhookCrmTokenResolver webhookCrmTokenResolver;
    private final HttpClient httpClient;

    public WebhookHttpSender(WebhookCrmTokenResolver webhookCrmTokenResolver,
                             @Value("${webhook.crm.secret:}") String crmWebhookSecret) {
        this.webhookCrmTokenResolver = webhookCrmTokenResolver;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public WebhookSendResult send(String targetUrl, String payload) throws IOException, InterruptedException {
        return send(targetUrl, payload, null);
    }

    /**
     * 按用户身份调 CRM 回调。
     * <p>username 非空 → 用户 OSS → generateToken → CRM JWT。
     * username 为空 → {@link TokenUnavailableException}（全局 token 路径已删除）。
     */
    public WebhookSendResult send(String targetUrl, String payload, String username)
            throws IOException, InterruptedException {
        String token = resolveTokenForUser(username);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        LocalDateTime now = LocalDateTime.now();
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return WebhookSendResult.success(response.statusCode(), truncate(response.body(), 1000), now);
        }
        if (response.statusCode() == 401) {
            handleUnauthorizedForUser(username);
        }
        return WebhookSendResult.failure(response.statusCode(), truncate(response.body(), 1000), "HTTP_" + response.statusCode(), now);
    }

    private String resolveTokenForUser(String username) {
        if (username == null || username.isBlank()) {
            throw new TokenUnavailableException(
                    "Webhook has no operator username (global 03595 token path removed)");
        }
        return webhookCrmTokenResolver.getValidTokenForUserStrict(username);
    }

    private void handleUnauthorizedForUser(String username) {
        if (username == null || username.isBlank()) {
            log.warn("WebhookHttpSender: 401 but no operator username, cannot clear user token cache");
        } else {
            webhookCrmTokenResolver.handleUnauthorizedForUser(username);
        }
    }

    private String truncate(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }
}
