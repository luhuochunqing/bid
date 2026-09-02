// Input: Deterministic score parsing result
// Output: Immutable auditable ScoreFact
// Pos: scoreparse/domain — pure core (spec 059 Phase 2)
package com.xiyu.bid.scoreparse.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** A score fact whose value can be traced back to source cells and text. */
public record ScoreFact(
        String factId,
        String sourceTableId,
        List<String> sourceCellIds,
        String sourceText,
        String code,
        String dimension,
        BigDecimal weight,
        ScoreEvidence weightEvidence,
        ScoreRule scoreRule,
        String parentFactId,
        String extractionMethod,
        double confidence,
        ValidationStatus validationStatus
) {
    public ScoreFact {
        factId = factId == null ? "" : factId;
        sourceTableId = sourceTableId == null ? "" : sourceTableId;
        sourceCellIds = sourceCellIds == null ? List.of() : List.copyOf(sourceCellIds);
        sourceText = sourceText == null ? "" : sourceText;
        code = code == null ? "" : code;
        dimension = dimension == null ? "" : dimension;
        extractionMethod = extractionMethod == null ? "" : extractionMethod;
        scoreRule = scoreRule == null ? ScoreRule.MAX_SCORE : scoreRule;
        validationStatus = validationStatus == null ? ValidationStatus.REVIEW_REQUIRED : validationStatus;
    }

    public boolean isLeaf() {
        return parentFactId == null || parentFactId.isBlank();
    }

    public boolean isAutoApprovable() {
        return validationStatus == ValidationStatus.AUTO_APPROVED;
    }

    /** Returns a copy of this fact with {@code parentFactId} replaced (structural linkage). */
    public ScoreFact withParentFactId(String newParentFactId) {
        return new ScoreFact(factId, sourceTableId, sourceCellIds, sourceText, code, dimension,
                weight, weightEvidence, scoreRule, newParentFactId, extractionMethod,
                confidence, validationStatus);
    }

    public Optional<ScoreCandidate> toCandidate() {
        if (!isAutoApprovable()) {
            return Optional.empty();
        }
        return Optional.of(new ScoreCandidate(
                code, dimension, sourceText, weight, null, null,
                sourceText, extractionMethod, "EVIDENCE_PIPELINE"));
    }

    public enum ValidationStatus {
        AUTO_APPROVED,
        REVIEW_REQUIRED,
        STRUCTURE_INCOMPLETE
    }
}
