package com.xiyu.bid.workflowform.infrastructure.oa;

import com.xiyu.bid.settings.dto.SettingsResponse;
import com.xiyu.bid.settings.service.SettingsService;
import com.xiyu.bid.workflowform.dto.OaCallbackRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

@Component
public class OaCallbackVerifier {

    private static final String DEFAULT_SETTINGS_API_KEY = "sk_xiyu_bid_server_default";
    private final SettingsService settingsService;
    private final String fallbackSecret;

    public OaCallbackVerifier(
            SettingsService settingsService,
            @Value("${oa.workflow.callback.secret:}") String fallbackSecret
    ) {
        this.settingsService = settingsService;
        this.fallbackSecret = fallbackSecret == null ? "" : fallbackSecret;
    }

    public void verify(OaCallbackRequest request) {
        validateRequest(request);
        String callbackSecret = resolveCallbackSecret();
        if (callbackSecret.isBlank()) {
            throw unauthorized();
        }
        long timestamp = parseTimestamp(request.timestamp());
        if (Math.abs(Instant.now().getEpochSecond() - timestamp) > 300) {
            throw unauthorized();
        }
        String payload = String.join("|", request.oaInstanceId(), request.status().name(), request.timestamp(), request.nonce(), request.eventId());
        if (!MessageDigest.isEqual(
                sign(payload, callbackSecret).getBytes(StandardCharsets.UTF_8),
                request.signature().getBytes(StandardCharsets.UTF_8))) {
            throw unauthorized();
        }
    }

    private String resolveCallbackSecret() {
        try {
            SettingsResponse settings = settingsService.getSettings();
            String settingsKey = settings == null || settings.getIntegrationConfig() == null
                    ? null
                    : settings.getIntegrationConfig().getApiKey();
            if (settingsKey != null && !settingsKey.isBlank() && !DEFAULT_SETTINGS_API_KEY.equals(settingsKey)) {
                return settingsKey;
            }
        } catch (RuntimeException ignored) {
            // 配置回源失败时，优先使用运行时配置兜底，避免因配置中心瞬时问题导致回调直接抛错
        }
        return fallbackSecret;
    }

    private void validateRequest(OaCallbackRequest request) {
        if (request == null
                || request.oaInstanceId() == null
                || request.oaInstanceId().isBlank()
                || request.status() == null
                || request.timestamp() == null
                || request.timestamp().isBlank()
                || request.nonce() == null
                || request.nonce().isBlank()
                || request.eventId() == null
                || request.eventId().isBlank()
                || request.signature() == null
                || request.signature().isBlank()) {
            throw unauthorized();
        }
    }

    private String sign(String payload, String callbackSecret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(callbackSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (InvalidKeyException | NoSuchAlgorithmException exception) {
            throw unauthorized();
        }
    }

    private long parseTimestamp(String timestamp) {
        try {
            return Long.parseLong(timestamp);
        } catch (NumberFormatException exception) {
            throw unauthorized();
        }
    }

    private ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "OA 回调验证失败");
    }
}
