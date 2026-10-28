package com.xiyu.bid.settings.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.platform.util.PasswordEncryptionUtil;
import com.xiyu.bid.scoreparse.infrastructure.baidu.BaiduOcrProperties;
import com.xiyu.bid.settings.dto.BaiduOcrTestRequest;
import com.xiyu.bid.settings.dto.BaiduOcrTestResponse;
import com.xiyu.bid.settings.dto.SettingsResponse;
import com.xiyu.bid.settings.dto.SettingsUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaiduOcrSettingsServiceTest {

    private PasswordEncryptionUtil passwordEncryptionUtil;
    private BaiduOcrProperties baiduOcrProperties;
    private ObjectMapper objectMapper;
    private BaiduOcrSettingsService service;

    @BeforeEach
    void setUp() {
        passwordEncryptionUtil = new PasswordEncryptionUtil();
        passwordEncryptionUtil.initialize();
        baiduOcrProperties = new BaiduOcrProperties();
        baiduOcrProperties.setEnabled(false);
        baiduOcrProperties.setApiKey("env-api-key-1234");
        baiduOcrProperties.setSecretKey("env-secret-key-5678");
        objectMapper = new ObjectMapper();
        service = new BaiduOcrSettingsService(passwordEncryptionUtil, baiduOcrProperties, objectMapper);
    }

    @Test
    void defaultAndNormalizeConfig_ShouldHaveDefaultValues() {
        SettingsResponse.BaiduOcrConfig config = service.defaultBaiduOcrConfig();
        assertNotNull(config);
        assertFalse(config.getEnabled());
        assertFalse(config.getApiKeyConfigured());
        assertEquals("", config.getApiKeyMasked());
    }

    @Test
    void mergeBaiduOcrConfig_ShouldEncryptNewPlaintextKeys() {
        SettingsResponse.BaiduOcrConfig initial = service.defaultBaiduOcrConfig();

        SettingsResponse.BaiduOcrConfig merged = service.mergeBaiduOcrConfig(
                initial,
                SettingsUpdateRequest.BaiduOcrConfigUpdate.builder()
                        .enabled(true)
                        .apiKey("custom-plain-api-key")
                        .secretKey("custom-plain-secret-key")
                        .build()
        );

        assertTrue(merged.getEnabled());
        assertTrue(merged.getApiKeyConfigured());
        assertTrue(merged.getSecretKeyConfigured());

        // 验证加密存储
        assertNotNull(merged.getEncryptedApiKey());
        assertEquals("custom-plain-api-key", passwordEncryptionUtil.decrypt(merged.getEncryptedApiKey()));
    }

    @Test
    void mergeBaiduOcrConfig_WithMaskedKeys_ShouldRetainExistingEncryptedValues() {
        SettingsResponse.BaiduOcrConfig current = SettingsResponse.BaiduOcrConfig.builder()
                .enabled(true)
                .encryptedApiKey(passwordEncryptionUtil.encrypt("old-real-key"))
                .encryptedSecretKey(passwordEncryptionUtil.encrypt("old-real-secret"))
                .build();

        SettingsResponse.BaiduOcrConfig merged = service.mergeBaiduOcrConfig(
                current,
                SettingsUpdateRequest.BaiduOcrConfigUpdate.builder()
                        .enabled(false)
                        .apiKey("old-****-key") // 包含 **** 视为掩码回传
                        .secretKey("old-****-secret")
                        .build()
        );

        assertFalse(merged.getEnabled());
        assertEquals("old-real-key", passwordEncryptionUtil.decrypt(merged.getEncryptedApiKey()));
        assertEquals("old-real-secret", passwordEncryptionUtil.decrypt(merged.getEncryptedSecretKey()));
    }

    @Test
    void copyForResponse_ShouldMaskKeysAndExposeConfigurationStatus() {
        SettingsResponse.BaiduOcrConfig raw = SettingsResponse.BaiduOcrConfig.builder()
                .enabled(true)
                .encryptedApiKey(passwordEncryptionUtil.encrypt("VJ8ZnT3WTnUR433O7QX6sWlY"))
                .encryptedSecretKey(passwordEncryptionUtil.encrypt("QE4wXv7U774mb5IceRRY3XpI7FsY7bKC"))
                .build();

        SettingsResponse.BaiduOcrConfig response = service.copyForResponse(raw);

        assertTrue(response.getEnabled());
        assertTrue(response.getApiKeyConfigured());
        assertTrue(response.getSecretKeyConfigured());
        assertEquals("VJ8Z****sWlY", response.getApiKeyMasked());
        assertEquals("QE4w****7bKC", response.getSecretKeyMasked());
    }

    @Test
    void resolveEffectiveCredentials_ShouldPreferDatabaseOverProperties() {
        SettingsResponse.BaiduOcrConfig dbConfig = SettingsResponse.BaiduOcrConfig.builder()
                .enabled(true)
                .encryptedApiKey(passwordEncryptionUtil.encrypt("db-key"))
                .encryptedSecretKey(passwordEncryptionUtil.encrypt("db-secret"))
                .build();

        var effective = service.resolveEffectiveCredentials(dbConfig);
        assertTrue(effective.enabled());
        assertEquals("db-key", effective.apiKey());
        assertEquals("db-secret", effective.secretKey());
    }

    @Test
    void resolveEffectiveCredentials_WhenDbEmpty_ShouldFallbackToProperties() {
        SettingsResponse.BaiduOcrConfig emptyDbConfig = SettingsResponse.BaiduOcrConfig.builder()
                .enabled(null)
                .encryptedApiKey(null)
                .encryptedSecretKey(null)
                .build();

        var effective = service.resolveEffectiveCredentials(emptyDbConfig);
        assertFalse(effective.enabled());
        assertEquals("env-api-key-1234", effective.apiKey());
        assertEquals("env-secret-key-5678", effective.secretKey());
    }

    @Test
    void testConnection_WhenNoKeysConfigured_ShouldReturnFailed() {
        BaiduOcrSettingsService emptyService = new BaiduOcrSettingsService(passwordEncryptionUtil, null, objectMapper);
        BaiduOcrTestResponse res = emptyService.testConnection(new BaiduOcrTestRequest("", ""), null);
        assertEquals("failed", res.getStatus());
        assertTrue(res.getMessage().contains("不能为空"));
    }
}
