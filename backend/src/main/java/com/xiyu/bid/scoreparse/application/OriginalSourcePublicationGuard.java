// Input: deterministic facts and the same original document supplied to extraction
// Output: scheme-level original-source publication proof or review diagnostics
// Pos: scoreparse/application — deterministic evidence publication gate
package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.OriginalScoreEvidencePolicy;
import com.xiyu.bid.scoreparse.domain.OriginalScoringContext;
import com.xiyu.bid.scoreparse.domain.ScoreCompletenessPolicy;
import com.xiyu.bid.scoreparse.domain.ScoreFact;
import com.xiyu.bid.scoreparse.domain.ScoreParseDecision;
import com.xiyu.bid.scoreparse.domain.ScoreUnitPolicy;
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
        java.util.Set<String> eligibleIds = original.qualifications().stream()
                .filter(OriginalScoreEvidencePolicy.Qualification::candidateEligible)
                .map(OriginalScoreEvidencePolicy.Qualification::factId)
                .collect(java.util.stream.Collectors.toSet());
        List<ScoreFact> eligibleFacts = facts.stream().filter(fact -> eligibleIds.contains(fact.factId())).toList();
        validateChildRelationships(artifact, eligibleFacts, defects);
        var unitIds = ScoreUnitPolicy.effectiveUnitIds(eligibleFacts.stream().map(f -> new ScoreUnitPolicy.Node(
                f.factId(), f.parentFactId(), f.weight(), f.childAggregation().name())).toList());
        BigDecimal leaves = eligibleFacts.stream().filter(f -> unitIds.contains(f.factId()))
                .map(ScoreFact::weight).filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = original.total().value();
        BigDecimal comparable = ScoreUnitPolicy.comparableTotal(eligibleFacts.stream().map(f -> new ScoreUnitPolicy.Node(
                f.factId(), f.parentFactId(), f.weight(), f.childAggregation().name())).toList(),
                original.total().source());
        if (total == null || comparable.compareTo(total) != 0) {
            defects.add("WEIGHT_SUM_MISMATCH[scheme=" + context.scheme() + ",expected=" + total
                    + ",actual=" + comparable + ",source=" + original.total().locator() + "]");
        }
        var candidates = eligibleFacts.stream().filter(f -> unitIds.contains(f.factId()))
                .map(ScoreFact::toCandidate).flatMap(java.util.Optional::stream).toList();
        var proof = defects.isEmpty() ? OriginalScoreEvidencePolicy.publicationProof(original, leaves,
                facts, candidates) : null;
        return new Check(proof == null ? List.of() : List.of(proof), List.copyOf(defects), total, leaves,
                original.total().source(), original.total().locator(), original.qualifications());
    }

    private static void validateChildRelationships(DocumentArtifact artifact, List<ScoreFact> facts,
                                                   List<String> defects) {
        for (ScoreFact parent : facts) {
            List<ScoreFact> children = facts.stream()
                    .filter(fact -> parent.factId().equals(fact.parentFactId())).toList();
            if (children.isEmpty()) continue;
            var citedIds = new java.util.HashSet<>(parent.sourceCellIds());
            children.forEach(child -> citedIds.addAll(child.sourceCellIds()));
            String evidence = artifact == null ? "" : artifact.tables().stream()
                    .flatMap(table -> table.cells().stream())
                    .filter(cell -> citedIds.contains(cell.cellId()))
                    .map(cell -> cell.rawText() == null ? "" : cell.rawText())
                    .collect(java.util.stream.Collectors.joining(" "));
            if (!parent.childAggregation().supportedByOriginalText(evidence)) {
                defects.add("CHILD_AGGREGATION_UNSUPPORTED_BY_SOURCE[fact=" + parent.factId()
                        + ",relationship=" + parent.childAggregation() + "]");
            }
        }
    }

    static ScoreCompletenessPolicy.ScoreCompletenessResult review(
            ScoreCompletenessPolicy.ScoreCompletenessResult current, List<String> defects, Check check) {
        if (current == null) return null;
        List<String> all = new ArrayList<>(current.defects());
        all.addAll(defects);
        if (current.decision() == ScoreParseDecision.REVIEW_REQUIRED && check.originalTotal() == null) {
            return new ScoreCompletenessPolicy.ScoreCompletenessResult(false, ScoreParseDecision.REVIEW_REQUIRED,
                    current.fallbackReason(), current.declaredTotalScore(), current.declaredTotalSource(),
                    check.leafTotal(), current.leafCount(), current.parentCount(),
                    current.missingEvidenceCount(), all, current.leafFacts(), current.parentFacts());
        }
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
                 BigDecimal originalTotal, BigDecimal leafTotal, String source, String locator,
                 List<OriginalScoreEvidencePolicy.Qualification> qualifications) {}
}
