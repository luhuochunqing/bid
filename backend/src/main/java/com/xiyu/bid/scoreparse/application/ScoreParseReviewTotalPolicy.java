package com.xiyu.bid.scoreparse.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.scoreparse.entity.ScoreParseTask;

/** Limits manual total confirmation to a single unresolved score table. */
final class ScoreParseReviewTotalPolicy {
    private static final ObjectMapper JSON = new ObjectMapper();

    private ScoreParseReviewTotalPolicy() {
    }

    static void requirePublishable(ScoreParseTask task, String schemeId,
                                   java.util.Collection<ScoreParseReviewFactMapper.Qualification> qualifications,
                                   java.math.BigDecimal selectedTotal) {
        var selected = qualifications.stream().filter(q -> schemeId.equals(q.schemeId())).toList();
        String issue = task.getFallbackReason();
        if (task.getRunSummary() != null && !task.getRunSummary().isBlank()) {
            try {
                issue = JSON.readTree(task.getRunSummary()).path("fallbackReason").asText(issue);
            } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
                // Keep the task-level diagnosis for historical summaries.
            }
        }
        if ("ORIGINAL_TOTAL_CONFLICT".equals(issue)
                || selected.stream().anyMatch(q -> "CONFLICT".equals(q.totalEvidenceStatus()))) {
            throw new IllegalArgumentException("所选评分方案的原文总分存在冲突，请核验后重新解析");
        }
        if ("ORIGINAL_SCHEME_AMBIGUOUS".equals(issue) && selected.stream()
                .noneMatch(q -> "VERIFIED".equals(q.totalEvidenceStatus()))) {
            throw new IllegalArgumentException("评分方案尚未核实，请核对标书后重新解析");
        }
        if (hasUncoveredRows(task)) {
            throw new IllegalArgumentException("原文评分表仍有未识别行，请重新解析后再采纳");
        }
        var declared = selected.stream().filter(q -> "VERIFIED".equals(q.totalEvidenceStatus()))
                .map(ScoreParseReviewFactMapper.Qualification::declaredTotal).filter(java.util.Objects::nonNull)
                .min(java.math.BigDecimal::compareTo).orElseGet(() -> ScoreParseReviewFactMapper.declaredTotal(task));
        if (declared != null && selectedTotal.compareTo(declared) > 0) {
            throw new IllegalArgumentException("候选评分项合计不能超过原文总分 " + declared.toPlainString() + " 分");
        }
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
