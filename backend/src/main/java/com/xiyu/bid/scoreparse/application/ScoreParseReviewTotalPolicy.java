package com.xiyu.bid.scoreparse.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.scoreparse.entity.ScoreParseTask;

/** Limits manual total confirmation to a single unresolved score table. */
final class ScoreParseReviewTotalPolicy {
    private static final ObjectMapper JSON = new ObjectMapper();

    private ScoreParseReviewTotalPolicy() {
    }

    static boolean hasUncoveredRows(ScoreParseTask task) {
        if (task == null || task.getRunSummary() == null) return false;
        try {
            JsonNode diagnostics = JSON.readTree(task.getRunSummary()).path("sourceDiagnostics");
            if (!diagnostics.isArray()) return false;
            for (JsonNode node : diagnostics)
                if (node.isTextual() && node.asText().startsWith("SOURCE_SCORE_ROW_UNCOVERED:")) return true;
        } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) { return false; }
        return false;
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

    static String adoptionStatus(ScoreParseTask task) {
        if (hasUncoveredRows(task)) return "SOURCE_ROWS_UNCOVERED";
        if (ScoreParseReviewAppService.declaredTotal(task) != null) return "DECLARED_TOTAL";
        return isSingleUnknownTable(task) ? "SINGLE_UNKNOWN_TOTAL" : "UNKNOWN_TOTAL";
    }

    static String adoptionStatus(ScoreParseTask task,
                                 java.util.List<com.xiyu.bid.scoreparse.dto.ScoreParseReviewDTO.Scheme> schemes) {
        if (hasUncoveredRows(task)) return "SOURCE_ROWS_UNCOVERED";
        if (schemes != null && schemes.size() > 1) return "SCHEME_SELECTION_REQUIRED";
        if (schemes != null && schemes.size() == 1) {
            var scheme = schemes.get(0);
            if ("VERIFIED".equals(scheme.totalEvidenceStatus()) && scheme.declaredTotal() != null)
                return "DECLARED_TOTAL";
            if ("CONFLICT".equals(scheme.totalEvidenceStatus())) return "CONFLICTING_TOTAL";
            return scheme.tableIds().size() == 1 ? "SINGLE_UNKNOWN_TOTAL" : "UNKNOWN_TOTAL";
        }
        return adoptionStatus(task);
    }
}
