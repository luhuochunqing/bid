// Input: Baidu OCR configuration updates and test requests
// Output: encrypted persistence payload, masked response and connection test results
// Pos: settings/service - Baidu OCR settings management
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.settings.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.platform.util.PasswordEncryptionUtil;
import com.xiyu.bid.scoreparse.infrastructure.baidu.BaiduOcrProperties;
import com.xiyu.bid.settings.dto.BaiduOcrTestRequest;
import com.xiyu.bid.settings.dto.BaiduOcrTestResponse;
import com.xiyu.bid.settings.dto.SettingsResponse;
import com.xiyu.bid.settings.dto.SettingsUpdateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

@Service
public class BaiduOcrSettingsService {

    private static final Logger log = LoggerFactory.getLogger(BaiduOcrSettingsService.class);
    private static final String DEFAULT_TOKEN_URL = "https://aip.baidubce.com/oauth/2.0/token";

    private final PasswordEncryptionUtil passwordEncryptionUtil;
    private final BaiduOcrProperties baiduOcrProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public BaiduOcrSettingsService(
            PasswordEncryptionUtil passwordEncryptionUtil,
            @Autowired(required = false) BaiduOcrProperties baiduOcrProperties,
            ObjectMapper objectMapper
    ) {
        this.passwordEncryptionUtil = passwordEncryptionUtil;
        this.baiduOcrProperties = baiduOcrProperties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public SettingsResponse.BaiduOcrConfig defaultBaiduOcrConfig() {
        return SettingsResponse.BaiduOcrConfig.builder()
                .enabled(false)
                .apiKeyConfigured(false)
                .secretKeyConfigured(false)
                .apiKeyMasked("")
                .secretKeyMasked("")
                .build();
    }

    public SettingsResponse.BaiduOcrConfig normalizeBaiduOcrConfig(SettingsResponse.BaiduOcrConfig source) {
        if (source == null) {
            return defaultBaiduOcrConfig();
        }
        return SettingsResponse.BaiduOcrConfig.builder()
                .enabled(source.getEnabled() != null && source.getEnabled())
                .encryptedApiKey(source.getEncryptedApiKey())
                .encryptedSecretKey(source.getEncryptedSecretKey())
                .apiKeyMasked(source.getApiKeyMasked())
                .secretKeyMasked(source.getSecretKeyMasked())
                .apiKeyConfigured(isNotBlank(source.getEncryptedApiKey()))
                .secretKeyConfigured(isNotBlank(source.getEncryptedSecretKey()))
                .lastTestStatus(source.getLastTestStatus())
                .lastTestMessage(source.getLastTestMessage())
                .lastTestAt(source.getLastTestAt())
                .build();
    }

    public SettingsResponse.BaiduOcrConfig mergeBaiduOcrConfig(
            SettingsResponse.BaiduOcrConfig current,
            SettingsUpdateRequest.BaiduOcrConfigUpdate update
    ) {
        SettingsResponse.BaiduOcrConfig normalized = normalizeBaiduOcrConfig(current);
        if (update == null) {
            return normalized;
        }

        Boolean enabled = update.getEnabled() != null ? update.getEnabled() : normalized.getEnabled();
        String encryptedApiKey = normalized.getEncryptedApiKey();
        if (isFreshSecret(update.getApiKey())) {
            encryptedApiKey = passwordEncryptionUtil.encrypt(update.getApiKey().trim());
        }

        String encryptedSecretKey = normalized.getEncryptedSecretKey();
        if (isFreshSecret(update.getSecretKey())) {
            encryptedSecretKey = passwordEncryptionUtil.encrypt(update.getSecretKey().trim());
        }

        return SettingsResponse.BaiduOcrConfig.builder()
                .enabled(enabled)
                .encryptedApiKey(encryptedApiKey)
                .encryptedSecretKey(encryptedSecretKey)
                .apiKeyConfigured(isNotBlank(encryptedApiKey))
                .secretKeyConfigured(isNotBlank(encryptedSecretKey))
                .lastTestStatus(normalized.getLastTestStatus())
                .lastTestMessage(normalized.getLastTestMessage())
                .lastTestAt(normalized.getLastTestAt())
                .build();
    }

    public SettingsResponse.BaiduOcrConfig copyForResponse(SettingsResponse.BaiduOcrConfig source) {
        SettingsResponse.BaiduOcrConfig normalized = normalizeBaiduOcrConfig(source);
        String decryptedApiKey = decryptSafely(normalized.getEncryptedApiKey());
        String decryptedSecretKey = decryptSafely(normalized.getEncryptedSecretKey());

        // 如果数据库没有配置，但 application.yml 注入了，则提示已配置并展示环境变量掩码
        boolean envHasKey = baiduOcrProperties != null && isNotBlank(baiduOcrProperties.getApiKey());
        boolean envHasSecret = baiduOcrProperties != null && isNotBlank(baiduOcrProperties.getSecretKey());

        String displayApiKey = isNotBlank(decryptedApiKey) ? decryptedApiKey : (envHasKey ? baiduOcrProperties.getApiKey() : "");
        String displaySecretKey = isNotBlank(decryptedSecretKey) ? decryptedSecretKey : (envHasSecret ? baiduOcrProperties.getSecretKey() : "");

        boolean hasKey = isNotBlank(displayApiKey);
        boolean hasSecret = isNotBlank(displaySecretKey);

        return SettingsResponse.BaiduOcrConfig.builder()
                .enabled(normalized.getEnabled())
                .apiKeyConfigured(hasKey)
                .secretKeyConfigured(hasSecret)
                .apiKeyMasked(maskKey(displayApiKey))
                .secretKeyMasked(maskKey(displaySecretKey))
                .lastTestStatus(normalized.getLastTestStatus())
                .lastTestMessage(normalized.getLastTestMessage())
                .lastTestAt(normalized.getLastTestAt())
                .build();
    }

    public record EffectiveBaiduCredentials(boolean enabled, String apiKey, String secretKey) {}

    public EffectiveBaiduCredentials resolveEffectiveCredentials(SettingsResponse.BaiduOcrConfig config) {
        SettingsResponse.BaiduOcrConfig normalized = normalizeBaiduOcrConfig(config);
        String apiKey = decryptSafely(normalized.getEncryptedApiKey());
        String secretKey = decryptSafely(normalized.getEncryptedSecretKey());

        if (isBlank(apiKey) && baiduOcrProperties != null) {
            apiKey = baiduOcrProperties.getApiKey();
        }
        if (isBlank(secretKey) && baiduOcrProperties != null) {
            secretKey = baiduOcrProperties.getSecretKey();
        }

        boolean enabled = normalized.getEnabled() != null ? normalized.getEnabled() :
                (baiduOcrProperties != null && baiduOcrProperties.isEnabled());

        return new EffectiveBaiduCredentials(enabled, apiKey, secretKey);
    }

    public BaiduOcrTestResponse testConnection(BaiduOcrTestRequest request, SettingsResponse.BaiduOcrConfig currentConfig) {
        String testApiKey = null;
        String testSecretKey = null;

        if (request != null && isFreshSecret(request.getApiKey())) {
            testApiKey = request.getApiKey().trim();
        }
        if (request != null && isFreshSecret(request.getSecretKey())) {
            testSecretKey = request.getSecretKey().trim();
        }

        // 若请求中未指定或为掩码，从已持久化配置或配置属性回退
        EffectiveBaiduCredentials effective = resolveEffectiveCredentials(currentConfig);
        if (isBlank(testApiKey)) {
            testApiKey = effective.apiKey();
        }
        if (isBlank(testSecretKey)) {
            testSecretKey = effective.secretKey();
        }

        if (isBlank(testApiKey) || isBlank(testSecretKey)) {
            return BaiduOcrTestResponse.builder()
                    .status("failed")
                    .message("API Key 或 Secret Key 不能为空")
                    .testedAt(Instant.now())
                    .build();
        }

        String tokenUrl = baiduOcrProperties != null && isNotBlank(baiduOcrProperties.getTokenUrl())
                ? baiduOcrProperties.getTokenUrl() : DEFAULT_TOKEN_URL;

        String fullUrl = String.format("%s?grant_type=client_credentials&client_id=%s&client_secret=%s",
                tokenUrl, testApiKey, testSecretKey);

        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(fullUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(response.body());

            if (root.has("access_token")) {
                return BaiduOcrTestResponse.builder()
                        .status("success")
                        .message("百度智能云鉴权连接成功")
                        .testedAt(Instant.now())
                        .build();
            } else {
                String error = root.has("error_description") ? root.get("error_description").asText()
                        : (root.has("error") ? root.get("error").asText() : "未知鉴权错误");
                return BaiduOcrTestResponse.builder()
                        .status("failed")
                        .message("连接失败: " + error)
                        .testedAt(Instant.now())
                        .build();
            }
        } catch (java.io.IOException | InterruptedException | RuntimeException e) {
            log.warn("Baidu OCR connection test error: {}", e.getMessage());
            return BaiduOcrTestResponse.builder()
                    .status("failed")
                    .message("网络通信异常: " + e.getMessage())
                    .testedAt(Instant.now())
                    .build();
        }
    }

    private String decryptSafely(String encrypted) {
        if (isBlank(encrypted)) return null;
        try {
            return passwordEncryptionUtil.decrypt(encrypted);
        } catch (RuntimeException e) {
            log.error("Failed to decrypt Baidu OCR secret", e);
            return null;
        }
    }

    private String maskKey(String raw) {
        if (isBlank(raw)) return "";
        int len = raw.length();
        if (len <= 8) return "******";
        return raw.substring(0, 4) + "****" + raw.substring(len - 4);
    }

    private boolean isFreshSecret(String secret) {
        return isNotBlank(secret) && !secret.contains("****");
    }

    private boolean isNotBlank(String str) {
        return str != null && !str.isBlank();
    }

    private boolean isBlank(String str) {
        return str == null || str.isBlank();
    }
}
