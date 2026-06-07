package com.xiyu.bid.settings.service;

import com.xiyu.bid.ai.client.AiProviderRuntimeConfig;
import com.xiyu.bid.ai.client.OpenAiCompatibleClient;
import com.xiyu.bid.settings.dto.AiModelTestRequest;
import com.xiyu.bid.settings.dto.AiModelTestResponse;
import com.xiyu.bid.settings.dto.SettingsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AiModelConnectionTestService {

    private final SettingsService settingsService;
    private final OpenAiCompatibleClient openAiCompatibleClient;
    private final Environment environment;
    private final AiProviderCatalog aiProviderCatalog;

    public AiModelTestResponse testConnection(AiModelTestRequest request) {
        String providerCode = normalize(request == null ? null : request.getProviderCode());
        SettingsResponse.AiProviderSetting provider = findProvider(providerCode);

        String baseUrl = firstNonBlank(request == null ? null : request.getBaseUrl(), provider.getBaseUrl());
        String model = firstNonBlank(request == null ? null : request.getModel(), provider.getModel());
        String apiKey = firstNonBlank(
                request == null ? null : request.getApiKeyPlaintext(),
                settingsService.resolveAiApiKey(providerCode),
                resolveEnvironmentApiKey(providerCode)
        );

        String status = "success";
        String message = "连接测试成功";
        try {
            aiProviderCatalog.validateBaseUrl(providerCode, baseUrl);
            openAiCompatibleClient.testConnection(new AiProviderRuntimeConfig(providerCode, baseUrl, model, apiKey));
            settingsService.saveSuccessfulAiProviderTestConfig(providerCode, baseUrl, model, apiKey, message);
        } catch (RuntimeException exception) {
            status = "failed";
            message = rootMessage(exception);
            settingsService.updateAiProviderTestResult(providerCode, status, message);
        }
        return AiModelTestResponse.builder()
                .providerCode(providerCode)
                .status(status)
                .message(message)
                .testedAt(Instant.now())
                .build();
    }

    private SettingsResponse.AiProviderSetting findProvider(String providerCode) {
        return settingsService.getInternalAiModelConfig().getProviders().stream()
                .filter(provider -> providerCode.equals(normalize(provider.getProviderCode())))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported AI provider: " + providerCode));
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

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String rootMessage(Throwable throwable) {
        if (throwable.getMessage() != null && !throwable.getMessage().isBlank()) {
            return throwable.getMessage();
        }
        Throwable cursor = throwable;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        return cursor.getMessage() == null || cursor.getMessage().isBlank()
                ? "连接测试失败"
                : cursor.getMessage();
    }

    private String normalize(String value) {
        return aiProviderCatalog.normalize(value);
    }
}
