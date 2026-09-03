// Tests for LlmDecisionValidator (spec 059 Phase 3 contract).
// LLM must never invent facts from scratch; every op must reference an
// existing factId in the pool. Ops follow the Plan contract:
// KEEP / CLASSIFY / LINK_PARENT / MARK_DUPLICATE / REVIEW_REQUIRED.
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

    private static ScoreFact fact(String factId, String tableId, String dimension, double weight) {
        return new ScoreFact(
                factId, tableId, List.of(tableId + "-r0-c0"),
                dimension + " | " + weight + "分", "A", dimension,
                new BigDecimal(String.valueOf(weight)),
                new ScoreEvidence(ScoreEvidence.Kind.EXPLICIT_WEIGHT_COLUMN,
                        weight + "分", tableId + "-r0-c0", 0, 2, "column"),
                ScoreRule.MAX_SCORE, "",
                "STRUCTURED_TABLE_DETERMINISTIC", 1.0,
                ScoreFact.ValidationStatus.AUTO_APPROVED);
    }

    private static ScoreFact fact(String factId, String dimension, double weight) {
        return fact(factId, "t-0001", dimension, weight);
    }

    @Test
    void empty_pool_any_op_with_unknown_factId_is_violation() {
        LlmDecision decision = new LlmDecision(List.of(
                new LlmFactOp.Keep("fact-missing", 0.9)));

        LlmDecisionValidator.ValidationResult result =
                LlmDecisionValidator.validate(List.of(), decision);

        assertFalse(result.valid(), "op referencing unknown factId must be rejected");
        assertTrue(result.violations().stream()
                .anyMatch(v -> v.contains("fact-missing") && v.contains("not in fact pool")));
    }

    @Test
    void keep_and_classify_referencing_existing_fact_are_accepted() {
        ScoreFact target = fact("fact-1", "技术方案", 10.0);

        LlmDecision decision = new LlmDecision(List.of(
                new LlmFactOp.Keep("fact-1", 0.95),
                new LlmFactOp.Classify("fact-1", "技术能力", 0.9)));

        LlmDecisionValidator.ValidationResult result =
                LlmDecisionValidator.validate(List.of(target), decision);

        assertTrue(result.valid(), () -> "expected accepted, got violations: " + result.violations());
    }

    @Test
    void classify_with_blank_category_is_violation() {
        ScoreFact target = fact("fact-1", "技术方案", 10.0);

        LlmDecision decision = new LlmDecision(List.of(
                new LlmFactOp.Classify("fact-1", "  ", 0.9)));

        LlmDecisionValidator.ValidationResult result =
                LlmDecisionValidator.validate(List.of(target), decision);

        assertFalse(result.valid());
        assertTrue(result.violations().stream().anyMatch(v -> v.contains("category is blank")));
    }

    @Test
    void link_parent_within_same_table_is_accepted() {
        ScoreFact child = fact("fact-child", "t-0001", "条款1", 5.0);
        ScoreFact parent = fact("fact-parent", "t-0001", "商务部分", 20.0);

        LlmDecision decision = new LlmDecision(List.of(
                new LlmFactOp.LinkParent("fact-child", "fact-parent")));

        LlmDecisionValidator.ValidationResult result =
                LlmDecisionValidator.validate(List.of(child, parent), decision);

        assertTrue(result.valid(), () -> "expected accepted, got violations: " + result.violations());
    }

    @Test
    void link_parent_with_unknown_or_self_reference_is_violation() {
        ScoreFact child = fact("fact-child", "t-0001", "条款1", 5.0);

        LlmDecision decision = new LlmDecision(List.of(
                new LlmFactOp.LinkParent("fact-child", "fact-ghost"),
                new LlmFactOp.LinkParent("fact-child", "fact-child")));

        LlmDecisionValidator.ValidationResult result =
                LlmDecisionValidator.validate(List.of(child), decision);

        assertFalse(result.valid());
        assertTrue(result.violations().stream()
                .anyMatch(v -> v.contains("parentFactId") && v.contains("not in fact pool")));
        assertTrue(result.violations().stream()
                .anyMatch(v -> v.contains("childFactId must differ from parentFactId")));
    }

    @Test
    void link_parent_across_tables_is_rejected() {
        // 跨文件/跨表 cell 引用违反证据边界（T302）。
        ScoreFact child = fact("fact-child", "t-0001", "条款1", 5.0);
        ScoreFact parent = fact("fact-parent", "t-0002", "商务部分", 20.0);

        LlmDecision decision = new LlmDecision(List.of(
                new LlmFactOp.LinkParent("fact-child", "fact-parent")));

        LlmDecisionValidator.ValidationResult result =
                LlmDecisionValidator.validate(List.of(child, parent), decision);

        assertFalse(result.valid());
        assertTrue(result.violations().stream().anyMatch(v -> v.contains("cross-table link rejected")));
    }

    @Test
    void mark_duplicate_referencing_existing_facts_is_accepted() {
        ScoreFact keep = fact("fact-keep", "财务状况", 5.0);
        ScoreFact dup1 = fact("fact-dup-1", "财务状况", 5.0);
        ScoreFact dup2 = fact("fact-dup-2", "财务状况", 5.0);

        LlmDecision decision = new LlmDecision(List.of(
                new LlmFactOp.MarkDuplicate("fact-keep", List.of("fact-dup-1", "fact-dup-2"))));

        LlmDecisionValidator.ValidationResult result =
                LlmDecisionValidator.validate(List.of(keep, dup1, dup2), decision);

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
                new LlmFactOp.Keep("fact-X", 0.9),
                new LlmFactOp.Classify("fact-X", "技术", 0.9),
                new LlmFactOp.LinkParent("fact-X", "fact-1"),
                new LlmFactOp.MarkDuplicate("fact-X", List.of("fact-1")),
                new LlmFactOp.MarkReviewRequired("fact-X", "no such fact")));

        LlmDecisionValidator.ValidationResult result =
                LlmDecisionValidator.validate(List.of(only), decision);

        assertFalse(result.valid(), "all five ops reference unknown fact-X; decision must be rejected");
        // 5 op-level violations expected (one per op, on the first factId reference).
        assertTrue(result.violations().size() >= 5,
                "expected at least 5 violations, got: " + result.violations());
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
    void mark_duplicate_with_self_in_duplicates_is_violation() {
        ScoreFact keep = fact("fact-keep", "财务状况", 5.0);
        ScoreFact other = fact("fact-other", "技术方案", 5.0);

        // keepFactId == duplicateFactIds[0]: must be rejected.
        LlmDecision decision = new LlmDecision(List.of(
                new LlmFactOp.MarkDuplicate("fact-keep", List.of("fact-keep", "fact-other"))));

        LlmDecisionValidator.ValidationResult result =
                LlmDecisionValidator.validate(List.of(keep, other), decision);

        assertFalse(result.valid(), () -> "expected violation, got: " + result.violations());
        assertTrue(result.violations().stream()
                .anyMatch(v -> v.contains("keepFactId must not appear in duplicateFactIds")));
    }
}
