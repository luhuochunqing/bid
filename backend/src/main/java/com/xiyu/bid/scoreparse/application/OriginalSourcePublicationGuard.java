// Input: deterministic facts and the same original document supplied to extraction
// Output: scheme-level original-source publication proof or review diagnostics
// Pos: scoreparse/application — deterministic evidence publication gate
package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.OriginalScoreEvidencePolicy;
import com.xiyu.bid.scoreparse.domain.OriginalScoringContext;
import com.xiyu.bid.scoreparse.domain.ScoreCompletenessPolicy;
import com.xiyu.bid.scoreparse.domain.ScoreFact;
import com.xiyu.bid.scoreparse.domain.ScoreParseDecision;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Applies the shared original-source policy to deterministic table facts. */
final class OriginalSourcePublicationGuard {
    private OriginalSourcePublicationGuard() {}

    static Check verify(OriginalScoringContext context, DocumentArtifact artifact, List<ScoreFact> facts) {
        List<String> defects = new ArrayList<>();
        var original = OriginalScoreEvidencePolicy.verifyFacts(context, artifact, facts);
        original.problems().forEach(p -> defects.add(p.code() + "[fact=" + p.factId()
                + ",field=" + p.field() + ",expected=" + p.expected() + ",actual=" + p.actual()
                + ",source=" + p.locator() + "]"));
        BigDecimal leaves = facts.stream().filter(f -> ScoreFact.isLeaf(f, facts))
                .map(ScoreFact::weight).filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = original.total().value();
        if (total == null || leaves.compareTo(total) != 0) {
            defects.add("WEIGHT_SUM_MISMATCH[scheme=" + context.scheme() + ",expected=" + total
                    + ",actual=" + leaves + ",source=" + original.total().locator() + "]");
        }
        var candidates = facts.stream().filter(f -> ScoreFact.isLeaf(f, facts))
                .map(ScoreFact::toCandidate).flatMap(java.util.Optional::stream).toList();
        var proof = defects.isEmpty() ? OriginalScoreEvidencePolicy.publicationProof(original, leaves,
                facts, candidates) : null;
        return new Check(proof == null ? List.of() : List.of(proof), List.copyOf(defects), total, leaves,
                original.total().source(), original.total().locator());
    }

    static ScoreCompletenessPolicy.ScoreCompletenessResult review(
            ScoreCompletenessPolicy.ScoreCompletenessResult current, List<String> defects, Check check) {
        if (current == null) return null;
        List<String> all = new ArrayList<>(current.defects());
        all.addAll(defects);
        BigDecimal originalTotal = check.originalTotal();
        return new ScoreCompletenessPolicy.ScoreCompletenessResult(false, ScoreParseDecision.REVIEW_REQUIRED,
                defects.get(0).split("\\[")[0], originalTotal,
                originalTotal == null ? ScoreCompletenessPolicy.DeclaredTotalSource.UNKNOWN
                        : "TENDER_TEXT".equals(check.source())
                        ? ScoreCompletenessPolicy.DeclaredTotalSource.TENDER_TEXT
                        : ScoreCompletenessPolicy.DeclaredTotalSource.STRUCTURED_SUMMARY_ROW,
                check.leafTotal(), current.leafCount(), current.parentCount(),
                current.missingEvidenceCount(), all, current.leafFacts(), current.parentFacts());
    }

    static ScoreCompletenessPolicy.ScoreCompletenessResult verified(
            ScoreCompletenessPolicy.ScoreCompletenessResult current, Check check) {
        if (current == null || check.proofs().size() != 1 || !check.defects().isEmpty()) return current;
        return new ScoreCompletenessPolicy.ScoreCompletenessResult(true, ScoreParseDecision.AUTO_APPROVED,
                null, check.originalTotal(), "TENDER_TEXT".equals(check.source())
                ? ScoreCompletenessPolicy.DeclaredTotalSource.TENDER_TEXT
                : ScoreCompletenessPolicy.DeclaredTotalSource.STRUCTURED_SUMMARY_ROW,
                check.leafTotal(), current.leafCount(), current.parentCount(),
                current.missingEvidenceCount(), current.defects(), current.leafFacts(), current.parentFacts());
    }

    record Check(List<OriginalScoreEvidencePolicy.PublicationProof> proofs, List<String> defects,
                 BigDecimal originalTotal, BigDecimal leafTotal, String source, String locator) {}
}
