package com.xiyu.bid.ai.client;

/**
 * Generates dense embedding vectors for text.
 * Decouples callers from provider-specific HTTP details.
 */
public interface EmbeddingClient {

    /**
     * Embed the given text into a float vector.
     *
     * @param config Provider runtime configuration (API key, endpoint, model)
     * @param text   The text to embed
     * @return embedding vector as float array
     */
    float[] embed(AiProviderRuntimeConfig config, String text);
}
