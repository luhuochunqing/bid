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
    void copyForResponse_ShouldMaskEnvironmentFallbackAndHandleMissingProperties() {
        baiduOcrProperties.setUsername("gateway-environment-user");
        baiduOcrProperties.setPassword(" padded-password ");

        var response = service.copyForResponse(null);
        assertEquals("env-****1234", response.getApiKeyMasked());
        assertEquals("env-****5678", response.getSecretKeyMasked());
        assertEquals("gate****user", response.getUsernameMasked());
        assertEquals("******", response.getPasswordMasked());
        assertTrue(response.getApiKeyConfigured());
        assertTrue(response.getSecretKeyConfigured());
        assertTrue(response.getUsernameConfigured());
        assertTrue(response.getPasswordConfigured());

        var emptyService = new BaiduOcrSettingsService(passwordEncryptionUtil, null, objectMapper);
        var emptyResponse = emptyService.copyForResponse(null);
        assertEquals("", emptyResponse.getApiKeyMasked());
        assertEquals("", emptyResponse.getSecretKeyMasked());
        assertEquals("", emptyResponse.getUsernameMasked());
        assertEquals("", emptyResponse.getPasswordMasked());
        assertFalse(emptyResponse.getApiKeyConfigured());
        assertFalse(emptyResponse.getSecretKeyConfigured());
        assertFalse(emptyResponse.getUsernameConfigured());
        assertFalse(emptyResponse.getPasswordConfigured());
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
        baiduOcrProperties.setEnabled(true);
        SettingsResponse.BaiduOcrConfig emptyDbConfig = SettingsResponse.BaiduOcrConfig.builder()
                .enabled(null)
                .encryptedApiKey(null)
                .encryptedSecretKey(null)
                .build();

        var effective = service.resolveEffectiveCredentials(emptyDbConfig);
        assertFalse(effective.enabled());
        assertFalse(service.resolveEffectiveCredentials(null).enabled());
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
    @Test
    void legacyTestResultsAreUnverifiedWithOrWithoutEnvironmentGatewayCredentials() throws Exception {
        for (String status : java.util.List.of("success", "failed")) {
            var legacy = objectMapper.readValue("{\"enabled\":true,\"lastTestStatus\":\"" + status
                    + "\",\"lastTestMessage\":\"旧百度直连结论\"}", SettingsResponse.BaiduOcrConfig.class);
            legacy.setLastTestAt(java.time.Instant.parse("2026-10-01T00:00:00Z"));
            for (boolean environmentConfigured : java.util.List.of(false, true)) {
                baiduOcrProperties.setUsername(environmentConfigured ? "gateway-user" : null);
                baiduOcrProperties.setPassword(environmentConfigured ? "gateway-password" : null);
                var response = service.copyForResponse(legacy);
                org.junit.jupiter.api.Assertions.assertNull(response.getLastTestStatus());
                org.junit.jupiter.api.Assertions.assertNull(response.getLastTestMessage());
                org.junit.jupiter.api.Assertions.assertNull(response.getLastTestAt());
                assertEquals(environmentConfigured, response.getUsernameConfigured());
                assertEquals(status, legacy.getLastTestStatus());
                var merged = service.mergeBaiduOcrConfig(legacy, new SettingsUpdateRequest.BaiduOcrConfigUpdate());
                org.junit.jupiter.api.Assertions.assertNull(merged.getLastTestStatus());
            }
        }
    }

    @Test
    void gatewayTestProtocolSurvivesPersistenceAndUnrelatedSaveButClearsOnCredentialChange() throws Exception {
        var config = service.defaultBaiduOcrConfig();
        var result = service.testConnection(null, config); // Missing credentials: no network request.
        config.setLastTestStatus(result.getStatus());
        config.setLastTestMessage(result.getMessage());
        config.setLastTestAt(result.getTestedAt());
        var mapper = new ObjectMapper().findAndRegisterModules();
        var persisted = mapper.readValue(mapper.writeValueAsString(config), SettingsResponse.BaiduOcrConfig.class);
        var retained = service.mergeBaiduOcrConfig(persisted, new SettingsUpdateRequest.BaiduOcrConfigUpdate());
        assertEquals("failed", service.copyForResponse(retained).getLastTestStatus());
        assertEquals(result.getMessage(), service.copyForResponse(retained).getLastTestMessage());
        assertEquals(result.getTestedAt(), service.copyForResponse(retained).getLastTestAt());
        // The existing persistence path writes successful results in exactly the same way.
        retained.setLastTestStatus("success");
        assertEquals("success", service.copyForResponse(retained).getLastTestStatus());
        var changed = service.mergeBaiduOcrConfig(retained,
                SettingsUpdateRequest.BaiduOcrConfigUpdate.builder().username("new-user").build());
        org.junit.jupiter.api.Assertions.assertNull(service.copyForResponse(changed).getLastTestStatus());
        org.junit.jupiter.api.Assertions.assertNull(changed.getLastTestProtocol());
    }

    @Test
    void testConnection_UsesConfiguredGatewayInsteadOfLegacyKeys() throws Exception {
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        var accepted = new java.util.concurrent.atomic.AtomicBoolean();
        var expectedUsername = new java.util.concurrent.atomic.AtomicReference<>("gateway-user");
        var expectedPassword = new java.util.concurrent.atomic.AtomicReference<>("gateway-password");
        server.createContext("/login", exchange -> {
            var body = objectMapper.readTree(exchange.getRequestBody());
            accepted.set(exchange.getRequestURI().getQuery() == null
                    && expectedUsername.get().equals(body.path("username").asText())
                    && expectedPassword.get().equals(body.path("password").asText()));
            byte[] response = "{\"success\":true,\"code\":\"000000\",\"data\":{\"token\":\"token\",\"tokenRemainTime\":3600}}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        try {
            var props = new BaiduOcrProperties();
            props.setGatewayEnabled(true);
            new org.springframework.boot.context.properties.bind.Binder(new org.springframework.boot.context.properties.source.MapConfigurationPropertySource(java.util.Map.of(
                    "username", "gateway-user", "password", "gateway-password")))
                    .bind("", org.springframework.boot.context.properties.bind.Bindable.ofInstance(props));
            props.setTokenUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/login");
            var target = new BaiduOcrSettingsService(passwordEncryptionUtil, props, objectMapper);
            assertEquals("success", target.testConnection(new BaiduOcrTestRequest("", ""), null).getStatus());
            assertTrue(accepted.get());
            var saved = target.mergeBaiduOcrConfig(null, SettingsUpdateRequest.BaiduOcrConfigUpdate.builder()
                    .username("saved-user").password(" saved-password ").build());
            expectedUsername.set("saved-user"); expectedPassword.set(" saved-password ");
            var loginResult = target.testConnection(null, saved);
            assertEquals("success", loginResult.getStatus());
            saved.setLastTestStatus(loginResult.getStatus());
            saved.setLastTestMessage(loginResult.getMessage());
            saved.setLastTestAt(loginResult.getTestedAt());
            var persistenceMapper = new ObjectMapper().findAndRegisterModules();
            var reloaded = persistenceMapper.readValue(persistenceMapper.writeValueAsString(saved),
                    SettingsResponse.BaiduOcrConfig.class);
            assertEquals("success", target.copyForResponse(reloaded).getLastTestStatus());
            assertTrue(accepted.get());
            expectedUsername.set("input-user"); expectedPassword.set(" input-password ");
            var request = BaiduOcrTestRequest.builder().username("input-user").password(" input-password ").build();
            assertEquals("success", target.testConnection(request, saved).getStatus());
            assertTrue(accepted.get());
            var effective = target.resolveEffectiveCredentials(saved);
            assertEquals("saved-user", effective.username());
            assertEquals(" saved-password ", effective.password());
        } finally { server.stop(0); }
    }

    @Test
    void gatewayCredentialsAreEncryptedRetainedAndMaskedWithoutReusingBaiduKeys() throws Exception {
        var mapper = new ObjectMapper().disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        var update = mapper.readValue("{\"username\":\"gateway-user\",\"password\":\" padded-password \"}",
                SettingsUpdateRequest.BaiduOcrConfigUpdate.class);
        var saved = service.mergeBaiduOcrConfig(null, update);
        var json = mapper.valueToTree(saved);
        assertEquals("gateway-user", passwordEncryptionUtil.decrypt(json.path("encryptedUsername").asText()));
        assertEquals(" padded-password ", passwordEncryptionUtil.decrypt(json.path("encryptedPassword").asText()));
        var retained = service.mergeBaiduOcrConfig(saved, new SettingsUpdateRequest.BaiduOcrConfigUpdate());
        assertEquals(json.path("encryptedPassword"), mapper.valueToTree(retained).path("encryptedPassword"));
        var response = mapper.valueToTree(service.copyForResponse(saved));
        assertTrue(response.path("usernameConfigured").asBoolean());
        assertTrue(response.path("passwordConfigured").asBoolean());
        assertTrue(response.path("encryptedUsername").isNull());
        assertTrue(response.path("encryptedPassword").isNull());
    }

}
