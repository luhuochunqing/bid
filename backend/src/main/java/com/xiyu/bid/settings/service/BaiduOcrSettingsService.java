// Input: Baidu OCR configuration updates and test requests
// Output: encrypted OCR gateway settings, masked credentials and protocol-scoped login test results
// Pos: settings/service - Baidu OCR settings management
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.settings.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.platform.util.PasswordEncryptionUtil;
import com.xiyu.bid.scoreparse.infrastructure.baidu.BaiduOcrProperties;
import com.xiyu.bid.scoreparse.infrastructure.baidu.BaiduTableOcrClient;
import com.xiyu.bid.settings.dto.BaiduOcrTestRequest;
import com.xiyu.bid.settings.dto.BaiduOcrTestResponse;
import com.xiyu.bid.settings.dto.SettingsResponse;
import com.xiyu.bid.settings.dto.SettingsUpdateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class BaiduOcrSettingsService {

    private static final String TEST_PROTOCOL = "apihub-login-v1";

    private static final Logger log = LoggerFactory.getLogger(BaiduOcrSettingsService.class);

    private final PasswordEncryptionUtil passwordEncryptionUtil;
    private final BaiduOcrProperties baiduOcrProperties;
    private final ObjectMapper objectMapper;

    @Autowired
    public BaiduOcrSettingsService(
            PasswordEncryptionUtil passwordEncryptionUtil,
            @Autowired(required = false) BaiduOcrProperties baiduOcrProperties,
            ObjectMapper objectMapper
    ) {
        this.passwordEncryptionUtil = passwordEncryptionUtil;
        this.baiduOcrProperties = baiduOcrProperties;
        this.objectMapper = objectMapper;
    }

    public SettingsResponse.BaiduOcrConfig defaultBaiduOcrConfig() {
        return SettingsResponse.BaiduOcrConfig.builder()
                .usernameConfigured(false)
                .passwordConfigured(false)
                .usernameMasked("")
                .passwordMasked("")
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
        boolean currentProtocol = TEST_PROTOCOL.equals(source.getLastTestProtocol());
        return SettingsResponse.BaiduOcrConfig.builder()
                .enabled(source.getEnabled() != null && source.getEnabled())
                .encryptedUsername(source.getEncryptedUsername())
                .encryptedPassword(source.getEncryptedPassword())
                .usernameConfigured(isNotBlank(source.getEncryptedUsername()))
                .passwordConfigured(isNotBlank(source.getEncryptedPassword()))
                .encryptedApiKey(source.getEncryptedApiKey())
                .encryptedSecretKey(source.getEncryptedSecretKey())
                .apiKeyMasked(source.getApiKeyMasked())
                .secretKeyMasked(source.getSecretKeyMasked())
                .apiKeyConfigured(isNotBlank(source.getEncryptedApiKey()))
                .secretKeyConfigured(isNotBlank(source.getEncryptedSecretKey()))
                .lastTestProtocol(currentProtocol ? TEST_PROTOCOL : null)
                .lastTestStatus(currentProtocol ? source.getLastTestStatus() : null)
                .lastTestMessage(currentProtocol ? source.getLastTestMessage() : null)
                .lastTestAt(currentProtocol ? source.getLastTestAt() : null)
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

        String encryptedUsername = isNotBlank(update.getUsername())
                ? passwordEncryptionUtil.encrypt(update.getUsername().trim()) : normalized.getEncryptedUsername();
        String encryptedPassword = isNotBlank(update.getPassword())
                ? passwordEncryptionUtil.encrypt(update.getPassword()) : normalized.getEncryptedPassword();
        boolean changed = isNotBlank(update.getUsername()) || isNotBlank(update.getPassword());
        return SettingsResponse.BaiduOcrConfig.builder()
                .encryptedUsername(encryptedUsername)
                .encryptedPassword(encryptedPassword)
                .usernameConfigured(isNotBlank(encryptedUsername))
                .passwordConfigured(isNotBlank(encryptedPassword))
                .enabled(enabled)
                .encryptedApiKey(encryptedApiKey)
                .encryptedSecretKey(encryptedSecretKey)
                .apiKeyConfigured(isNotBlank(encryptedApiKey))
                .secretKeyConfigured(isNotBlank(encryptedSecretKey))
                .lastTestProtocol(changed ? null : normalized.getLastTestProtocol())
                .lastTestStatus(changed ? null : normalized.getLastTestStatus())
                .lastTestMessage(changed ? null : normalized.getLastTestMessage())
                .lastTestAt(changed ? null : normalized.getLastTestAt())
                .build();
    }

    public SettingsResponse.BaiduOcrConfig copyForResponse(SettingsResponse.BaiduOcrConfig source) {
        SettingsResponse.BaiduOcrConfig normalized = normalizeBaiduOcrConfig(source);
        var gateway = resolveEffectiveCredentials(source);

        return SettingsResponse.BaiduOcrConfig.builder()
                .enabled(normalized.getEnabled())
                .usernameConfigured(isNotBlank(gateway.username()))
                .passwordConfigured(isNotBlank(gateway.password()))
                .usernameMasked(maskKey(gateway.username()))
                .passwordMasked(isNotBlank(gateway.password()) ? "******" : "")
                .apiKeyConfigured(isNotBlank(gateway.apiKey()))
                .secretKeyConfigured(isNotBlank(gateway.secretKey()))
                .apiKeyMasked(maskKey(gateway.apiKey()))
                .secretKeyMasked(maskKey(gateway.secretKey()))
                .lastTestProtocol(normalized.getLastTestProtocol())
                .lastTestStatus(normalized.getLastTestStatus())
                .lastTestMessage(normalized.getLastTestMessage())
                .lastTestAt(normalized.getLastTestAt())
                .build();
    }

    public record EffectiveBaiduCredentials(boolean enabled, String apiKey, String secretKey, String username, String password) {}

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

        boolean enabled = normalized.getEnabled();

        String username = decryptSafely(normalized.getEncryptedUsername());
        String password = decryptSafely(normalized.getEncryptedPassword());
        if (isBlank(username) && baiduOcrProperties != null) username = baiduOcrProperties.getUsername();
        if (isBlank(password) && baiduOcrProperties != null) password = baiduOcrProperties.getPassword();
        return new EffectiveBaiduCredentials(enabled, apiKey, secretKey, username, password);
    }

    public BaiduOcrTestResponse testConnection(BaiduOcrTestRequest request, SettingsResponse.BaiduOcrConfig currentConfig) {
        // SettingsService persists this same config together with the returned status/message/time.
        // Stamp only when a new gateway test runs, never while loading historical settings.
        if (currentConfig != null) currentConfig.setLastTestProtocol(TEST_PROTOCOL);
        var effective = resolveEffectiveCredentials(currentConfig);
        var testProperties = new BaiduOcrProperties();
        if (baiduOcrProperties != null) org.springframework.beans.BeanUtils.copyProperties(baiduOcrProperties, testProperties);
        testProperties.setUsername(request != null && isNotBlank(request.getUsername())
                ? request.getUsername().trim() : effective.username());
        testProperties.setPassword(request != null && isNotBlank(request.getPassword())
                ? request.getPassword() : effective.password());
        if (isBlank(testProperties.getUsername()) || isBlank(testProperties.getPassword())) {
            return BaiduOcrTestResponse.builder().status("failed")
                    .message("OCR 用户名或密码不能为空，请填写并保存配置")
                    .testedAt(Instant.now()).build();
        }
        if (!testProperties.isGatewayEnabled()) {
            return BaiduOcrTestResponse.builder().status("failed")
                    .message("当前部署未启用 OCR 中转，仅测试环境可用")
                    .testedAt(Instant.now()).build();
        }
        try {
            new BaiduTableOcrClient(testProperties, objectMapper).getAccessToken();
            return BaiduOcrTestResponse.builder().status("success")
                    .message("APIHub 登录鉴权成功（未验证 OCR 额度和识别结果）")
                    .testedAt(Instant.now()).build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return BaiduOcrTestResponse.builder().status("failed")
                    .message("APIHub 连接测试被中断").testedAt(Instant.now()).build();
        } catch (java.io.IOException | RuntimeException e) {
            return BaiduOcrTestResponse.builder().status("failed")
                    .message("APIHub 登录失败，请检查测试环境网络和中转账号配置")
                    .testedAt(Instant.now()).build();
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
