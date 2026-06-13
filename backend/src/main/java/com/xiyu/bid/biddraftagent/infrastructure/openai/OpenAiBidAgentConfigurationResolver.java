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

    private static final String DEEPSEEK_PROVIDER = "deepseek";
    private static final String DEEPSEEK_DEFAULT_BASE_URL = "https://api.deepseek.com/chat/completions";
    private static final String DEEPSEEK_DEFAULT_MODEL = "deepseek-chat";
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
        return deepSeekProviderRequest(requestTimeout)
                .or(() -> deepSeekDefaultRequest(requestTimeout))
                .orElseThrow(() -> missingDeepSeekConfiguration(useCase));
    }

    OpenAiBidAgentRequestConfig resolveTenderIntake() {
        return deepSeekProviderRequest()
                .or(this::deepSeekDefaultRequest)
                .orElseThrow(() -> new IllegalStateException(
                        "DeepSeek API key must be configured for "
                                + TENDER_INTAKE_USE_CASE
                                + "; set DEEPSEEK_API_KEY or the DeepSeek provider key in system settings"
                ));
    }

    boolean hasTenderIntakeConfiguration() {
        return deepSeekProviderRequest()
                .or(this::deepSeekDefaultRequest)
                .isPresent();
    }

    private Optional<OpenAiBidAgentRequestConfig> deepSeekProviderRequest() {
        return deepSeekProviderRequest(tenderIntakeTimeout);
    }

    private Optional<OpenAiBidAgentRequestConfig> deepSeekProviderRequest(Duration requestTimeout) {
        SettingsResponse.AiModelConfig aiModelConfig = aiConfigService.getInternalAiModelConfig();
        if (aiModelConfig == null) {
            return Optional.empty();
        }

        return findProvider(aiModelConfig, DEEPSEEK_PROVIDER)
                .flatMap(provider -> deepSeekApiKey()
                        .map(apiKey -> deepSeekRequestConfig(
                                apiKey,
                                firstNonBlank(provider.getBaseUrl(), DEEPSEEK_DEFAULT_BASE_URL),
                                firstNonBlank(provider.getModel(), DEEPSEEK_DEFAULT_MODEL),
                                requestTimeout
                        )));
    }

    private Optional<OpenAiBidAgentRequestConfig> deepSeekDefaultRequest() {
        return deepSeekDefaultRequest(tenderIntakeTimeout);
    }

    private Optional<OpenAiBidAgentRequestConfig> deepSeekDefaultRequest(Duration requestTimeout) {
        return deepSeekApiKey()
                .map(apiKey -> deepSeekRequestConfig(
                        apiKey,
                        DEEPSEEK_DEFAULT_BASE_URL,
                        DEEPSEEK_DEFAULT_MODEL,
                        requestTimeout
                ));
    }

    private OpenAiBidAgentRequestConfig deepSeekRequestConfig(
            String apiKey,
            String rawBaseUrl,
            String rawModel,
            Duration requestTimeout
    ) {
        return new OpenAiBidAgentRequestConfig(
                apiKey,
                normalizedBaseUrl(rawBaseUrl),
                firstNonBlank(rawModel, DEEPSEEK_DEFAULT_MODEL),
                requestTimeout,
                OpenAiBidAgentApiStyle.CHAT_COMPLETIONS
        );
    }

    private IllegalStateException missingDeepSeekConfiguration(String useCase) {
        return new IllegalStateException(
                "DeepSeek API key must be configured for "
                        + useCase
                        + "; set DEEPSEEK_API_KEY or the DeepSeek provider key in system settings"
        );
    }

    private Optional<String> deepSeekApiKey() {
        return usableValue(aiConfigService.resolveAiApiKey(DEEPSEEK_PROVIDER))
                .or(() -> providerEnvironmentApiKey(DEEPSEEK_PROVIDER));
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
        String trimmed = candidate == null ? DEEPSEEK_DEFAULT_BASE_URL : candidate.trim();
        if (trimmed.endsWith("/chat/completions")) {
            return trimmed.substring(0, trimmed.length() - "/chat/completions".length());
        }
        if (trimmed.endsWith("/responses")) {
            return trimmed.substring(0, trimmed.length() - "/responses".length());
        }
        return trimmed;
    }

    private Duration effectiveChatCompletionTimeout() {
        if (timeout.compareTo(CHAT_COMPLETION_MIN_TIMEOUT) < 0) {
            return CHAT_COMPLETION_MIN_TIMEOUT;
        }
        return timeout;
    }
}
