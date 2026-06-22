package com.xiyu.bid.biddraftagent.infrastructure.openai;

import com.xiyu.bid.settings.dto.SettingsResponse;
import com.xiyu.bid.settings.service.AiProviderCatalog;
import com.xiyu.bid.settings.service.AiConfigService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Service
public class OpenAiBidAgentConfigurationResolver {

    private static final String TENDER_INTAKE_USE_CASE = "tender intake document analysis";
    private static final Duration CHAT_COMPLETION_MIN_TIMEOUT = Duration.ofSeconds(90);

    private final AiConfigService aiConfigService;
    private final AiProviderCatalog aiProviderCatalog;
    private final Environment environment;
    private final Duration timeout;
    private final Duration tenderIntakeTimeout;

    public OpenAiBidAgentConfigurationResolver(
            AiConfigService pAiConfigService,
            AiProviderCatalog pAiProviderCatalog,
            Environment pEnvironment,
            @Value("${ai.openai.timeout:PT30S}") Duration pTimeout,
            @Value("${ai.deepseek.tender-intake-timeout:PT45S}") Duration pTenderIntakeTimeout
    ) {
        this.aiConfigService = pAiConfigService;
        this.aiProviderCatalog = pAiProviderCatalog;
        this.environment = pEnvironment;
        this.timeout = pTimeout;
        this.tenderIntakeTimeout = pTenderIntakeTimeout;
    }

    OpenAiBidAgentRequestConfig resolve(String useCase) {
        Duration requestTimeout = effectiveChatCompletionTimeout();
        return activeProviderRequest(requestTimeout)
                .orElseThrow(() -> missingProviderConfiguration(useCase));
    }

    OpenAiBidAgentRequestConfig resolveTenderIntake() {
        return activeProviderRequest(tenderIntakeTimeout)
                .orElseThrow(() -> missingProviderConfiguration(
                        TENDER_INTAKE_USE_CASE,
                        aiProviderCatalog.defaultActiveProvider()
                ));
    }

    boolean hasTenderIntakeConfiguration() {
        return activeProviderRequest(tenderIntakeTimeout).isPresent();
    }

    private Optional<OpenAiBidAgentRequestConfig> activeProviderRequest(Duration requestTimeout) {
        SettingsResponse.AiModelConfig aiModelConfig = aiConfigService.getInternalAiModelConfig();
        if (aiModelConfig == null) {
            return fallbackToDefaultProvider(requestTimeout);
        }

        String activeProviderCode = aiModelConfig.getActiveProvider();
        if (activeProviderCode == null || activeProviderCode.isBlank()) {
            activeProviderCode = aiProviderCatalog.defaultActiveProvider();
        }

        SettingsResponse.AiProviderSetting provider = findProvider(aiModelConfig, activeProviderCode).orElse(null);
        String resolvedProviderCode;

        if (provider == null) {
            provider = firstEnabledProvider(aiModelConfig);
            if (provider == null) {
                return fallbackToDefaultProvider(requestTimeout);
            }
            resolvedProviderCode = provider.getProviderCode();
        } else {
            resolvedProviderCode = activeProviderCode;
        }

        final String finalResolvedProviderCode = resolvedProviderCode;
        SettingsResponse.AiProviderSetting finalProvider = provider;
        SettingsResponse.AiProviderSetting defaultSetting = aiProviderCatalog.defaultProviderSetting(finalResolvedProviderCode);

        return resolveApiKey(finalResolvedProviderCode)
                .map(apiKey -> buildRequestConfig(
                        apiKey,
                        firstNonBlank(finalProvider.getBaseUrl(), defaultSetting.getBaseUrl()),
                        firstNonBlank(finalProvider.getModel(), defaultSetting.getModel()),
                        requestTimeout,
                        finalResolvedProviderCode
                ));
    }

    private SettingsResponse.AiProviderSetting firstEnabledProvider(SettingsResponse.AiModelConfig aiModelConfig) {
        List<SettingsResponse.AiProviderSetting> providers = aiModelConfig.getProviders();
        if (providers == null || providers.isEmpty()) {
            return null;
        }
        return providers.stream()
                .filter(provider -> !Boolean.FALSE.equals(provider.getEnabled()))
                .findFirst()
                .orElse(null);
    }

    private Optional<OpenAiBidAgentRequestConfig> fallbackToDefaultProvider(Duration requestTimeout) {
        String defaultProvider = aiProviderCatalog.defaultActiveProvider();
        return resolveApiKey(defaultProvider)
                .map(apiKey -> {
                    SettingsResponse.AiProviderSetting defaultSetting = aiProviderCatalog.defaultProviderSetting(defaultProvider);
                    return buildRequestConfig(
                            apiKey,
                            defaultSetting.getBaseUrl(),
                            defaultSetting.getModel(),
                            requestTimeout,
                            defaultProvider
                    );
                });
    }

    private OpenAiBidAgentRequestConfig buildRequestConfig(
            String apiKey,
            String rawBaseUrl,
            String rawModel,
            Duration requestTimeout,
            String providerCode
    ) {
        String normalizedBaseUrl = normalizedBaseUrl(rawBaseUrl);
        return new OpenAiBidAgentRequestConfig(
                apiKey,
                normalizedBaseUrl,
                rawModel,
                requestTimeout,
                OpenAiBidAgentApiStyle.CHAT_COMPLETIONS,
                buildFullEndpoint(normalizedBaseUrl, providerCode)
        );
    }

    private IllegalStateException missingProviderConfiguration(String useCase) {
        return missingProviderConfiguration(useCase, aiProviderCatalog.defaultActiveProvider());
    }

    private IllegalStateException missingProviderConfiguration(String useCase, String providerCode) {
        String safeProviderCode = providerCode != null ? providerCode : "AI";
        String providerName = safeProviderCode;
        List<String> envKeys = List.of();
        try {
            SettingsResponse.AiProviderSetting setting = aiProviderCatalog.defaultProviderSetting(safeProviderCode);
            if (setting != null && setting.getProviderName() != null) {
                providerName = setting.getProviderName();
            }
            envKeys = aiProviderCatalog.environmentKeys(safeProviderCode);
        } catch (IllegalArgumentException ignored) {
        }
        StringBuilder message = new StringBuilder(providerName != null ? providerName : safeProviderCode)
                .append(" API key must be configured for ")
                .append(useCase)
                .append("; configure in system settings");
        if (!envKeys.isEmpty()) {
            message.append(" or set environment variable ").append(String.join(" or ", envKeys));
        }
        return new IllegalStateException(message.toString());
    }

    private Optional<String> resolveApiKey(String providerCode) {
        return usableValue(aiConfigService.resolveAiApiKey(providerCode))
                .or(() -> providerEnvironmentApiKey(providerCode));
    }

    private Optional<SettingsResponse.AiProviderSetting> findProvider(
            SettingsResponse.AiModelConfig aiModelConfig,
            String providerCode
    ) {
        List<SettingsResponse.AiProviderSetting> providers = aiModelConfig.getProviders();
        if (providers == null || providers.isEmpty()) {
            return Optional.empty();
        }
        return providers.stream()
                .filter(provider -> providerCode.equals(aiProviderCatalog.normalize(provider.getProviderCode())))
                .filter(provider -> !Boolean.FALSE.equals(provider.getEnabled()))
                .findFirst();
    }

    private Optional<String> providerEnvironmentApiKey(String providerCode) {
        for (String keyName : aiProviderCatalog.environmentKeys(providerCode)) {
            Optional<String> propertyValue = usableValue(environment.getProperty(keyName));
            if (propertyValue.isPresent()) {
                return propertyValue;
            }
        }
        return Optional.empty();
    }

    private Optional<String> usableValue(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value.trim());
    }

    private String firstNonBlank(String value, String defaultValue) {
        return usableValue(value).orElse(defaultValue);
    }

    private String normalizedBaseUrl(String candidate) {
        if (candidate == null) {
            return "";
        }
        String trimmed = candidate.trim();
        if (trimmed.endsWith("/chat/completions")) {
            trimmed = trimmed.substring(0, trimmed.length() - "/chat/completions".length());
        } else if (trimmed.endsWith("/responses")) {
            trimmed = trimmed.substring(0, trimmed.length() - "/responses".length());
        }
        if (trimmed.endsWith("/v1")) {
            trimmed = trimmed.substring(0, trimmed.length() - "/v1".length());
        } else if (trimmed.endsWith("/v3")) {
            trimmed = trimmed.substring(0, trimmed.length() - "/v3".length());
        } else if (trimmed.endsWith("/v2")) {
            trimmed = trimmed.substring(0, trimmed.length() - "/v2".length());
        } else if (trimmed.endsWith("/beta")) {
            trimmed = trimmed.substring(0, trimmed.length() - "/beta".length());
        } else if (trimmed.endsWith("/api/v3")) {
            trimmed = trimmed.substring(0, trimmed.length() - "/api/v3".length()) + "/api";
        } else if (trimmed.endsWith("/api/v1")) {
            trimmed = trimmed.substring(0, trimmed.length() - "/api/v1".length()) + "/api";
        } else if (trimmed.endsWith("/compatible-mode/v1")) {
            trimmed = trimmed.substring(0, trimmed.length() - "/compatible-mode/v1".length()) + "/compatible-mode";
        }
        return trimmed;
    }

    // 豆包需要特殊的 endpoint 处理
    private static final String DOUBAO_HOST = "ark.cn-beijing.volces.com";

    private String buildFullEndpoint(String baseUrl, String resolvedProviderCode) {
        // 只有豆包需要特殊处理
        if (resolvedProviderCode != null && resolvedProviderCode.contains("doubao")) {
            return baseUrl + "/chat/completions";
        }
        return "";
    }

    private Duration effectiveChatCompletionTimeout() {
        if (timeout.compareTo(CHAT_COMPLETION_MIN_TIMEOUT) < 0) {
            return CHAT_COMPLETION_MIN_TIMEOUT;
        }
        return timeout;
    }
}
