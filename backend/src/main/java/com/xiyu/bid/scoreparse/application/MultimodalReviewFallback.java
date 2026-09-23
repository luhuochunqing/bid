package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.MultimodalScoreFactValidator;
import com.xiyu.bid.scoreparse.domain.OriginalScoringContext;
import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import com.xiyu.bid.scoreparse.domain.ScoreCompletenessPolicy;
import com.xiyu.bid.scoreparse.domain.ScoreFact;
import com.xiyu.bid.scoreparse.domain.ScoreParseDecision;
import com.xiyu.bid.scoreparse.domain.ScoreUnitPolicy;
import com.xiyu.bid.scoreparse.domain.TableCompleteness;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** Keeps degraded multimodal outcomes review-only unless deterministic source proof is complete. */
@Slf4j
final class MultimodalReviewFallback {
    private MultimodalReviewFallback() {}

    static StructuredTableResult completeDeterministic(
            OriginalScoringContext originalContext, DocumentArtifact artifact, String tenderSectionText,
            MultimodalScoreFactValidator.ValidationReport modelValidation) {
        var shadow = ScoreFactShadowPipeline.run(artifact, tenderSectionText);
        var completeness = shadow.completeness();
        if (completeness == null || completeness.decision() != ScoreParseDecision.AUTO_APPROVED) return null;
        List<ScoreFact> facts = shadow.tables().stream().flatMap(table -> table.facts().stream()).toList();
        List<ScoreCandidate> candidates = ScoreUnitPolicy.effectiveFacts(facts).stream()
                .map(ScoreFact::toCandidate).flatMap(Optional::stream).toList();
        var original = OriginalSourcePublicationGuard.verify(originalContext, artifact, facts);
        if (candidates.isEmpty() || original.proofs().isEmpty() || !original.defects().isEmpty()) {
            log.warn("确定性回补原文核验失败，保留模型复核事实: modelFacts={}, fallbackFacts={}, problems={}",
                    modelValidation.facts().size(), facts.size(), original.defects());
            return null;
        }
        completeness = OriginalSourcePublicationGuard.verified(completeness, original);
        log.warn("多模态召回未闭合，确定性表格管道已完整回补: modelFacts={}, fallbackFacts={}, declaredTotal={}, leafTotal={}",
                modelValidation.facts().size(), facts.size(), completeness.declaredTotalScore(),
                completeness.leafWeightSum());
        return StructuredTableResult.evidencePipeline(facts, candidates, completeness,
                shadow.tableCompletenesses(), original.proofs());
    }

    static StructuredTableResult reviewOnly(DocumentArtifact artifact, OriginalScoringContext context,
                                             String text, String reason) {
        try {
            var shadow = ScoreFactShadowPipeline.run(artifact, text);
            List<ScoreFact> facts = shadow.tables().stream().flatMap(table -> table.facts().stream())
                    .map(fact -> fact.withValidationStatus(ScoreFact.ValidationStatus.REVIEW_REQUIRED)).toList();
            List<ScoreFact> leaves = ScoreUnitPolicy.effectiveFacts(facts);
            List<ScoreFact> parents = facts.stream().filter(fact -> !leaves.contains(fact)).toList();
            BigDecimal sum = leaves.stream().map(ScoreFact::weight).filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal originalTotal = context.total().value();
            var totalSource = originalTotal == null ? ScoreCompletenessPolicy.DeclaredTotalSource.UNKNOWN
                    : "TENDER_TEXT".equals(context.total().source())
                    ? ScoreCompletenessPolicy.DeclaredTotalSource.TENDER_TEXT
                    : ScoreCompletenessPolicy.DeclaredTotalSource.STRUCTURED_SUMMARY_ROW;
            var defects = new java.util.ArrayList<String>();
            defects.add(reason);
            defects.addAll(context.total().problems().stream().map(problem -> problem.code() + ": "
                    + problem.expected() + " actual=" + problem.actual() + " at " + problem.locator()).toList());
            var completeness = new ScoreCompletenessPolicy.ScoreCompletenessResult(false,
                    ScoreParseDecision.REVIEW_REQUIRED, reason, originalTotal, totalSource, sum,
                    leaves.size(), parents.size(), 0, defects, leaves, parents);
            return StructuredTableResult.evidencePipeline(facts, List.of(), completeness,
                    List.of(new TableCompleteness("MULTIMODAL_FALLBACK", false, completeness)));
        } catch (RuntimeException failure) {
            return StructuredTableResult.structureIncomplete(reason);
        }
    }
}
