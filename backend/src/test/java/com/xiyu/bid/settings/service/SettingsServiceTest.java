package com.xiyu.bid.settings.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.platform.util.PasswordEncryptionUtil;
import com.xiyu.bid.settings.dto.SettingsResponse;
import com.xiyu.bid.settings.dto.SettingsUpdateRequest;
import com.xiyu.bid.settings.repository.SystemSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@DataJpaTest
@ActiveProfiles("test")
class SettingsServiceTest {

    @Autowired
    private SystemSettingRepository systemSettingRepository;

    private UserRepository userRepository;
    private ObjectMapper objectMapper;
    private SettingsPayloadMapper payloadMapper;
    private SettingsService settingsService;
    private AiProviderCatalog aiProviderCatalog;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        aiProviderCatalog = new AiProviderCatalog();
        payloadMapper = new SettingsPayloadMapper(new SettingsDefaultPayloadFactory());
        PasswordEncryptionUtil passwordEncryptionUtil = new PasswordEncryptionUtil();
        passwordEncryptionUtil.initialize();
        settingsService = new SettingsService(
                systemSettingRepository,
                userRepository,
                objectMapper,
                payloadMapper,
                passwordEncryptionUtil,
                aiProviderCatalog
        );
    }

    @Test
    void getSettings_ShouldSeedPersistentDefaultRecord() {
        assertEquals(0, systemSettingRepository.count());

        SettingsResponse settings = settingsService.getSettings();

        assertNotNull(settings);
        assertEquals("西域数智化投标管理平台", settings.getSystemConfig().getSysName());
        assertEquals(1, systemSettingRepository.count());
        assertTrue(systemSettingRepository.findByConfigKey("default").isPresent());
    }

    @Test
    void updateSettings_ShouldRemainVisibleAcrossNewServiceInstance() {
        settingsService.updateSettings(SettingsUpdateRequest.builder()
                .systemConfig(SettingsUpdateRequest.SystemConfigUpdate.builder()
                        .sysName("整改后平台")
                        .depositWarnDays(12)
                        .qualWarnDays(60)
                        .enableAI(false)
                        .build())
                .roles(List.of(
                        SettingsUpdateRequest.RoleSettingUpdate.builder()
                                .code("admin")
                                .name("管理员")
                                .description("持久化验证")
                                .userCount(2)
                                .dataScope("all")
                                .menuPermissions(List.of("dashboard", "settings"))
                                .allowedProjects(List.of("pg1"))
                                .allowedDepts(List.of("dept1"))
                                .build()
                ))
                .integrationConfig(SettingsUpdateRequest.IntegrationConfigUpdate.builder()
                        .apiKey("sk-system-test")
                        .aiBaseUrl("https://gateway.example.test/v1")
                        .aiModel("gpt-system")
                        .build())
                .build());

        PasswordEncryptionUtil passwordEncryptionUtil = new PasswordEncryptionUtil();
        passwordEncryptionUtil.initialize();
        SettingsService reloadedService = new SettingsService(
                systemSettingRepository,
                userRepository,
                objectMapper,
                payloadMapper,
                passwordEncryptionUtil,
                aiProviderCatalog
        );
        SettingsResponse reloaded = reloadedService.getSettings();

        assertEquals("整改后平台", reloaded.getSystemConfig().getSysName());
        assertEquals(12, reloaded.getSystemConfig().getDepositWarnDays());
        assertFalse(reloaded.getSystemConfig().getEnableAI());
        assertEquals("settings", reloaded.getRoles().get(0).getMenuPermissions().get(1));
        assertEquals("https://gateway.example.test/v1", reloaded.getIntegrationConfig().getAiBaseUrl());
        assertEquals("gpt-system", reloaded.getIntegrationConfig().getAiModel());
    }

    @Test
    void updateSettings_ShouldEncryptAndMaskAiProviderApiKey() {
        settingsService.updateSettings(SettingsUpdateRequest.builder()
                .aiModelConfig(SettingsUpdateRequest.AiModelConfigUpdate.builder()
                        .activeProvider("doubao")
                        .providers(List.of(
                                SettingsUpdateRequest.AiProviderSettingUpdate.builder()
                                        .providerCode("doubao")
                                        .enabled(true)
                                        .baseUrl("https://ark.cn-beijing.volces.com/api/v3/chat/completions")
                                        .model("doubao-test")
                                        .apiKeyPlaintext("sk-doubao-secret-1234")
                                        .build()
                        ))
                        .build())
                .build());

        SettingsResponse response = settingsService.getSettings();
        SettingsResponse.AiProviderSetting doubao = response.getAiModelConfig().getProviders().stream()
                .filter(provider -> "doubao".equals(provider.getProviderCode()))
                .findFirst()
                .orElseThrow();

        assertEquals("doubao", response.getAiModelConfig().getActiveProvider());
        assertTrue(doubao.getApiKeyConfigured());
        assertEquals("sk-d****1234", doubao.getApiKeyMasked());
        assertNull(doubao.getEncryptedApiKey());
        assertFalse(systemSettingRepository.findByConfigKey("default").orElseThrow()
                .getPayloadJson()
                .contains("sk-doubao-secret-1234"));
    }

    @Test
    void updateSettings_BlankApiKey_ShouldKeepExistingEncryptedKey() {
        settingsService.updateSettings(SettingsUpdateRequest.builder()
                .aiModelConfig(SettingsUpdateRequest.AiModelConfigUpdate.builder()
                        .activeProvider("deepseek")
                        .providers(List.of(SettingsUpdateRequest.AiProviderSettingUpdate.builder()
                                .providerCode("deepseek")
                                .apiKeyPlaintext("sk-deepseek-secret-5678")
                                .build()))
                        .build())
                .build());

        settingsService.updateSettings(SettingsUpdateRequest.builder()
                .aiModelConfig(SettingsUpdateRequest.AiModelConfigUpdate.builder()
                        .activeProvider("deepseek")
                        .providers(List.of(SettingsUpdateRequest.AiProviderSettingUpdate.builder()
                                .providerCode("deepseek")
                                .model("deepseek-chat-updated")
                                .apiKeyPlaintext("")
                                .build()))
                        .build())
                .build());

        SettingsResponse.AiProviderSetting deepseek = settingsService.getSettings().getAiModelConfig().getProviders().stream()
                .filter(provider -> "deepseek".equals(provider.getProviderCode()))
                .findFirst()
                .orElseThrow();

        assertTrue(deepseek.getApiKeyConfigured());
        assertEquals("sk-d****5678", deepseek.getApiKeyMasked());
        assertEquals("deepseek-chat-updated", deepseek.getModel());
    }

    @Test
    void updateSettings_WithUntrustedAiBaseUrl_ShouldRejectUpdate() {
        IllegalArgumentException exception = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> settingsService.updateSettings(SettingsUpdateRequest.builder()
                        .aiModelConfig(SettingsUpdateRequest.AiModelConfigUpdate.builder()
                                .providers(List.of(SettingsUpdateRequest.AiProviderSettingUpdate.builder()
                                        .providerCode("openai")
                                        .baseUrl("https://metadata.internal/latest")
                                        .build()))
                                .build())
                        .build())
        );

        assertEquals("AI API 地址必须匹配当前厂商的官方域名", exception.getMessage());
    }
}
