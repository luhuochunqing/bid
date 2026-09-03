// Input: ScoreFact collection and document structure metadata
// Output: immutable validation findings for fact and scoring completeness
// Pos: scoreparse/domain - pure core (spec 059 Phase 2)
package com.xiyu.bid.scoreparse.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Validates evidence and separates fact validity from scoring completeness. */
public final class ScoreFactValidator {
    private ScoreFactValidator() {
    }

    public static ValidationResult validateFacts(List<ScoreFact> facts) {
        List<String> violations = new ArrayList<>();
        if (facts == null) {
            return new ValidationResult(false, false, List.of("facts is null"));
        }
        for (ScoreFact fact : facts) {
            if (fact == null) {
                violations.add("null fact");
                continue;
            }
            if (fact.factId().isBlank()) {
                violations.add("factId is blank");
            }
            if (fact.sourceTableId().isBlank()) {
                violations.add(fact.factId() + ": sourceTableId is blank");
            }
            if (fact.sourceText().isBlank()) {
                violations.add(fact.factId() + ": sourceText is blank");
            }
            if (fact.sourceCellIds().isEmpty()) {
                violations.add(fact.factId() + ": sourceCellIds is empty");
            }
            if (fact.weight() == null || fact.weight().compareTo(BigDecimal.ZERO) <= 0) {
                violations.add(fact.factId() + ": weight must be positive");
            }
            if (fact.weightEvidence() == null || !fact.weightEvidence().isLocatable()) {
                violations.add(fact.factId() + ": weight evidence is not locatable");
            }
            if (fact.confidence() < 0 || fact.confidence() > 1) {
                violations.add(fact.factId() + ": confidence outside [0,1]");
            }
        }
        return new ValidationResult(violations.isEmpty(), false, violations);
    }

    /**
     * Validates the set used for automatic scoring. Parent facts (referenced
     * as {@code parentFactId} by any fact in the set) are retained in the
     * snapshot but must not be counted alongside their leaves.
     */
    public static ValidationResult validateScoringSet(List<ScoreFact> facts) {
        ValidationResult factResult = validateFacts(facts);
        List<String> violations = new ArrayList<>(factResult.violations());
        if (facts == null) {
            return new ValidationResult(false, false, violations);
        }
        Set<String> parentIds = ScoreFact.parentIds(facts);
        for (ScoreFact fact : facts) {
            if (fact == null || !fact.isAutoApprovable()) {
                continue;
            }
            if (parentIds.contains(fact.factId())) {
                violations.add(fact.factId() + ": parent fact cannot be auto-scored");
            }
        }
        boolean complete = violations.isEmpty()
                && facts.stream().filter(f -> f != null && f.isAutoApprovable()).count() > 0;
        return new ValidationResult(violations.isEmpty(), complete, violations);
    }

    public record ValidationResult(boolean factsValid, boolean scoringComplete, List<String> violations) {
        public ValidationResult {
            violations = violations == null ? List.of() : List.copyOf(violations);
        }
    }
}
