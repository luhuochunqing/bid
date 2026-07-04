package com.xiyu.bid.ai.client;

import org.springframework.stereotype.Component;

@Component
public class NoopEmbeddingClient implements EmbeddingClient {

    public static final int DEFAULT_DIMENSION = 1024;

    @Override
    public float[] embed(AiProviderRuntimeConfig config, String text) {
        return new float[DEFAULT_DIMENSION];
    }
}
