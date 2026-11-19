package com.xiyu.bid.scoreparse.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.scoreparse.domain.ScoreItemTitleExtractor;
import com.xiyu.bid.scoreparse.domain.ScoreTypeClassificationPolicy;
import com.xiyu.bid.scoreparse.dto.ScoreParseReviewDTO;
import com.xiyu.bid.scoreparse.entity.ScoreParseEvidence;
import com.xiyu.bid.scoreparse.entity.ScoreParseFact;
import com.xiyu.bid.scoreparse.entity.ScoreParseTask;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/** Maps persisted parse facts and qualification evidence into review snapshots. */
@Slf4j
final class ScoreParseReviewFactMapper {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern SCORE_TYPE_IN_EXPLANATION = Pattern.compile("scoreType=(OBJECTIVE|SUBJECTIVE)");

    private ScoreParseReviewFactMapper() {}

    static String resolveScoreType(String sourceText, List<ScoreParseEvidence> evidences) {
        String scoreTypeGuess = null;
        for (ScoreParseEvidence evidence : evidences) {
            String explanation = evidence.getExplanation();
            if (explanation == null) continue;
            var matcher = SCORE_TYPE_IN_EXPLANATION.matcher(explanation);
            if (matcher.find()) {
                scoreTypeGuess = matcher.group(1);
                break;
            }
        }
        return new ScoreTypeClassificationPolicy().classify(sourceText, scoreTypeGuess);
    }

    static BigDecimal declaredTotal(ScoreParseTask task) {
        if (task != null && task.getRunSummary() != null && !task.getRunSummary().isBlank()) {
            try {
                JsonNode splits = JSON.readTree(task.getRunSummary()).path("tableSplits");
                if (splits.isArray() && !splits.isEmpty()) {
                    return splits.size() == 1 ? numericTotal(splits.get(0)) : null;
                }
            } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
                log.warn("解析任务声明总分失败，保留未知状态: taskId={}", task.getTaskId());
            }
        }
        return null;
    }

    private static BigDecimal numericTotal(JsonNode split) {
        JsonNode value = split.get("declaredTotal");
        return value != null && value.isNumber() ? value.decimalValue() : null;
    }
    static ScoreParseReviewDTO.Fact toFactDTO(ScoreParseFact fact, List<ScoreParseEvidence> evidences) {
        Qualification qualification = evidences.stream()
                .filter(evidence -> "SCHEME_QUALIFICATION".equals(evidence.getKind()))
                .map(ScoreParseReviewFactMapper::qualification).findFirst()
                .orElseGet(() -> evidences.stream()
                        .filter(evidence -> "ITEM_SCHEME_SCOPE_MISMATCH".equals(evidence.getKind()))
                        .map(ScoreParseReviewFactMapper::qualification).findFirst()
                        .orElseGet(Qualification::sourceUnverified));
        boolean scored = fact.getWeight() != null && fact.getWeight().signum() > 0
                || verifiedZeroDeduction(fact, evidences);
        String resolvedDimension = ScoreItemTitleExtractor.resolveDimension(fact.getDimension(), fact.getSourceText());
        String resolvedScoreType = resolveScoreType(fact.getSourceText(), evidences);
        return new ScoreParseReviewDTO.Fact(
                fact.getFactId(),
                fact.getCode(),
                resolvedDimension,
                fact.getWeight(),
                resolvedScoreType,
                fact.getValidationStatus(),
                evidences.stream().map(ScoreParseEvidence::getExplanation)
                        .filter(java.util.Objects::nonNull).filter(message -> !message.isBlank())
                        .distinct().collect(Collectors.joining("；")),
                fact.getSourceText(),
                fact.getSourceCellIds(),
                fact.getWeightEvidence(),
                fact.getParentFactId(),
                fact.getExtractionMethod(),
                fact.getConfidence(),
                evidences.stream()
                        .sorted(Comparator.comparing(ScoreParseEvidence::getId))
                        .map(evidence -> new ScoreParseReviewDTO.Evidence(
                                evidence.getCellId(),
                                evidence.getKind(),
                                evidence.getQuotedText(),
                                evidence.getStartOffset(),
                                evidence.getEndOffset(),
                                evidence.getExplanation()))
                        .toList(),
                com.xiyu.bid.scoreparse.domain.ChildAggregation.legacyDefault(fact.getChildAggregation()).name(),
                qualification.schemeId(), qualification.scopeStatus(), qualification.candidateEligible() && scored,
                qualification.candidateEligible() && !scored ? "SCORE_WEIGHT_NOT_CONFIRMED" : qualification.reason(),
                qualification.schemeTitle(), qualification.declaredTotal(),
                qualification.totalEvidenceStatus(), qualification.totalQuote(), qualification.totalLocator(),
                qualification.layoutStatus(), qualification.tableIds());
    }

    static List<String> sourceCellIds(ScoreParseFact fact) {
        if (fact.getSourceCellIds() == null || fact.getSourceCellIds().isBlank()) return List.of();
        try {
            var ids = JSON.readTree(fact.getSourceCellIds());
            if (!ids.isArray()) return List.of();
            return java.util.stream.StreamSupport.stream(ids.spliterator(), false)
                    .filter(JsonNode::isTextual).map(JsonNode::asText).toList();
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            return List.of();
        }
    }

    static boolean verifiedZeroDeduction(ScoreParseFact fact, List<ScoreParseEvidence> evidences) {
        if (fact.getWeight() == null || fact.getWeight().signum() != 0
                || fact.getSourceText() == null || fact.getSourceText().isBlank()) return false;
        var local = evidences.stream().filter(e -> fact.getFactId().equals(e.getFactId())).toList();
        return local.stream().filter(e -> "SCHEME_QUALIFICATION".equals(e.getKind())).map(ScoreParseReviewFactMapper::qualification)
                .anyMatch(q -> q.candidateEligible() && "IN_SCHEME".equals(q.scopeStatus()))
                && local.stream().anyMatch(e -> com.xiyu.bid.scoreparse.domain.SourceDeductionSelectionPolicy.EVIDENCE_KIND.equals(e.getKind())
                        && fact.getSourceText().equals(e.getQuotedText()));
    }

    static Qualification qualification(ScoreParseEvidence evidence) {
        if ("ITEM_SCHEME_SCOPE_MISMATCH".equals(evidence.getKind())) {
            return new Qualification(null, "OUT_OF_SCHEME", false, "ITEM_SCHEME_SCOPE_MISMATCH");
        }
        String explanation = evidence.getExplanation();
        if (explanation == null || explanation.isBlank()) return Qualification.sourceUnverified();
        try {
            JsonNode data = JSON.readTree(explanation);
            String scope = data.path("scopeStatus").asText("SOURCE_UNVERIFIED");
            boolean eligible = "IN_SCHEME".equals(scope) && data.path("candidateEligible").asBoolean(false);
            String reason = data.path("reason").isTextual() ? data.path("reason").asText() : null;
            List<String> tableIds = new java.util.ArrayList<>();
            if (data.path("tableIds").isArray()) data.path("tableIds").forEach(value -> tableIds.add(value.asText()));
            BigDecimal total = data.path("declaredTotal").isNumber() ? data.path("declaredTotal").decimalValue() : null;
            return new Qualification(data.path("schemeId").asText(null), scope, eligible,
                    reason == null || reason.isBlank() ? (eligible ? null : "SOURCE_SCHEME_NOT_VERIFIED") : reason,
                    data.path("schemeTitle").asText(""), total,
                    data.path("totalEvidenceStatus").asText("UNKNOWN"), data.path("totalQuote").asText(""),
                    data.path("totalLocator").asText(""), data.path("layoutStatus").asText("UNKNOWN"), tableIds);
        } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) {
            return Qualification.sourceUnverified();
        }
    }

    static List<ScoreParseReviewDTO.Scheme> schemes(List<ScoreParseReviewDTO.Fact> facts) {
        Map<String, List<ScoreParseReviewDTO.Fact>> grouped = facts.stream()
                .filter(fact -> fact.schemeId() != null && !fact.schemeId().isBlank())
                .collect(Collectors.groupingBy(ScoreParseReviewDTO.Fact::schemeId, java.util.LinkedHashMap::new,
                        Collectors.toList()));
        boolean hasInSchemeFacts = grouped.values().stream()
                .anyMatch(group -> group.stream().anyMatch(f -> "IN_SCHEME".equals(f.scopeStatus())));
        var activeEntries = grouped.entrySet().stream()
                .filter(entry -> !hasInSchemeFacts || entry.getValue().stream()
                        .anyMatch(f -> !"OUT_OF_SCHEME".equals(f.scopeStatus())))
                .toList();
        return activeEntries.stream().map(entry -> {
            ScoreParseReviewDTO.Fact fact = entry.getValue().get(0);
            return new ScoreParseReviewDTO.Scheme(entry.getKey(), fact.schemeTitle(), fact.tableIds(),
                    fact.layoutStatus(), fact.totalEvidenceStatus(), fact.declaredTotal(), fact.totalQuote(),
                    fact.totalLocator(), activeEntries.size() > 1);
        }).toList();
    }

    record Qualification(String schemeId, String scopeStatus, boolean candidateEligible, String reason,
                                 String schemeTitle, BigDecimal declaredTotal, String totalEvidenceStatus,
                                 String totalQuote, String totalLocator, String layoutStatus, List<String> tableIds) {
        Qualification {
            schemeTitle = schemeTitle == null ? "" : schemeTitle;
            totalEvidenceStatus = totalEvidenceStatus == null ? "UNKNOWN" : totalEvidenceStatus;
            totalQuote = totalQuote == null ? "" : totalQuote;
            totalLocator = totalLocator == null ? "" : totalLocator;
            layoutStatus = layoutStatus == null ? "UNKNOWN" : layoutStatus;
            tableIds = tableIds == null ? List.of() : List.copyOf(tableIds);
        }
        private Qualification(String schemeId, String scopeStatus, boolean candidateEligible, String reason) {
            this(schemeId, scopeStatus, candidateEligible, reason, "", null,
                    "UNKNOWN", "", "", "UNKNOWN", List.of());
        }
        private static Qualification sourceUnverified() {
            return new Qualification(null, "SOURCE_UNVERIFIED", false, "SOURCE_SCHEME_NOT_VERIFIED");
        }
    }
}
