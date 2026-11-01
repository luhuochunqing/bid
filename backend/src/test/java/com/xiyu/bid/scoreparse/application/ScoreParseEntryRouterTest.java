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

    @Test
    void inferiorFallbackCannotOverrideStructuredEvidenceAndPreservesDiagnostics() {
        // 主结果有 48 分事实与漏项诊断；fallback 仅提取出 20 分
        // 契约：严禁由 20 分覆盖 48 分导致倒退，必须坚决保留 48 分原结果与原始诊断
        var fact = createFact("f1", new BigDecimal("48"));
        var completeness = ScoreCompletenessPolicy.evaluateCompleteness(List.of(fact), "评分总分100分。");
        var resultWithDeficit = new StructuredTableResult(
                List.of(), false, null, true, List.of(fact), completeness, List.of(), List.of(), null, List.of(),
                List.of("SOURCE_SCORE_ROW_UNCOVERED:p39-t1"));

        var inferiorCandidate = new ScoreCandidate("1", "商务", "部分细则", new BigDecimal("20"), "OBJECTIVE", "", "", "", "RECOVERED");
        var inferiorFallback = StructuredTableResult.ok(List.of(inferiorCandidate));

        var outcome = ScoreParseEntryRouter.extract(() -> resultWithDeficit, () -> inferiorFallback);

        // 断言：保持主结果，不被 20 分 fallback 覆盖
        assertThat(outcome).isSameAs(resultWithDeficit);
        assertThat(outcome.facts()).hasSize(1);
        assertThat(outcome.facts().getFirst().weight()).isEqualByComparingTo("48");
        assertThat(outcome.sourceDiagnostics()).containsExactly("SOURCE_SCORE_ROW_UNCOVERED:p39-t1");
    }

    @Test
    void superiorFallbackAdoptsHigherScoreAndMergesOriginalDiagnostics() {
        // 主结果有 48 分事实与诊断；fallback 成功提取出完整 100 分
        // 契约：采纳 100 分 fallback，同时完整保留合并原有的漏项诊断
        var fact = createFact("f1", new BigDecimal("48"));
        var completeness = ScoreCompletenessPolicy.evaluateCompleteness(List.of(fact), "评分总分100分。");
        var resultWithDeficit = new StructuredTableResult(
                List.of(), false, null, true, List.of(fact), completeness, List.of(), List.of(), null, List.of(),
                List.of("SOURCE_SCORE_ROW_UNCOVERED:p39-t1"));

        var superiorCandidate = new ScoreCandidate("1", "商务", "全量细则", new BigDecimal("100"), "OBJECTIVE", "", "", "", "RECOVERED");
        var superiorFallback = new StructuredTableResult(
                List.of(superiorCandidate), false, null, false, List.of(), null, List.of(), List.of(), null, List.of(),
                List.of("MODEL_FALLBACK_APPLIED"));

        var outcome = ScoreParseEntryRouter.extract(() -> resultWithDeficit, () -> superiorFallback);

        // 断言：采纳 100 分，且诊断包含原漏项诊断与 fallback 诊断
        assertThat(outcome.candidates()).hasSize(1);
        assertThat(outcome.candidates().getFirst().weight()).isEqualByComparingTo("100");
        assertThat(outcome.sourceDiagnostics()).contains("SOURCE_SCORE_ROW_UNCOVERED:p39-t1", "MODEL_FALLBACK_APPLIED");
    }

    @Test
    void equalScoreFallbackPreservesPrimaryStructuredEvidence() {
        // 主结果 48 分；fallback 也只有 48 分
        // 契约：分值未证明更优时，权威结构化表格事实优先，不被覆盖
        var fact = createFact("f1", new BigDecimal("48"));
        var completeness = ScoreCompletenessPolicy.evaluateCompleteness(List.of(fact), "评分总分100分。");
        var resultWithDeficit = StructuredTableResult.evidencePipeline(
                List.of(fact), List.of(), completeness, List.of());

        var equalCandidate = new ScoreCandidate("1", "商务", "相同分值细则", new BigDecimal("48"), "OBJECTIVE", "", "", "", "RECOVERED");
        var equalFallback = StructuredTableResult.ok(List.of(equalCandidate));

        var outcome = ScoreParseEntryRouter.extract(() -> resultWithDeficit, () -> equalFallback);
        assertThat(outcome).isSameAs(resultWithDeficit);
    }

    @Test
    void partialDeclaredTotalFallbackCannotOverrideTenderDeclaredTotal() {
        // 主结果是 48/100 分；fallback 抽取出 20/20 分（且在其局部总分下 complete=true）
        // 契约：fallback 的局部方案与全案目标总分（100分）冲突，绝不允许以 20 分覆盖 48 分导致倒退；
        // 且必须在诊断中记录总分冲突证据以保留双方事实。
        var fact48 = createFact("f1", new BigDecimal("48"));
        var completeness100 = ScoreCompletenessPolicy.evaluateCompleteness(List.of(fact48), "评分总分100分。");
        var resultWithDeficit = StructuredTableResult.evidencePipeline(
                List.of(fact48), List.of(), completeness100, List.of());

        var fact20 = createFact("f2", new BigDecimal("20"));
        var completeness20 = ScoreCompletenessPolicy.evaluateCompleteness(List.of(fact20), "评分总分20分。");
        assertThat(completeness20.complete()).isTrue();
        var partialFallback = StructuredTableResult.evidencePipeline(
                List.of(fact20), List.of(), completeness20, List.of());

        var outcome = ScoreParseEntryRouter.extract(() -> resultWithDeficit, () -> partialFallback);

        // 断言：严禁采纳 20 分局部 fallback，坚决保留 48/100 分主结果
        assertThat(outcome.facts()).hasSize(1);
        assertThat(outcome.facts().getFirst().weight()).isEqualByComparingTo("48");
        assertThat(outcome.completeness().declaredTotalScore()).isEqualByComparingTo("100");
        assertThat(outcome.sourceDiagnostics()).anyMatch(d -> d.contains("FALLBACK_SCHEME_CONFLICT"));
    }

    @Test
    void higherDeclaredTotalFallbackCannotOverrideTenderDeclaredTotalAndRecordsConflict() {
        // 主结果是 48/100 分；fallback 抽取出 80/120 分（declared=120 大于目标 100）
        // 契约：总分冲突双向严守（既挡小于也挡大于），严禁采纳 120 分方案结果覆盖 100 分主结果，
        // 且必须在诊断中记录总分冲突证据 FALLBACK_SCHEME_CONFLICT:declared=120,target=100
        var fact48 = createFact("f1", new BigDecimal("48"));
        var completeness100 = ScoreCompletenessPolicy.evaluateCompleteness(List.of(fact48), "评分总分100分。");
        var resultWithDeficit = StructuredTableResult.evidencePipeline(
                List.of(fact48), List.of(), completeness100, List.of());

        var fact80 = createFact("f80", new BigDecimal("80"));
        var completeness120 = ScoreCompletenessPolicy.evaluateCompleteness(List.of(fact80), "评分总分120分。");
        var higherFallback = StructuredTableResult.evidencePipeline(
                List.of(fact80), List.of(), completeness120, List.of());

        var outcome = ScoreParseEntryRouter.extract(() -> resultWithDeficit, () -> higherFallback);

        // 断言：严禁采纳 80/120 分 fallback，坚决保留 48/100 分主结果并记录总分冲突
        assertThat(outcome.facts()).hasSize(1);
        assertThat(outcome.facts().getFirst().weight()).isEqualByComparingTo("48");
        assertThat(outcome.completeness().declaredTotalScore()).isEqualByComparingTo("100");
        assertThat(outcome.sourceDiagnostics()).contains("FALLBACK_SCHEME_CONFLICT:declared=120,target=100");
    }

    @Test
    void excessiveCandidatesCannotOverrideCloserFactsWithoutCompletenessProof() {
        // 主结果是 96/100 分事实（距离目标 100 仅差 4 分）；
        // fallback 仅有 110 分候选（超额，无来源完整性证明，距离目标 100 为 10 分）
        // 契约：超额候选绝不能被误判为“完整”，不能绕过距离防倒退比较；坚决保留更接近目标的 96 分事实
        var fact96 = createFact("f96", new BigDecimal("96"));
        var completeness100 = ScoreCompletenessPolicy.evaluateCompleteness(List.of(fact96), "评分总分100分。");
        var result96 = StructuredTableResult.evidencePipeline(
                List.of(fact96), List.of(), completeness100, List.of());

        var candidate110 = new ScoreCandidate("c1", "技术+商务", "超额候选", new BigDecimal("110"), "OBJECTIVE", "", "", "", "RECOVERED");
        var excessiveFallback = StructuredTableResult.ok(List.of(candidate110));

        var outcome = ScoreParseEntryRouter.extract(() -> result96, () -> excessiveFallback);

        // 断言：坚决保留 96 分事实，拒绝距离更远的 110 分超额候选
        assertThat(outcome).isSameAs(result96);
        assertThat(outcome.facts()).hasSize(1);
        assertThat(outcome.facts().getFirst().weight()).isEqualByComparingTo("96");
    }

    private static ScoreFact createFact(String id, BigDecimal weight) {
        return new ScoreFact(
                id, "tbl1", List.of("cell1"), "rule", "1", "商务", weight,
                new ScoreEvidence(ScoreEvidence.Kind.EXPLICIT_WEIGHT_COLUMN, weight.toPlainString(), "cell1", 0, weight.toPlainString().length(), "48分"),
                ScoreRule.MAX_SCORE, null, "BAIDU_OCR", 0.95, ValidationStatus.AUTO_APPROVED);
    }
}
