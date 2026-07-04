package com.xiyu.bid.ai.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Pure-core parser for OpenAI-compatible /embeddings responses.
 * Keeps JSON deserialization separate from HTTP orchestration.
 */
final class OpenAiEmbeddingResponseParser {

    private OpenAiEmbeddingResponseParser() {
    }

    static float[] parse(String responseBody, ObjectMapper objectMapper) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode data = root.path("data");
            if (!data.isArray() || data.isEmpty()) {
                throw new RuntimeException("AI embedding response missing data array");
            }
            JsonNode embedding = data.get(0).path("embedding");
            if (!embedding.isArray()) {
                throw new RuntimeException("AI embedding response missing embedding array");
            }
            float[] result = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                result[i] = embedding.get(i).floatValue();
            }
            return result;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse AI embedding response: " + e.getMessage(), e);
        }
    }
}
