package com.xiyu.bid.ai.client;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class QwenEmbeddingClient implements EmbeddingClient {

    public static final String DEFAULT_EMBEDDING_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings";
    public static final String DEFAULT_EMBEDDING_MODEL = "text-embedding-v3";
    public static final int DEFAULT_DIMENSION = 1024;

    private final OpenAiCompatibleEmbeddingClient openAiCompatibleEmbeddingClient;

    @Override
    public float[] embed(AiProviderRuntimeConfig config, String text) {
        AiProviderRuntimeConfig resolved = resolveConfig(config);
        return openAiCompatibleEmbeddingClient.embed(resolved, text);
    }

    private AiProviderRuntimeConfig resolveConfig(AiProviderRuntimeConfig config) {
        String baseUrl = config.embeddingBaseUrl() != null && !config.embeddingBaseUrl().isBlank()
                ? config.embeddingBaseUrl()
                : DEFAULT_EMBEDDING_BASE_URL;
        String model = config.embeddingModel() != null && !config.embeddingModel().isBlank()
                ? config.embeddingModel()
                : DEFAULT_EMBEDDING_MODEL;
        return new AiProviderRuntimeConfig(
                config.providerCode(),
                config.baseUrl(),
                config.model(),
                config.apiKey(),
                baseUrl,
                model
        );
    }
}
