package com.xiyu.bid.ai.client;

import com.xiyu.bid.ai.dto.AiAnalysisResponse;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.settings.dto.SettingsResponse;
import com.xiyu.bid.settings.service.AiProviderCatalog;
import com.xiyu.bid.settings.service.SettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoutingAiProviderTest {

    @Mock
    private SettingsService settingsService;

    @Mock
    private OpenAiCompatibleClient openAiCompatibleClient;

    @Mock
    private MockAiProvider mockAiProvider;

    @Mock
    private Environment environment;

    private final AiProviderCatalog aiProviderCatalog = new AiProviderCatalog();

    @Test
    void analyzeTender_ShouldRouteToActiveProviderFromSettings() {
        RoutingAiProvider provider = providerWithLegacyMode("openai");
        AiAnalysisResponse expected = response(88);
        when(settingsService.isAiEnabled()).thenReturn(true);
        when(settingsService.getInternalAiModelConfig()).thenReturn(config("deepseek"));
        when(settingsService.resolveAiApiKey("deepseek")).thenReturn("sk-configured");
        when(openAiCompatibleClient.analyzeTender(any(AiProviderRuntimeConfig.class), eq("content"), eq(Map.of())))
                .thenReturn(expected);

        AiAnalysisResponse actual = provider.analyzeTender("content", Map.of());

        assertThat(actual).isEqualTo(expected);
        verify(openAiCompatibleClient).analyzeTender(any(AiProviderRuntimeConfig.class), eq("content"), eq(Map.of()));
    }

    @Test
    void analyzeProject_ShouldUseEnvironmentFallbackWhenSettingsKeyMissing() {
        RoutingAiProvider provider = providerWithLegacyMode("openai");
        AiAnalysisResponse expected = response(77);
        when(settingsService.isAiEnabled()).thenReturn(true);
        when(settingsService.getInternalAiModelConfig()).thenReturn(config("qwen"));
        when(settingsService.resolveAiApiKey("qwen")).thenReturn(null);
        when(environment.getProperty("DASHSCOPE_API_KEY")).thenReturn("sk-env");
        when(openAiCompatibleClient.analyzeProject(any(AiProviderRuntimeConfig.class), eq(9L), eq(Map.of())))
                .thenReturn(expected);

        AiAnalysisResponse actual = provider.analyzeProject(9L, Map.of());

        assertThat(actual).isEqualTo(expected);
        verify(openAiCompatibleClient).analyzeProject(any(AiProviderRuntimeConfig.class), eq(9L), eq(Map.of()));
    }

    @Test
    void analyzeTender_WhenLegacyModeMockAndNoRealKey_ShouldUseMockProvider() {
        RoutingAiProvider provider = providerWithLegacyMode("mock");
        AiAnalysisResponse expected = response(66);
        when(settingsService.isAiEnabled()).thenReturn(true);
        when(mockAiProvider.analyzeTender("content", Map.of())).thenReturn(expected);

        AiAnalysisResponse actual = provider.analyzeTender("content", Map.of());

        assertThat(actual).isEqualTo(expected);
        verify(mockAiProvider).analyzeTender("content", Map.of());
        verify(openAiCompatibleClient, never()).analyzeTender(any(), any(), any());
    }

    @Test
    void analyzeTender_WhenAiDisabled_ShouldRejectWithoutCallingRealOrMockProvider() {
        RoutingAiProvider provider = providerWithLegacyMode("mock");
        when(settingsService.isAiEnabled()).thenReturn(false);

        assertThatThrownBy(() -> provider.analyzeTender("content", Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("AI 功能已在系统设置中关闭");

        verify(openAiCompatibleClient, never()).analyzeTender(any(), any(), any());
        verify(mockAiProvider, never()).analyzeTender(any(), any());
    }

    @Test
    void analyzeTender_WhenActiveProviderDisabled_ShouldRejectWithoutCallingProvider() {
        RoutingAiProvider provider = providerWithLegacyMode("openai");
        when(settingsService.isAiEnabled()).thenReturn(true);
        when(settingsService.getInternalAiModelConfig()).thenReturn(configWithProviderEnabled("openai", false));

        assertThatThrownBy(() -> provider.analyzeTender("content", Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("当前 AI 厂商已停用，请在系统设置中启用或切换厂商");

        verify(openAiCompatibleClient, never()).analyzeTender(any(), any(), any());
        verify(mockAiProvider, never()).analyzeTender(any(), any());
    }

    private RoutingAiProvider providerWithLegacyMode(String legacyMode) {
        RoutingAiProvider provider = new RoutingAiProvider(
                settingsService,
                openAiCompatibleClient,
                mockAiProvider,
                environment,
                aiProviderCatalog
        );
        ReflectionTestUtils.setField(provider, "legacyProviderMode", legacyMode);
        return provider;
    }

    private SettingsResponse.AiModelConfig config(String activeProvider) {
        return SettingsResponse.AiModelConfig.builder()
                .activeProvider(activeProvider)
                .providers(List.of(
                        provider("openai", "https://api.openai.com/v1/chat/completions", "gpt-4o-mini"),
                        provider("deepseek", "https://api.deepseek.com/chat/completions", "deepseek-chat"),
                        provider("qwen", "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions", "qwen-plus"),
                        provider("doubao", "https://ark.cn-beijing.volces.com/api/v3/chat/completions", "doubao-test")
                ))
                .build();
    }

    private SettingsResponse.AiModelConfig configWithProviderEnabled(String activeProvider, boolean enabled) {
        SettingsResponse.AiModelConfig config = config(activeProvider);
        config.getProviders().stream()
                .filter(provider -> activeProvider.equals(provider.getProviderCode()))
                .findFirst()
                .orElseThrow()
                .setEnabled(enabled);
        return config;
    }

    private SettingsResponse.AiProviderSetting provider(String code, String baseUrl, String model) {
        return SettingsResponse.AiProviderSetting.builder()
                .providerCode(code)
                .enabled(true)
                .baseUrl(baseUrl)
                .model(model)
                .build();
    }

    private AiAnalysisResponse response(int score) {
        return AiAnalysisResponse.builder()
                .score(score)
                .riskLevel(Tender.RiskLevel.LOW)
                .build();
    }
}
