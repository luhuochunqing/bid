package com.xiyu.bid.ai.client;

import com.xiyu.bid.ai.dto.AiAnalysisResponse;
import com.xiyu.bid.settings.dto.SettingsResponse;
import com.xiyu.bid.settings.service.AiProviderCatalog;
import com.xiyu.bid.settings.service.AiConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Primary
@RequiredArgsConstructor
public class RoutingAiProvider implements AiProvider {

    private final AiConfigService aiConfigService;
    private final OpenAiCompatibleClient openAiCompatibleClient;
    private final MockAiProvider mockAiProvider;
    private final Environment environment;
    private final AiProviderCatalog aiProviderCatalog;

    @Value("${ai.provider:mock}")
    private String legacyProviderMode;

    @Override
    public AiAnalysisResponse analyzeTender(String content, Map<String, Object> context) {
        AiProviderRuntimeConfig config = resolveActiveConfig();
        if (config == null) {
            return mockAiProvider.analyzeTender(content, context);
        }
        return openAiCompatibleClient.analyzeTender(config, content, context);
    }

    @Override
    public AiAnalysisResponse analyzeProject(Long projectId, Map<String, Object> context) {
        AiProviderRuntimeConfig config = resolveActiveConfig();
        if (config == null) {
            return mockAiProvider.analyzeProject(projectId, context);
        }
        return openAiCompatibleClient.analyzeProject(config, projectId, context);
    }

    public AiProviderRuntimeConfig resolveActiveConfig() {
        if (!aiConfigService.isAiEnabled()) {
            throw new IllegalStateException("AI 功能已在系统设置中关闭");
        }
        if ("mock".equalsIgnoreCase(legacyProviderMode)) {
            return null;
        }

        SettingsResponse.AiModelConfig aiModelConfig = aiConfigService.getInternalAiModelConfig();
        String providerCode = normalize(aiModelConfig.getActiveProvider());
        SettingsResponse.AiProviderSetting provider = aiModelConfig.getProviders().stream()
                .filter(item -> providerCode.equals(normalize(item.getProviderCode())))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Active AI provider is not configured: " + providerCode));

        if (Boolean.FALSE.equals(provider.getEnabled())) {
            throw new IllegalStateException("当前 AI 厂商已停用，请在系统设置中启用或切换厂商");
        }

        aiProviderCatalog.validateBaseUrl(providerCode, provider.getBaseUrl());
        String apiKey = aiConfigService.resolveAiApiKey(providerCode);
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = resolveEnvironmentApiKey(providerCode);
        }

        return new AiProviderRuntimeConfig(providerCode, provider.getBaseUrl(), provider.getModel(), apiKey);
    }

    private String resolveEnvironmentApiKey(String providerCode) {
        return firstConfigured(aiProviderCatalog.environmentKeys(providerCode).toArray(String[]::new));
    }

    private String firstConfigured(String... keys) {
        for (String key : keys) {
            String value = environment.getProperty(key);
            if (value == null || value.isBlank()) {
                value = System.getenv(key);
            }
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String normalize(String value) {
        return aiProviderCatalog.normalize(value);
    }
}
