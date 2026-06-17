package com.xiyu.bid.webhook.infrastructure;

import com.xiyu.bid.crm.application.CrmAuthService;
import com.xiyu.bid.webhook.application.WebhookSendResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final CrmAuthService crmAuthService;
    private final HttpClient httpClient;

    public WebhookHttpSender(CrmAuthService crmAuthService,
                             @Value("${webhook.crm.secret:}") String crmWebhookSecret) {
        this.crmAuthService = crmAuthService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public WebhookSendResult send(String targetUrl, String payload) throws IOException, InterruptedException {
        String token = crmAuthService.getValidToken();
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
            crmAuthService.handleUnauthorized();
        }
        return WebhookSendResult.failure(response.statusCode(), truncate(response.body(), 1000), "HTTP_" + response.statusCode(), now);
    }

    private String truncate(String value, int maxLen) {
        if (value == null) return null;
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }
}
