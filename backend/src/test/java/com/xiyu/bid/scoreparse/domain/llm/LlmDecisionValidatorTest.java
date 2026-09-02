// Tests for LlmDecisionValidator (spec 059 Phase 3).
// LLM must never invent facts from scratch; every op must reference an
// existing factId in the pool.
package com.xiyu.bid.scoreparse.domain.llm;

import com.xiyu.bid.scoreparse.domain.ScoreEvidence;
import com.xiyu.bid.scoreparse.domain.ScoreFact;
import com.xiyu.bid.scoreparse.domain.ScoreRule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmDecisionValidatorTest {

    private static ScoreFact fact(String factId, String dimension, double weight) {
        return new ScoreFact(
                factId, "t-0001", List.of("t-0001-r0-c0"),
                dimension + " | " + weight + "分", "A", dimension,
                new BigDecimal(String.valueOf(weight)),
                new ScoreEvidence(ScoreEvidence.Kind.EXPLICIT_WEIGHT_COLUMN,
                        weight + "分", "t-0001-r0-c0", 0, 2, "column"),
                ScoreRule.MAX_SCORE, "",
                "STRUCTURED_TABLE_DETERMINISTIC", 1.0,
                ScoreFact.ValidationStatus.AUTO_APPROVED);
    }

    @Test
    void empty_pool_any_op_with_unknown_factId_is_violation() {
        ScoreFact phantom = fact("fact-missing", "幻影维度", 5.0);

        LlmDecision decision = new LlmDecision(List.of(
                new LlmFactOp.SelectFact("fact-missing", 0.9)));

        LlmDecisionValidator.ValidationResult result =
                LlmDecisionValidator.validate(List.of(), decision);

        assertFalse(result.valid(), "op referencing unknown factId must be rejected");
        assertTrue(result.violations().stream()
                .anyMatch(v -> v.contains("fact-missing") && v.contains("not in fact pool")));
    }

    @Test
    void merge_duplicates_referencing_existing_facts_is_accepted() {
        ScoreFact keep = fact("fact-keep", "财务状况", 5.0);
        ScoreFact remove1 = fact("fact-remove-1", "财务状况", 5.0);
        ScoreFact remove2 = fact("fact-remove-2", "财务状况", 5.0);

        LlmDecision decision = new LlmDecision(List.of(
                new LlmFactOp.MergeDuplicates("fact-keep", List.of("fact-remove-1", "fact-remove-2"))));

        LlmDecisionValidator.ValidationResult result =
                LlmDecisionValidator.validate(List.of(keep, remove1, remove2), decision);

        assertTrue(result.valid(), () -> "expected accepted, got violations: " + result.violations());
        assertTrue(result.violations().isEmpty());
    }

    @Test
    void mark_review_required_referencing_existing_fact_is_accepted() {
        ScoreFact target = fact("fact-1", "技术方案", 10.0);

        LlmDecision decision = new LlmDecision(List.of(
                new LlmFactOp.MarkReviewRequired("fact-1", "原文存在歧义，需人工确认")));

        LlmDecisionValidator.ValidationResult result =
                LlmDecisionValidator.validate(List.of(target), decision);

        assertTrue(result.valid(), () -> "expected accepted, got violations: " + result.violations());
        assertTrue(result.violations().isEmpty());
    }

    @Test
    void op_referencing_fact_not_in_pool_is_violation() {
        // Pool has only fact-1; any op referencing "fact-X" must be rejected.
        ScoreFact only = fact("fact-1", "财务状况", 5.0);

        LlmDecision decision = new LlmDecision(List.of(
                new LlmFactOp.SelectFact("fact-X", 0.9),
                new LlmFactOp.AddObservation("fact-X", "幻影备注"),
                new LlmFactOp.MergeDuplicates("fact-X", List.of("fact-1")),
                new LlmFactOp.MarkReviewRequired("fact-X", "no such fact")));

        LlmDecisionValidator.ValidationResult result =
                LlmDecisionValidator.validate(List.of(only), decision);

        assertFalse(result.valid(), "all four ops reference unknown fact-X; decision must be rejected");
        // 4 op-level violations expected (one per op, on the first factId reference).
        assertTrue(result.violations().size() >= 4,
                "expected at least 4 violations, got: " + result.violations());
        assertTrue(result.violations().stream()
                        .allMatch(v -> v.contains("fact-X") && v.contains("not in fact pool")),
                () -> "every violation should mention fact-X and 'not in fact pool', got: "
                        + result.violations());
    }

    @Test
    void null_pool_is_violation() {
        LlmDecision decision = new LlmDecision(List.of());
        LlmDecisionValidator.ValidationResult result =
                LlmDecisionValidator.validate(null, decision);
        assertFalse(result.valid());
        assertTrue(result.violations().stream().anyMatch(v -> v.contains("pool is null")));
    }

    @Test
    void null_decision_is_violation() {
        LlmDecisionValidator.ValidationResult result =
                LlmDecisionValidator.validate(List.of(), null);
        assertFalse(result.valid());
        assertTrue(result.violations().stream().anyMatch(v -> v.contains("decision is null")));
    }

    @Test
    void merge_duplicates_with_self_in_remove_is_violation() {
        ScoreFact keep = fact("fact-keep", "财务状况", 5.0);
        ScoreFact other = fact("fact-other", "技术方案", 5.0);

        // keepFactId == removeFactId[0]: must be rejected.
        LlmDecision decision = new LlmDecision(List.of(
                new LlmFactOp.MergeDuplicates("fact-keep", List.of("fact-keep", "fact-other"))));

        LlmDecisionValidator.ValidationResult result =
                LlmDecisionValidator.validate(List.of(keep, other), decision);

        assertFalse(result.valid(), () -> "expected violation, got: " + result.violations());
        assertTrue(result.violations().stream()
                .anyMatch(v -> v.contains("keepFactId must not appear in removeFactIds")));
    }
}
