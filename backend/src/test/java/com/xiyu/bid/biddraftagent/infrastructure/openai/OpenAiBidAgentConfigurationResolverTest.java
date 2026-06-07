package com.xiyu.bid.biddraftagent.infrastructure.openai;

import com.xiyu.bid.settings.dto.SettingsResponse;
import com.xiyu.bid.settings.service.AiProviderCatalog;
import com.xiyu.bid.settings.service.SettingsService;
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
        SettingsService settingsService = mock(SettingsService.class);
        Environment environment = mock(Environment.class);
        when(settingsService.getInternalAiModelConfig()).thenReturn(null);
        when(settingsService.resolveAiApiKey("deepseek")).thenReturn(null);
        when(environment.getProperty("DEEPSEEK_API_KEY")).thenReturn(" sk-deepseek ");
        OpenAiBidAgentConfigurationResolver resolver = resolver(settingsService, environment);

        OpenAiBidAgentRequestConfig config = resolver.resolve("bid draft generation");

        assertThat(config.apiKey()).isEqualTo("sk-deepseek");
        assertThat(config.baseUrl()).isEqualTo("https://api.deepseek.com");
        assertThat(config.model()).isEqualTo("deepseek-chat");
        assertThat(config.timeout()).isEqualTo(Duration.ofSeconds(90));
        assertThat(config.apiStyle()).isEqualTo(OpenAiBidAgentApiStyle.CHAT_COMPLETIONS);
    }

    @Test
    void resolve_shouldUseActiveProviderConfigAndEnvironmentKeyWhenSpringKeyMissing() {
        SettingsService settingsService = mock(SettingsService.class);
        Environment environment = mock(Environment.class);
        when(settingsService.getInternalAiModelConfig()).thenReturn(aiModelConfig(
                "deepseek",
                "https://api.deepseek.com/chat/completions",
                "deepseek-chat"
        ));
        when(settingsService.resolveAiApiKey("deepseek")).thenReturn(null);
        when(environment.getProperty("DEEPSEEK_API_KEY")).thenReturn(" sk-deepseek ");
        OpenAiBidAgentConfigurationResolver resolver = resolver(settingsService, environment);

        OpenAiBidAgentRequestConfig config = resolver.resolve("tender document analysis");

        assertThat(config.apiKey()).isEqualTo("sk-deepseek");
        assertThat(config.baseUrl()).isEqualTo("https://api.deepseek.com");
        assertThat(config.model()).isEqualTo("deepseek-chat");
        assertThat(config.apiStyle()).isEqualTo(OpenAiBidAgentApiStyle.CHAT_COMPLETIONS);
        verify(settingsService, never()).getSettings();
    }

    @Test
    void resolve_shouldRaiseTimeoutFloorForChatCompletionProviders() {
        SettingsService settingsService = mock(SettingsService.class);
        Environment environment = mock(Environment.class);
        when(settingsService.getInternalAiModelConfig()).thenReturn(aiModelConfig(
                "deepseek",
                "https://api.deepseek.com/chat/completions",
                "deepseek-chat"
        ));
        when(settingsService.resolveAiApiKey("deepseek")).thenReturn(" sk-deepseek ");
        OpenAiBidAgentConfigurationResolver resolver = new OpenAiBidAgentConfigurationResolver(
                settingsService,
                new AiProviderCatalog(),
                environment,
                Duration.ofSeconds(30),
                Duration.ofSeconds(45)
        );

        OpenAiBidAgentRequestConfig config = resolver.resolve("tender document analysis");

        assertThat(config.timeout()).isEqualTo(Duration.ofSeconds(90));
    }

    @Test
    void resolve_shouldIgnoreActiveOpenAiProviderAndUseDeepSeekKey() {
        SettingsService settingsService = mock(SettingsService.class);
        Environment environment = mock(Environment.class);
        when(settingsService.getInternalAiModelConfig()).thenReturn(aiModelConfig(
                "openai",
                "https://api.openai.com/v1/chat/completions",
                "gpt-4o-mini"
        ));
        when(settingsService.resolveAiApiKey("openai")).thenReturn(" sk-openai ");
        when(settingsService.resolveAiApiKey("deepseek")).thenReturn(null);
        when(environment.getProperty("DEEPSEEK_API_KEY")).thenReturn(" sk-deepseek ");
        OpenAiBidAgentConfigurationResolver resolver = resolver(settingsService, environment);

        OpenAiBidAgentRequestConfig config = resolver.resolve("tender document analysis");

        assertThat(config.apiKey()).isEqualTo("sk-deepseek");
        assertThat(config.apiStyle()).isEqualTo(OpenAiBidAgentApiStyle.CHAT_COMPLETIONS);
        assertThat(config.baseUrl()).isEqualTo("https://api.deepseek.com");
        verify(settingsService, never()).resolveAiApiKey("openai");
    }

    @Test
    void resolve_shouldUseDeepSeekProviderSettingsKeyWhenLegacyIntegrationKeyExists() {
        SettingsService settingsService = mock(SettingsService.class);
        Environment environment = mock(Environment.class);
        when(settingsService.getSettings()).thenReturn(settingsWithApiKey(" sk-system "));
        when(settingsService.getInternalAiModelConfig()).thenReturn(aiModelConfig(
                "deepseek",
                "https://api.deepseek.com/chat/completions",
                "deepseek-chat"
        ));
        when(settingsService.resolveAiApiKey("deepseek")).thenReturn(" sk-deepseek ");
        OpenAiBidAgentConfigurationResolver resolver = resolver(settingsService, environment);

        OpenAiBidAgentRequestConfig config = resolver.resolve("tender document analysis");

        assertThat(config.apiKey()).isEqualTo("sk-deepseek");
        assertThat(config.apiStyle()).isEqualTo(OpenAiBidAgentApiStyle.CHAT_COMPLETIONS);
        verify(settingsService, never()).getSettings();
    }

    @Test
    void resolve_shouldUseDeepSeekDefaultsWhenLegacyIntegrationGatewayExists() {
        SettingsService settingsService = mock(SettingsService.class);
        Environment environment = mock(Environment.class);
        when(settingsService.getSettings()).thenReturn(settingsWithAiConfig(
                "sk-system",
                " https://gateway.example.test/v1 ",
                " gpt-system "
        ));
        when(settingsService.getInternalAiModelConfig()).thenReturn(null);
        when(settingsService.resolveAiApiKey("deepseek")).thenReturn(null);
        when(environment.getProperty("DEEPSEEK_API_KEY")).thenReturn(" sk-deepseek ");
        OpenAiBidAgentConfigurationResolver resolver = new OpenAiBidAgentConfigurationResolver(
                settingsService,
                new AiProviderCatalog(),
                environment,
                Duration.ofSeconds(90),
                Duration.ofSeconds(45)
        );

        OpenAiBidAgentRequestConfig config = resolver.resolve("tender document analysis");

        assertThat(config.baseUrl()).isEqualTo("https://api.deepseek.com");
        assertThat(config.model()).isEqualTo("deepseek-chat");
        assertThat(config.apiStyle()).isEqualTo(OpenAiBidAgentApiStyle.CHAT_COMPLETIONS);
        verify(settingsService, never()).getSettings();
    }

    @Test
    void resolve_shouldRejectMissingDeepSeekKey() {
        SettingsService settingsService = mock(SettingsService.class);
        Environment environment = mock(Environment.class);
        when(settingsService.getSettings()).thenReturn(settingsWithApiKey("sk_xiyu_bid_server_default"));
        when(settingsService.getInternalAiModelConfig()).thenReturn(null);
        when(settingsService.resolveAiApiKey("deepseek")).thenReturn(null);
        OpenAiBidAgentConfigurationResolver resolver = resolver(settingsService, environment);

        assertThatThrownBy(() -> resolver.resolve("bid draft generation"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DeepSeek API key must be configured for bid draft generation")
                .hasMessageContaining("DEEPSEEK_API_KEY");
        verify(settingsService, never()).getSettings();
    }

    @Test
    void resolveTenderIntake_shouldUseDeepSeekProviderSettingsAndSettingsApiKey() {
        SettingsService settingsService = mock(SettingsService.class);
        Environment environment = mock(Environment.class);
        when(settingsService.getInternalAiModelConfig()).thenReturn(aiModelConfig(
                "openai",
                "deepseek",
                "https://api.deepseek.com/chat/completions",
                "deepseek-reasoner"
        ));
        when(settingsService.resolveAiApiKey("deepseek")).thenReturn(" sk-deepseek-settings ");
        OpenAiBidAgentConfigurationResolver resolver = resolver(settingsService, environment);

        OpenAiBidAgentRequestConfig config = resolver.resolveTenderIntake();

        assertThat(config.apiKey()).isEqualTo("sk-deepseek-settings");
        assertThat(config.baseUrl()).isEqualTo("https://api.deepseek.com");
        assertThat(config.model()).isEqualTo("deepseek-reasoner");
        assertThat(config.timeout()).isEqualTo(Duration.ofSeconds(45));
        assertThat(config.apiStyle()).isEqualTo(OpenAiBidAgentApiStyle.CHAT_COMPLETIONS);
        verify(settingsService, never()).resolveAiApiKey("openai");
        verify(settingsService, never()).getSettings();
    }

    @Test
    void resolveTenderIntake_shouldUseDeepSeekEnvAndDefaultsWhenSettingsProviderMissing() {
        SettingsService settingsService = mock(SettingsService.class);
        Environment environment = mock(Environment.class);
        when(settingsService.getInternalAiModelConfig()).thenReturn(null);
        when(settingsService.resolveAiApiKey("deepseek")).thenReturn(null);
        when(environment.getProperty("DEEPSEEK_API_KEY")).thenReturn(" sk-deepseek-env ");
        OpenAiBidAgentConfigurationResolver resolver = resolver(settingsService, environment);

        OpenAiBidAgentRequestConfig config = resolver.resolveTenderIntake();

        assertThat(config.apiKey()).isEqualTo("sk-deepseek-env");
        assertThat(config.baseUrl()).isEqualTo("https://api.deepseek.com");
        assertThat(config.model()).isEqualTo("deepseek-chat");
        assertThat(config.apiStyle()).isEqualTo(OpenAiBidAgentApiStyle.CHAT_COMPLETIONS);
        verify(environment, never()).getProperty("OPENAI_API_KEY");
    }

    @Test
    void resolveTenderIntake_missingKey_shouldMentionDeepSeekEnvironmentKey() {
        SettingsService settingsService = mock(SettingsService.class);
        Environment environment = mock(Environment.class);
        AiProviderCatalog aiProviderCatalog = mock(AiProviderCatalog.class);
        when(aiProviderCatalog.normalize("deepseek")).thenReturn("deepseek");
        when(aiProviderCatalog.environmentKeys("deepseek")).thenReturn(List.of("DEEPSEEK_API_KEY_MISSING_TEST"));
        when(settingsService.getInternalAiModelConfig()).thenReturn(aiModelConfig(
                "deepseek",
                "https://api.deepseek.com/chat/completions",
                "deepseek-chat"
        ));
        when(settingsService.resolveAiApiKey("deepseek")).thenReturn(null);
        OpenAiBidAgentConfigurationResolver resolver = new OpenAiBidAgentConfigurationResolver(
                settingsService,
                aiProviderCatalog,
                environment,
                Duration.ofSeconds(90),
                Duration.ofSeconds(45)
        );

        assertThatThrownBy(resolver::resolveTenderIntake)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DeepSeek")
                .hasMessageContaining("DEEPSEEK_API_KEY");

        verify(settingsService, never()).getSettings();
    }

    private OpenAiBidAgentConfigurationResolver resolver(
            SettingsService settingsService,
            Environment environment
    ) {
        return new OpenAiBidAgentConfigurationResolver(
                settingsService,
                new AiProviderCatalog(),
                environment,
                Duration.ofSeconds(90),
                Duration.ofSeconds(45)
        );
    }

    private SettingsResponse settingsWithApiKey(String apiKey) {
        return settingsWithAiConfig(apiKey, null, null);
    }

    private SettingsResponse settingsWithAiConfig(String apiKey, String aiBaseUrl, String aiModel) {
        return SettingsResponse.builder()
                .integrationConfig(SettingsResponse.IntegrationConfig.builder()
                        .apiKey(apiKey)
                        .aiBaseUrl(aiBaseUrl)
                        .aiModel(aiModel)
                        .build())
                .build();
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
