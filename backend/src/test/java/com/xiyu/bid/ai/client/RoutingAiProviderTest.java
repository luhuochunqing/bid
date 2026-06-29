package com.xiyu.bid.ai.client;

import com.xiyu.bid.ai.dto.AiAnalysisResponse;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.settings.dto.SettingsResponse;
import com.xiyu.bid.settings.service.AiProviderCatalog;
import com.xiyu.bid.settings.service.AiConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

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
    private AiConfigService aiConfigService;

    @Mock
    private OpenAiCompatibleClient openAiCompatibleClient;

    @Mock
    private MockAiProvider mockAiProvider;

    @Mock
    private Environment environment;

    private final AiProviderCatalog aiProviderCatalog = new AiProviderCatalog();

    @Test
    void analyzeTender_WhenAiConfigured_ShouldRouteToRealProviderNotMock() {
        RoutingAiProvider provider = createProvider();
        AiAnalysisResponse expected = response(88);
        when(aiConfigService.isAiEnabled()).thenReturn(true);
        when(aiConfigService.getInternalAiModelConfig()).thenReturn(config("deepseek"));
        when(aiConfigService.resolveAiApiKey("deepseek")).thenReturn("sk-configured");
        when(openAiCompatibleClient.analyzeTender(any(AiProviderRuntimeConfig.class), eq("content"), eq(Map.of())))
                .thenReturn(expected);

        AiAnalysisResponse actual = provider.analyzeTender("content", Map.of());

        assertThat(actual).isEqualTo(expected);
        verify(openAiCompatibleClient).analyzeTender(any(AiProviderRuntimeConfig.class), eq("content"), eq(Map.of()));
        verify(mockAiProvider, never()).analyzeTender(any(), any());
    }

    @Test
    void analyzeProject_ShouldUseEnvironmentFallbackWhenSettingsKeyMissing() {
        RoutingAiProvider provider = createProvider();
        AiAnalysisResponse expected = response(77);
        when(aiConfigService.isAiEnabled()).thenReturn(true);
        when(aiConfigService.getInternalAiModelConfig()).thenReturn(config("qwen"));
        when(aiConfigService.resolveAiApiKey("qwen")).thenReturn(null);
        when(environment.getProperty("DASHSCOPE_API_KEY")).thenReturn("sk-env");
        when(openAiCompatibleClient.analyzeProject(any(AiProviderRuntimeConfig.class), eq(9L), eq(Map.of())))
                .thenReturn(expected);

        AiAnalysisResponse actual = provider.analyzeProject(9L, Map.of());

        assertThat(actual).isEqualTo(expected);
        verify(openAiCompatibleClient).analyzeProject(any(AiProviderRuntimeConfig.class), eq(9L), eq(Map.of()));
    }

    @Test
    void analyzeTender_WhenAiDisabled_ShouldRejectWithoutCallingRealOrMockProvider() {
        RoutingAiProvider provider = createProvider();
        when(aiConfigService.isAiEnabled()).thenReturn(false);

        assertThatThrownBy(() -> provider.analyzeTender("content", Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("AI 功能已在系统设置中关闭");

        verify(openAiCompatibleClient, never()).analyzeTender(any(), any(), any());
        verify(mockAiProvider, never()).analyzeTender(any(), any());
    }

    @Test
    void analyzeTender_WhenActiveProviderDisabled_ShouldRejectWithoutCallingProvider() {
        RoutingAiProvider provider = createProvider();
        when(aiConfigService.isAiEnabled()).thenReturn(true);
        when(aiConfigService.getInternalAiModelConfig()).thenReturn(configWithProviderEnabled("openai", false));

        assertThatThrownBy(() -> provider.analyzeTender("content", Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("当前 AI 厂商已停用，请在系统设置中启用或切换厂商");

        verify(openAiCompatibleClient, never()).analyzeTender(any(), any(), any());
        verify(mockAiProvider, never()).analyzeTender(any(), any());
    }

    private RoutingAiProvider createProvider() {
        return new RoutingAiProvider(
                aiConfigService,
                openAiCompatibleClient,
                mockAiProvider,
                environment,
                aiProviderCatalog
        );
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
