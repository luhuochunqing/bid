package com.xiyu.bid.ai.client;

public record AiProviderRuntimeConfig(
        String providerCode,
        String baseUrl,
        String model,
        String apiKey
) {
}
