package com.xiyu.bid.biddraftagent.infrastructure.openai;

import com.xiyu.bid.settings.dto.SettingsResponse;
import com.xiyu.bid.settings.service.AiProviderCatalog;
import com.xiyu.bid.settings.service.AiConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OpenAiBidAgentConfigurationResolverTest {

    @Test
    void resolve_shouldUseDeepSeekEnvironmentKeyBeforeLegacySpringOpenAiKey() {
        AiConfigService aiConfigService = mock(AiConfigService.class);
        Environment environment = mock(Environment.class);
        when(aiConfigService.getInternalAiModelConfig()).thenReturn(null);
        when(aiConfigService.resolveAiApiKey("deepseek")).thenReturn(null);
        when(environment.getProperty("DEEPSEEK_API_KEY")).thenReturn(" sk-deepseek ");
        OpenAiBidAgentConfigurationResolver resolver = resolver(aiConfigService, environment);

        OpenAiBidAgentRequestConfig config = resolver.resolve("bid draft generation");

        assertThat(config.apiKey()).isEqualTo("sk-deepseek");
        assertThat(config.baseUrl()).isEqualTo("https://api.deepseek.com");
        assertThat(config.model()).isEqualTo("deepseek-chat");
        assertThat(config.timeout()).isEqualTo(Duration.ofSeconds(90));
        assertThat(config.apiStyle()).isEqualTo(OpenAiBidAgentApiStyle.CHAT_COMPLETIONS);
    }

    @Test
    void resolve_shouldUseActiveProviderConfigAndEnvironmentKeyWhenSpringKeyMissing() {
        AiConfigService aiConfigService = mock(AiConfigService.class);
        Environment environment = mock(Environment.class);
        when(aiConfigService.getInternalAiModelConfig()).thenReturn(aiModelConfig(
                "deepseek",
                "https://api.deepseek.com/chat/completions",
                "deepseek-chat"
        ));
        when(aiConfigService.resolveAiApiKey("deepseek")).thenReturn(null);
        when(environment.getProperty("DEEPSEEK_API_KEY")).thenReturn(" sk-deepseek ");
        OpenAiBidAgentConfigurationResolver resolver = resolver(aiConfigService, environment);

        OpenAiBidAgentRequestConfig config = resolver.resolve("tender document analysis");

        assertThat(config.apiKey()).isEqualTo("sk-deepseek");
        assertThat(config.baseUrl()).isEqualTo("https://api.deepseek.com");
        assertThat(config.model()).isEqualTo("deepseek-chat");
        assertThat(config.apiStyle()).isEqualTo(OpenAiBidAgentApiStyle.CHAT_COMPLETIONS);
    }

    @Test
    void resolve_shouldRaiseTimeoutFloorForChatCompletionProviders() {
        AiConfigService aiConfigService = mock(AiConfigService.class);
        Environment environment = mock(Environment.class);
        when(aiConfigService.getInternalAiModelConfig()).thenReturn(aiModelConfig(
                "deepseek",
                "https://api.deepseek.com/chat/completions",
                "deepseek-chat"
        ));
        when(aiConfigService.resolveAiApiKey("deepseek")).thenReturn(" sk-deepseek ");
        OpenAiBidAgentConfigurationResolver resolver = new OpenAiBidAgentConfigurationResolver(
                aiConfigService,
                new AiProviderCatalog(),
                environment,
                Duration.ofSeconds(30),
                Duration.ofSeconds(45)
        );

        OpenAiBidAgentRequestConfig config = resolver.resolve("tender document analysis");

        assertThat(config.timeout()).isEqualTo(Duration.ofSeconds(90));
    }

    @Test
    void resolve_shouldUseActiveProviderWhenConfigured() {
        AiConfigService aiConfigService = mock(AiConfigService.class);
        Environment environment = mock(Environment.class);
        when(aiConfigService.getInternalAiModelConfig()).thenReturn(aiModelConfig(
                "openai",
                "https://api.openai.com/v1/chat/completions",
                "gpt-4o-mini"
        ));
        when(aiConfigService.resolveAiApiKey("openai")).thenReturn(" sk-openai ");
        when(aiConfigService.resolveAiApiKey("deepseek")).thenReturn(null);
        when(environment.getProperty("DEEPSEEK_API_KEY")).thenReturn(" sk-deepseek ");
        OpenAiBidAgentConfigurationResolver resolver = resolver(aiConfigService, environment);

        OpenAiBidAgentRequestConfig config = resolver.resolve("tender document analysis");

        assertThat(config.apiKey()).isEqualTo("sk-openai");
        assertThat(config.apiStyle()).isEqualTo(OpenAiBidAgentApiStyle.CHAT_COMPLETIONS);
        assertThat(config.baseUrl()).isEqualTo("https://api.openai.com/v1");
    }

    @Test
    void resolve_shouldUseDeepSeekProviderSettingsKeyWhenLegacyIntegrationKeyExists() {
        AiConfigService aiConfigService = mock(AiConfigService.class);
        Environment environment = mock(Environment.class);
        when(aiConfigService.getInternalAiModelConfig()).thenReturn(settingsWithApiKey(" sk-system "));
        when(aiConfigService.getInternalAiModelConfig()).thenReturn(aiModelConfig(
                "deepseek",
                "https://api.deepseek.com/chat/completions",
                "deepseek-chat"
        ));
        when(aiConfigService.resolveAiApiKey("deepseek")).thenReturn(" sk-deepseek ");
        OpenAiBidAgentConfigurationResolver resolver = resolver(aiConfigService, environment);

        OpenAiBidAgentRequestConfig config = resolver.resolve("tender document analysis");

        assertThat(config.apiKey()).isEqualTo("sk-deepseek");
        assertThat(config.apiStyle()).isEqualTo(OpenAiBidAgentApiStyle.CHAT_COMPLETIONS);
    }

    @Test
    void resolve_shouldUseDeepSeekDefaultsWhenLegacyIntegrationGatewayExists() {
        AiConfigService aiConfigService = mock(AiConfigService.class);
        Environment environment = mock(Environment.class);
        when(aiConfigService.getInternalAiModelConfig()).thenReturn(settingsWithAiConfig(
                "sk-system",
                " https://gateway.example.test/v1 ",
                " gpt-system "
        ));
        when(aiConfigService.getInternalAiModelConfig()).thenReturn(null);
        when(aiConfigService.resolveAiApiKey("deepseek")).thenReturn(null);
        when(environment.getProperty("DEEPSEEK_API_KEY")).thenReturn(" sk-deepseek ");
        OpenAiBidAgentConfigurationResolver resolver = new OpenAiBidAgentConfigurationResolver(
                aiConfigService,
                new AiProviderCatalog(),
                environment,
                Duration.ofSeconds(90),
                Duration.ofSeconds(45)
        );

        OpenAiBidAgentRequestConfig config = resolver.resolve("tender document analysis");

        assertThat(config.baseUrl()).isEqualTo("https://api.deepseek.com");
        assertThat(config.model()).isEqualTo("deepseek-chat");
        assertThat(config.apiStyle()).isEqualTo(OpenAiBidAgentApiStyle.CHAT_COMPLETIONS);
    }

    @Test
    void resolve_shouldRejectMissingDeepSeekKey() {
        AiConfigService aiConfigService = mock(AiConfigService.class);
        Environment environment = mock(Environment.class);
        when(aiConfigService.getInternalAiModelConfig()).thenReturn(settingsWithApiKey("sk_xiyu_bid_server_default"));
        when(aiConfigService.getInternalAiModelConfig()).thenReturn(null);
        when(aiConfigService.resolveAiApiKey("deepseek")).thenReturn(null);
        OpenAiBidAgentConfigurationResolver resolver = resolver(aiConfigService, environment);

        assertThatThrownBy(() -> resolver.resolve("bid draft generation"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DeepSeek API key must be configured for bid draft generation")
                .hasMessageContaining("DEEPSEEK_API_KEY");
    }

    @Test
    void resolveTenderIntake_shouldUseDeepSeekProviderSettingsAndSettingsApiKey() {
        AiConfigService aiConfigService = mock(AiConfigService.class);
        Environment environment = mock(Environment.class);
        when(aiConfigService.getInternalAiModelConfig()).thenReturn(aiModelConfig(
                "openai",
                "deepseek",
                "https://api.deepseek.com/chat/completions",
                "deepseek-reasoner"
        ));
        when(aiConfigService.resolveAiApiKey("deepseek")).thenReturn(" sk-deepseek-settings ");
        OpenAiBidAgentConfigurationResolver resolver = resolver(aiConfigService, environment);

        OpenAiBidAgentRequestConfig config = resolver.resolveTenderIntake();

        assertThat(config.apiKey()).isEqualTo("sk-deepseek-settings");
        assertThat(config.baseUrl()).isEqualTo("https://api.deepseek.com");
        assertThat(config.model()).isEqualTo("deepseek-reasoner");
        assertThat(config.timeout()).isEqualTo(Duration.ofSeconds(45));
        assertThat(config.apiStyle()).isEqualTo(OpenAiBidAgentApiStyle.CHAT_COMPLETIONS);
        verify(aiConfigService, never()).resolveAiApiKey("openai");
    }

    @Test
    void resolveTenderIntake_shouldUseDeepSeekEnvAndDefaultsWhenSettingsProviderMissing() {
        AiConfigService aiConfigService = mock(AiConfigService.class);
        Environment environment = mock(Environment.class);
        when(aiConfigService.getInternalAiModelConfig()).thenReturn(null);
        when(aiConfigService.resolveAiApiKey("deepseek")).thenReturn(null);
        when(environment.getProperty("DEEPSEEK_API_KEY")).thenReturn(" sk-deepseek-env ");
        OpenAiBidAgentConfigurationResolver resolver = resolver(aiConfigService, environment);

        OpenAiBidAgentRequestConfig config = resolver.resolveTenderIntake();

        assertThat(config.apiKey()).isEqualTo("sk-deepseek-env");
        assertThat(config.baseUrl()).isEqualTo("https://api.deepseek.com");
        assertThat(config.model()).isEqualTo("deepseek-chat");
        assertThat(config.apiStyle()).isEqualTo(OpenAiBidAgentApiStyle.CHAT_COMPLETIONS);
        verify(environment, never()).getProperty("OPENAI_API_KEY");
    }

    @Test
    void resolveTenderIntake_missingKey_shouldMentionDeepSeekEnvironmentKey() {
        AiConfigService aiConfigService = mock(AiConfigService.class);
        Environment environment = mock(Environment.class);
        AiProviderCatalog aiProviderCatalog = mock(AiProviderCatalog.class);
        when(aiProviderCatalog.normalize("deepseek")).thenReturn("deepseek");
        when(aiProviderCatalog.environmentKeys("deepseek")).thenReturn(List.of("DEEPSEEK_API_KEY"));
        when(aiProviderCatalog.defaultActiveProvider()).thenReturn("deepseek");
        when(aiProviderCatalog.defaultProviderSetting("deepseek")).thenReturn(SettingsResponse.AiProviderSetting.builder()
                .providerCode("deepseek")
                .providerName("DeepSeek")
                .enabled(true)
                .baseUrl("https://api.deepseek.com/chat/completions")
                .model("deepseek-chat")
                .build());
        when(aiConfigService.getInternalAiModelConfig()).thenReturn(aiModelConfig(
                "deepseek",
                "https://api.deepseek.com/chat/completions",
                "deepseek-chat"
        ));
        when(aiConfigService.resolveAiApiKey("deepseek")).thenReturn(null);
        OpenAiBidAgentConfigurationResolver resolver = new OpenAiBidAgentConfigurationResolver(
                aiConfigService,
                aiProviderCatalog,
                environment,
                Duration.ofSeconds(90),
                Duration.ofSeconds(45)
        );

        assertThatThrownBy(resolver::resolveTenderIntake)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DeepSeek")
                .hasMessageContaining("DEEPSEEK_API_KEY");

    }

    // ── CO-301: 豆包 endpoint 规范化测试 ──────────────────────────────────────

    @Test
    void resolve_doubaoProvider_preservesV3VersionInBaseUrl() {
        AiConfigService aiConfigService = mock(AiConfigService.class);
        Environment environment = mock(Environment.class);
        when(aiConfigService.getInternalAiModelConfig()).thenReturn(aiModelConfig(
                "doubao",
                "https://ark.cn-beijing.volces.com/api/v3",
                "doubao-pro-32k"
        ));
        when(aiConfigService.resolveAiApiKey("doubao")).thenReturn("doubao-api-key");
        OpenAiBidAgentConfigurationResolver resolver = resolver(aiConfigService, environment);

        OpenAiBidAgentRequestConfig config = resolver.resolve("tender document analysis");

        assertThat(config.apiKey()).isEqualTo("doubao-api-key");
        assertThat(config.baseUrl()).isEqualTo("https://ark.cn-beijing.volces.com/api/v3");
        assertThat(config.model()).isEqualTo("doubao-pro-32k");
    }

    @Test
    void resolve_doubaoProvider_normalizesBaseUrlStripsChatCompletionsOnly() {
        AiConfigService aiConfigService = mock(AiConfigService.class);
        Environment environment = mock(Environment.class);
        when(aiConfigService.getInternalAiModelConfig()).thenReturn(aiModelConfig(
                "doubao",
                "https://ark.cn-beijing.volces.com/api/v3/chat/completions",
                "doubao-pro-32k"
        ));
        when(aiConfigService.resolveAiApiKey("doubao")).thenReturn("doubao-api-key");
        OpenAiBidAgentConfigurationResolver resolver = resolver(aiConfigService, environment);

        OpenAiBidAgentRequestConfig config = resolver.resolve("tender document analysis");

        assertThat(config.baseUrl()).isEqualTo("https://ark.cn-beijing.volces.com/api/v3");
    }

    @Test
    void resolveTenderIntake_doubaoProvider_returnsCorrectEndpoint() {
        AiConfigService aiConfigService = mock(AiConfigService.class);
        Environment environment = mock(Environment.class);
        when(aiConfigService.getInternalAiModelConfig()).thenReturn(aiModelConfig(
                "doubao",
                "https://ark.cn-beijing.volces.com/api/v3",
                "doubao-pro-32k"
        ));
        when(aiConfigService.resolveAiApiKey("doubao")).thenReturn("doubao-api-key");
        OpenAiBidAgentConfigurationResolver resolver = resolver(aiConfigService, environment);

        OpenAiBidAgentRequestConfig config = resolver.resolveTenderIntake();

        assertThat(config.baseUrl()).isEqualTo("https://ark.cn-beijing.volces.com/api/v3");
        assertThat(config.timeout()).isEqualTo(Duration.ofSeconds(45));
    }

    @Test
    void resolve_doubaoProvider_withCompatibleModePath_preservesVersion() {
        AiConfigService aiConfigService = mock(AiConfigService.class);
        Environment environment = mock(Environment.class);
        when(aiConfigService.getInternalAiModelConfig()).thenReturn(aiModelConfig(
                "doubao",
                "https://ark.cn-beijing.volces.com/compatible-mode/v1",
                "doubao-pro-32k"
        ));
        when(aiConfigService.resolveAiApiKey("doubao")).thenReturn("doubao-api-key");
        OpenAiBidAgentConfigurationResolver resolver = resolver(aiConfigService, environment);

        OpenAiBidAgentRequestConfig config = resolver.resolve("tender document analysis");

        assertThat(config.baseUrl()).isEqualTo("https://ark.cn-beijing.volces.com/compatible-mode/v1");
    }

    private OpenAiBidAgentConfigurationResolver resolver(
            AiConfigService aiConfigService,
            Environment environment
    ) {
        return new OpenAiBidAgentConfigurationResolver(
                aiConfigService,
                new AiProviderCatalog(),
                environment,
                Duration.ofSeconds(90),
                Duration.ofSeconds(45)
        );
    }

    private SettingsResponse.AiModelConfig settingsWithApiKey(String apiKey) {
        return aiModelConfig("deepseek", "https://api.deepseek.com", "deepseek-chat");
    }

    private SettingsResponse.AiModelConfig settingsWithAiConfig(String apiKey, String aiBaseUrl, String aiModel) {
        String baseUrl = aiBaseUrl != null ? aiBaseUrl.trim() : "https://api.deepseek.com";
        String model = aiModel != null ? aiModel.trim() : "deepseek-chat";
        return aiModelConfig("deepseek", baseUrl, model);
    }

    private SettingsResponse.AiModelConfig aiModelConfig(String activeProvider, String baseUrl, String model) {
        return aiModelConfig(activeProvider, activeProvider, baseUrl, model);
    }

    private SettingsResponse.AiModelConfig aiModelConfig(
            String activeProvider,
            String providerCode,
            String baseUrl,
            String model
    ) {
        return SettingsResponse.AiModelConfig.builder()
                .activeProvider(activeProvider)
                .providers(List.of(SettingsResponse.AiProviderSetting.builder()
                        .providerCode(providerCode)
                        .providerName(providerCode)
                        .enabled(true)
                        .baseUrl(baseUrl)
                        .model(model)
                        .lastTestAt(Instant.now())
                        .build()))
                .build();
    }
}
