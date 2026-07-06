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

    static String deriveEmbeddingUrl(String chatBaseUrl) {
        if (chatBaseUrl == null || chatBaseUrl.isBlank()) {
            return DEFAULT_EMBEDDING_BASE_URL;
        }
        if (chatBaseUrl.endsWith("/chat/completions")) {
            return chatBaseUrl.replace("/chat/completions", "/embeddings");
        }
        if (chatBaseUrl.endsWith("/v1")) {
            return chatBaseUrl + "/embeddings";
        }
        return chatBaseUrl.replaceAll("/$", "") + "/v1/embeddings";
    }

    /**
     * 默认 embedding 模型：custom 厂商用 chat 模型名，其他厂商用 text-embedding-v3。
     */
    static String resolveDefaultEmbeddingModel(AiProviderRuntimeConfig config) {
        if ("custom".equals(config.providerCode())) {
            String chatModel = config.model();
            return chatModel != null && !chatModel.isBlank() ? chatModel : DEFAULT_EMBEDDING_MODEL;
        }
        return DEFAULT_EMBEDDING_MODEL;
    }

    private AiProviderRuntimeConfig resolveConfig(AiProviderRuntimeConfig config) {
        String baseUrl = config.embeddingBaseUrl() != null && !config.embeddingBaseUrl().isBlank()
                ? config.embeddingBaseUrl()
                : deriveEmbeddingUrl(config.baseUrl());
        String model = config.embeddingModel() != null && !config.embeddingModel().isBlank()
                ? config.embeddingModel()
                : resolveDefaultEmbeddingModel(config);
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
