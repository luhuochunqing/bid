package com.xiyu.bid.webhook.infrastructure;

import com.xiyu.bid.webhook.application.WebhookSendResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;

@Component
public class WebhookHttpSender {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final String crmWebhookSecret;
    private final HttpClient httpClient;

    public WebhookHttpSender(@Value("${webhook.crm.secret:}") String crmWebhookSecret) {
        this.crmWebhookSecret = crmWebhookSecret;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public WebhookSendResult send(String targetUrl, String payload) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .header("Content-Type", "application/json")
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));

        if (crmWebhookSecret != null && !crmWebhookSecret.isBlank()) {
            builder.header("X-Webhook-Signature", "HMAC-SHA256=" + hmacSha256(payload, crmWebhookSecret));
        }

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        LocalDateTime now = LocalDateTime.now();
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return WebhookSendResult.success(response.statusCode(), truncate(response.body(), 1000), now);
        }
        return WebhookSendResult.failure(response.statusCode(), truncate(response.body(), 1000), "HTTP_" + response.statusCode(), now);
    }

    private String hmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC computation failed", e);
        }
    }

    private String truncate(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }
}
