// Input: ScoreCompletenessResult + original facts + tenderSectionText
// Output: immutable review snapshot for audit and front-end drawer (spec 059 Phase 4)
// Pos: scoreparse/domain — pure core, zero framework dependencies
package com.xiyu.bid.scoreparse.domain;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 评分解析复核工作流数据快照（spec 059 Phase 4）。
 * <p>用于在任务决策为 {@code REVIEW_REQUIRED} 或 {@code STRUCTURE_INCOMPLETE} 时，
 * 完整保存事实快照、校验失败项、原始总分与待确认差异，供前端抽屉展开原文证据
 * 并允许用户选择“保留原始权重”、“人工调整”或“排除非评分项”。
 */
public record ScoreParseReviewSnapshot(
        String taskId,
        ScoreParseDecision decisionStatus,
        String fallbackReason,
        BigDecimal declaredTotalScore,
        ScoreCompletenessPolicy.DeclaredTotalSource declaredTotalSource,
        BigDecimal leafWeightSum,
        BigDecimal discrepancy,
        int leafCount,
        int parentCount,
        int missingEvidenceCount,
        List<String> defects,
        List<FactView> facts
) {
    public ScoreParseReviewSnapshot {
        defects = defects == null ? List.of() : List.copyOf(defects);
        facts = facts == null ? List.of() : List.copyOf(facts);
    }

    public static ScoreParseReviewSnapshot from(
            String taskId, ScoreCompletenessPolicy.ScoreCompletenessResult completeness, List<ScoreFact> allFacts) {
        List<FactView> factViews = allFacts == null ? List.of() : allFacts.stream()
                .filter(f -> f != null)
                .map(FactView::from)
                .toList();
        BigDecimal diff = completeness.declaredTotalScore().subtract(completeness.leafWeightSum()).abs();
        return new ScoreParseReviewSnapshot(
                taskId, completeness.decision(), completeness.fallbackReason(),
                completeness.declaredTotalScore(), completeness.declaredTotalSource(),
                completeness.leafWeightSum(), diff,
                completeness.leafCount(), completeness.parentCount(), completeness.missingEvidenceCount(),
                completeness.defects(), factViews);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("taskId", taskId);
        map.put("decisionStatus", decisionStatus == null ? null : decisionStatus.name());
        map.put("fallbackReason", fallbackReason);
        map.put("declaredTotalScore", declaredTotalScore);
        map.put("declaredTotalSource", declaredTotalSource == null ? null : declaredTotalSource.name());
        map.put("leafWeightSum", leafWeightSum);
        map.put("discrepancy", discrepancy);
        map.put("leafCount", leafCount);
        map.put("parentCount", parentCount);
        map.put("missingEvidenceCount", missingEvidenceCount);
        map.put("defects", defects);
        map.put("facts", facts.stream().map(FactView::toMap).toList());
        return Collections.unmodifiableMap(map);
    }

    public record FactView(
            String factId,
            String code,
            String dimension,
            BigDecimal weight,
            String parentFactId,
            boolean isLeaf,
            String sourceText,
            List<String> sourceCellIds,
            String weightEvidenceKind,
            String weightEvidenceQuote,
            String weightEvidenceCellId,
            boolean locatable
    ) {
        public static FactView from(ScoreFact f) {
            String kind = f.weightEvidence() == null ? "NONE" : f.weightEvidence().kind().name();
            String quote = f.weightEvidence() == null ? "" : f.weightEvidence().quotedText();
            String cellId = f.weightEvidence() == null ? "" : f.weightEvidence().cellId();
            boolean locatable = f.weightEvidence() != null && f.weightEvidence().isLocatable();
            return new FactView(
                    f.factId(), f.code(), f.dimension(), f.weight(), f.parentFactId(),
                    f.parentFactId() == null || f.parentFactId().isBlank(),
                    f.sourceText(), f.sourceCellIds(),
                    kind, quote, cellId, locatable);
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("factId", factId);
            map.put("code", code);
            map.put("dimension", dimension);
            map.put("weight", weight);
            map.put("parentFactId", parentFactId);
            map.put("isLeaf", isLeaf);
            map.put("sourceText", sourceText);
            map.put("sourceCellIds", sourceCellIds);
            map.put("weightEvidenceKind", weightEvidenceKind);
            map.put("weightEvidenceQuote", weightEvidenceQuote);
            map.put("weightEvidenceCellId", weightEvidenceCellId);
            map.put("locatable", locatable);
            return Collections.unmodifiableMap(map);
        }
    }
}
