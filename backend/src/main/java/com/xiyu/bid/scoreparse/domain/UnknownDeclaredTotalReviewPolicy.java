package com.xiyu.bid.scoreparse.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Builds a review-only completeness result when no table-scoped total is known. */
public final class UnknownDeclaredTotalReviewPolicy {
    private UnknownDeclaredTotalReviewPolicy() {
    }

    // REUSE-OK: backend/src/main/java/com/xiyu/bid/ai/core/ProjectScorePreviewPolicy.java — 本策略返回 scoreparse 专用完整性结果与叶子/父项证据，不复用 AI 预览策略的不同返回契约。
    // REUSE-OK: backend/src/main/java/com/xiyu/bid/ai/core/TenderAiAnalysisPolicy.java — 同名 evaluate 仅为静态门禁命名重合，输入输出与评分完整性语义均不同。
    // REUSE-OK: backend/src/main/java/com/xiyu/bid/biddraftagent/domain/ManualConfirmationPolicy.java — 本策略不是人工采纳策略，专门生成未知总分的 scoreparse 复核结果。
    public static ScoreCompletenessPolicy.ScoreCompletenessResult evaluateUnknownDeclaredTotal(
            List<ScoreFact> facts, String reason) {
        return evaluateUnknownDeclaredTotal(facts, reason, List.of());
    }

    public static ScoreCompletenessPolicy.ScoreCompletenessResult evaluateUnknownDeclaredTotal(
            List<ScoreFact> facts, String reason, List<String> additionalDefects) {
        List<ScoreFact> safeFacts = facts == null ? List.of() : facts.stream()
                .filter(java.util.Objects::nonNull).toList();
        Set<String> parentIds = ScoreFact.parentIds(safeFacts);
        List<ScoreFact> leaves = safeFacts.stream().filter(fact -> !parentIds.contains(fact.factId())).toList();
        List<ScoreFact> parents = safeFacts.stream().filter(fact -> parentIds.contains(fact.factId())).toList();
        BigDecimal leafSum = leaves.stream().map(ScoreFact::weight).filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        List<String> defects = new ArrayList<>();
        defects.add(reason == null || reason.isBlank() ? "DECLARED_TOTAL_UNKNOWN" : reason);
        if (additionalDefects != null) {
            defects.addAll(additionalDefects);
        }
        return new ScoreCompletenessPolicy.ScoreCompletenessResult(false, ScoreParseDecision.REVIEW_REQUIRED,
                "DECLARED_TOTAL_UNKNOWN", null, ScoreCompletenessPolicy.DeclaredTotalSource.UNKNOWN,
                leafSum, leaves.size(), parents.size(), 0, defects, leaves, parents);
    }
}
