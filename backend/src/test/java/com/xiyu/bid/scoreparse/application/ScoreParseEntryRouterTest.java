package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import com.xiyu.bid.scoreparse.domain.ScoreCompletenessPolicy;
import com.xiyu.bid.scoreparse.domain.ScoreEvidence;
import com.xiyu.bid.scoreparse.domain.ScoreFact;
import com.xiyu.bid.scoreparse.domain.ScoreFact.ValidationStatus;
import com.xiyu.bid.scoreparse.domain.ScoreRule;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ScoreParseEntryRouterTest {
    @Test void emptyEvidenceAlsoFallsBack() {
        var evidence = StructuredTableResult.evidencePipeline(List.of(), List.of(), null, List.of());
        var recovered = StructuredTableResult.structureIncomplete("NO_SCORE_TABLE");
        assertThat(ScoreParseEntryRouter.extract(() -> evidence, () -> recovered)).isSameAs(recovered);
    }

    @Test void emptyCodeExtractionFallsBackExactlyOnceInOrder() {
        var calls = new ArrayList<String>();
        var recovered = StructuredTableResult.evidencePipeline(List.of(), List.of(), null, List.of());
        var result = ScoreParseEntryRouter.extract(() -> {
            calls.add("code"); return StructuredTableResult.ok(List.of());
        }, () -> { calls.add("legacy"); return recovered; });
        assertThat(calls).containsExactly("code", "legacy");
        assertThat(result).isSameAs(recovered);
    }

    @Test void failedFallbackDoesNotEraseStructureFailure() {
        var failed = StructuredTableResult.structureIncomplete("STRUCTURE_DEFECT");
        assertThat(ScoreParseEntryRouter.extract(() -> failed,
                () -> StructuredTableResult.ok(List.of()))).isSameAs(failed);
    }

    @Test
    void incompleteEvidenceWithDeficitTriggersFallback() {
        var fact = createFact("f1", new BigDecimal("48"));
        var completeness = ScoreCompletenessPolicy.evaluateCompleteness(List.of(fact), "评分总分100分。");
        var resultWithDeficit = StructuredTableResult.evidencePipeline(
                List.of(fact), List.of(), completeness, List.of());

        var recoveredCandidate = new ScoreCandidate("1", "技术+商务", "完整方案", new BigDecimal("100"), "OBJECTIVE", "", "", "", "RECOVERED");
        var recovered = StructuredTableResult.ok(List.of(recoveredCandidate));

        var outcome = ScoreParseEntryRouter.extract(() -> resultWithDeficit, () -> recovered);
        assertThat(outcome).isSameAs(recovered);
    }

    @Test
    void excessiveEvidencePreservedWithoutFallbackOverride() {
        var fact1 = createFact("f1", new BigDecimal("100"));
        var fact2 = createFact("f2", new BigDecimal("80"));
        var completeness = ScoreCompletenessPolicy.evaluateCompleteness(List.of(fact1, fact2), "评分总分100分。");
        var resultExcessive = StructuredTableResult.evidencePipeline(
                List.of(fact1, fact2), List.of(), completeness, List.of());

        var fallbackCalled = new ArrayList<Boolean>();
        var outcome = ScoreParseEntryRouter.extract(() -> resultExcessive, () -> {
            fallbackCalled.add(true);
            return StructuredTableResult.ok(List.of());
        });
        assertThat(fallbackCalled).isEmpty();
        assertThat(outcome).isSameAs(resultExcessive);
    }

    @Test
    void completeEvidenceReturnsImmediatelyWithoutFallback() {
        var fact = createFact("f1", new BigDecimal("100"));
        var completeness = ScoreCompletenessPolicy.evaluateCompleteness(List.of(fact), "评分总分100分。");
        assertThat(completeness.complete()).isTrue();
        var resultComplete = StructuredTableResult.evidencePipeline(
                List.of(fact), List.of(), completeness, List.of());

        var fallbackCalled = new ArrayList<Boolean>();
        var outcome = ScoreParseEntryRouter.extract(() -> resultComplete, () -> {
            fallbackCalled.add(true);
            return StructuredTableResult.ok(List.of());
        });
        assertThat(fallbackCalled).isEmpty();
        assertThat(outcome).isSameAs(resultComplete);
    }

    @Test
    void incompleteEvidenceRetainedWhenFallbackYieldsNoCandidates() {
        var fact = createFact("f1", new BigDecimal("48"));
        var completeness = ScoreCompletenessPolicy.evaluateCompleteness(List.of(fact), "评分总分100分。");
        var resultWithDeficit = StructuredTableResult.evidencePipeline(
                List.of(fact), List.of(), completeness, List.of());

        var emptyFallback = StructuredTableResult.ok(List.of());
        var outcome = ScoreParseEntryRouter.extract(() -> resultWithDeficit, () -> emptyFallback);
        assertThat(outcome).isSameAs(resultWithDeficit);
    }

    private static ScoreFact createFact(String id, BigDecimal weight) {
        return new ScoreFact(
                id, "tbl1", List.of("cell1"), "rule", "1", "商务", weight,
                new ScoreEvidence(ScoreEvidence.Kind.EXPLICIT_WEIGHT_COLUMN, weight.toPlainString(), "cell1", 0, weight.toPlainString().length(), "48分"),
                ScoreRule.MAX_SCORE, null, "BAIDU_OCR", 0.95, ValidationStatus.AUTO_APPROVED);
    }
}
