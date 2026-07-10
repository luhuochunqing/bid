package com.xiyu.bid.webhook.infrastructure;

import com.xiyu.bid.crm.application.CrmAuthService;
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

@Component
public class WebhookHttpSender {
    private static final Logger log = LoggerFactory.getLogger(WebhookHttpSender.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final CrmAuthService crmAuthService;
    private final WebhookCrmTokenResolver webhookCrmTokenResolver;
    private final HttpClient httpClient;

    public WebhookHttpSender(CrmAuthService crmAuthService,
                             WebhookCrmTokenResolver webhookCrmTokenResolver,
                             @Value("${webhook.crm.secret:}") String crmWebhookSecret) {
        this.crmAuthService = crmAuthService;
        this.webhookCrmTokenResolver = webhookCrmTokenResolver;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public WebhookSendResult send(String targetUrl, String payload) throws IOException, InterruptedException {
        return send(targetUrl, payload, null);
    }

    /**
     * CO-152 补齐：按用户身份调 CRM 回调。
     * <p>
     * username 非空 → 走 {@link CrmAuthService#getValidTokenForUserStrict}，用操作者的 OSS token 调 generateToken。
     * username 为空（历史数据/未识别操作者） → 回退到 {@link CrmAuthService#getValidToken}（全局共享 token）。
     * <p>
     * 401 时：
     * - username 非空 → 调 {@link CrmAuthService#handleUnauthorizedForUser} 只清当前用户缓存
     * - username 为空 → 调 {@link CrmAuthService#handleUnauthorized} 清全局缓存
     *
     * @param targetUrl 回调目标 URL
     * @param payload   回调请求体
     * @param username  操作者 username（可为 null，回退全局 token）
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

    /**
     * CO-152 补齐：按用户身份解析 CRM JWT token。
     * username 非空 → 严格按用户身份（走 WebhookCrmTokenResolver）；为空 → 回退全局共享 token。
     */
    private String resolveTokenForUser(String username) {
        if (username == null || username.isBlank()) {
            log.info("WebhookHttpSender: no operator username, falling back to global CRM token");
            return crmAuthService.getValidToken();
        }
        return webhookCrmTokenResolver.getValidTokenForUserStrict(username);
    }

    /**
     * CO-152 补齐：按用户身份处理 401。
     * username 非空 → 只清当前用户缓存；为空 → 清全局缓存。
     */
    private void handleUnauthorizedForUser(String username) {
        if (username == null || username.isBlank()) {
            crmAuthService.handleUnauthorized();
        } else {
            webhookCrmTokenResolver.handleUnauthorizedForUser(username);
        }
    }

    private String truncate(String value, int maxLen) {
        if (value == null) return null;
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }
}
