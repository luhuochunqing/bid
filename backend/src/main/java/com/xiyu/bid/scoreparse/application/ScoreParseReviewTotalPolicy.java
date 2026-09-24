package com.xiyu.bid.scoreparse.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.scoreparse.entity.ScoreParseTask;

/** Limits manual total confirmation to a single unresolved score table. */
final class ScoreParseReviewTotalPolicy {
    private static final ObjectMapper JSON = new ObjectMapper();

    private ScoreParseReviewTotalPolicy() {
    }

    static boolean isSingleUnknownTable(ScoreParseTask task) {
        if (task == null || task.getRunSummary() == null || task.getRunSummary().isBlank()) return false;
        try {
            JsonNode splits = JSON.readTree(task.getRunSummary()).path("tableSplits");
            if (!splits.isArray() || splits.size() != 1) return false;
            JsonNode split = splits.get(0);
            return "NO_DECLARED_TOTAL".equals(split.path("tableId").asText())
                    || !split.has("declaredTotal") || !split.get("declaredTotal").isNumber();
        } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
            return false;
        }
    }
}
