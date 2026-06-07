package com.xiyu.bid.settings.service;

import com.xiyu.bid.settings.dto.SettingsResponse;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class AiProviderCatalog {

    private static final String DEFAULT_ACTIVE_PROVIDER = "deepseek";

    private final Map<String, AiProviderDefinition> providers = Map.of(
            "openai", new AiProviderDefinition(
                    "openai",
                    "OpenAI",
                    "https://api.openai.com/v1/chat/completions",
                    "gpt-4o-mini",
                    List.of("OPENAI_API_KEY"),
                    Set.of("api.openai.com")
            ),
            "deepseek", new AiProviderDefinition(
                    "deepseek",
                    "DeepSeek",
                    "https://api.deepseek.com/chat/completions",
                    "deepseek-chat",
                    List.of("DEEPSEEK_API_KEY"),
                    Set.of("api.deepseek.com")
            ),
            "qwen", new AiProviderDefinition(
                    "qwen",
                    "通义千问",
                    "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
                    "qwen-plus",
                    List.of("DASHSCOPE_API_KEY", "QWEN_API_KEY"),
                    Set.of("dashscope.aliyuncs.com")
            ),
            "doubao", new AiProviderDefinition(
                    "doubao",
                    "豆包",
                    "https://ark.cn-beijing.volces.com/api/v3/chat/completions",
                    "doubao-1-5-pro-32k-250115",
                    List.of("ARK_API_KEY", "DOUBAO_API_KEY", "VOLCENGINE_API_KEY"),
                    Set.of("ark.cn-beijing.volces.com")
            )
    );

    private final List<String> providerOrder = List.of("openai", "deepseek", "qwen", "doubao");

    public String defaultActiveProvider() {
        return DEFAULT_ACTIVE_PROVIDER;
    }

    public List<String> supportedProviderCodes() {
        return providerOrder;
    }

    public boolean isSupported(String providerCode) {
        return providers.containsKey(normalize(providerCode));
    }

    public SettingsResponse.AiProviderSetting defaultProviderSetting(String providerCode) {
        AiProviderDefinition provider = get(providerCode);
        return SettingsResponse.AiProviderSetting.builder()
                .providerCode(provider.code())
                .providerName(provider.name())
                .enabled(true)
                .baseUrl(provider.defaultBaseUrl())
                .model(provider.defaultModel())
                .build();
    }

    public List<String> environmentKeys(String providerCode) {
        return get(providerCode).environmentKeys();
    }

    public void validateBaseUrl(String providerCode, String baseUrl) {
        AiProviderDefinition provider = get(providerCode);
        URI uri;
        try {
            uri = URI.create(baseUrl == null ? "" : baseUrl.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("AI API 地址格式不正确", exception);
        }

        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(scheme) || host == null || host.isBlank()) {
            throw new IllegalArgumentException("AI API 地址必须是 HTTPS 完整地址");
        }

        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (!provider.allowedHosts().contains(normalizedHost)) {
            throw new IllegalArgumentException("AI API 地址必须匹配当前厂商的官方域名");
        }
    }

    public String normalize(String providerCode) {
        return providerCode == null ? "" : providerCode.trim().toLowerCase(Locale.ROOT);
    }

    private AiProviderDefinition get(String providerCode) {
        String normalizedCode = normalize(providerCode);
        AiProviderDefinition provider = providers.get(normalizedCode);
        if (provider == null) {
            throw new IllegalArgumentException("Unsupported AI provider: " + providerCode);
        }
        return provider;
    }

    private record AiProviderDefinition(
            String code,
            String name,
            String defaultBaseUrl,
            String defaultModel,
            List<String> environmentKeys,
            Set<String> allowedHosts
    ) {
    }
}
