package com.xiyu.bid.ai.client;

public record AiProviderRuntimeConfig(
        String providerCode,
        String baseUrl,
        String model,
        String apiKey,
        String embeddingBaseUrl,
        String embeddingModel
) {
    public AiProviderRuntimeConfig(String providerCode, String baseUrl, String model, String apiKey) {
        this(providerCode, baseUrl, model, apiKey, null, null);
    }
}
