// Input: deterministic facts and the same original document supplied to extraction
// Output: per-table original-source publication proofs or review diagnostics
// Pos: scoreparse/application — deterministic evidence publication gate
package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.OriginalScoreEvidencePolicy;
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

    static Check verify(DocumentArtifact artifact, String tenderText, List<ScoreFact> facts, int tableCount) {
        if (facts.isEmpty()) return new Check(List.of(), List.of("ORIGINAL_FACTS_MISSING"));
        List<String> defects = new ArrayList<>();
        List<OriginalScoreEvidencePolicy.PublicationProof> proofs = new ArrayList<>();
        for (String tableId : facts.stream().map(ScoreFact::sourceTableId).distinct().toList()) {
            List<ScoreFact> tableFacts = facts.stream().filter(f -> tableId.equals(f.sourceTableId())).toList();
            var original = OriginalScoreEvidencePolicy.verifyFacts(artifact,
                    tableCount == 1 ? tenderText : null, tableFacts);
            original.problems().forEach(p -> defects.add(p.code() + "[fact=" + p.factId()
                    + ",field=" + p.field() + ",expected=" + p.expected() + ",actual=" + p.actual()
                    + ",source=" + p.locator() + "]"));
            BigDecimal total = original.total().value();
            BigDecimal leaves = tableFacts.stream().filter(f -> ScoreFact.isLeaf(f, tableFacts))
                    .map(ScoreFact::weight).filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (total == null || leaves.compareTo(total) != 0) {
                defects.add("WEIGHT_SUM_MISMATCH[table=" + tableId + ",expected=" + total
                        + ",actual=" + leaves + ",source=" + original.total().locator() + "]");
            }
            var proof = OriginalScoreEvidencePolicy.publicationProof(original, leaves);
            if (proof != null) proofs.add(proof);
        }
        return new Check(defects.isEmpty() ? List.copyOf(proofs) : List.of(), List.copyOf(defects));
    }

    static ScoreCompletenessPolicy.ScoreCompletenessResult review(
            ScoreCompletenessPolicy.ScoreCompletenessResult current, List<String> defects) {
        if (current == null) return null;
        List<String> all = new ArrayList<>(current.defects());
        all.addAll(defects);
        boolean totalStillVerified = defects.stream().allMatch(d -> d.startsWith("WEIGHT_SUM_MISMATCH["));
        return new ScoreCompletenessPolicy.ScoreCompletenessResult(false, ScoreParseDecision.REVIEW_REQUIRED,
                defects.get(0).split("\\[")[0], totalStillVerified ? current.declaredTotalScore() : null,
                totalStillVerified ? current.declaredTotalSource() : ScoreCompletenessPolicy.DeclaredTotalSource.UNKNOWN,
                current.leafWeightSum(), current.leafCount(), current.parentCount(),
                current.missingEvidenceCount(), all, current.leafFacts(), current.parentFacts());
    }

    record Check(List<OriginalScoreEvidencePolicy.PublicationProof> proofs, List<String> defects) {}
}
