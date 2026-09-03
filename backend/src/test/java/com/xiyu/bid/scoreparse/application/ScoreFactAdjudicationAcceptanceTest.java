// Input: ScoreGlobalMergeOrchestrator + Mock ScoreGlobalMergeExecutor
// Output: Phase 3 验收断言（LLM 受限语义适配器安全门禁与 fail-open 确定性复现）
// Pos: Test/scoreparse/application (spec 059 Phase 3 - T304)
package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.ScoreEvidence;
import com.xiyu.bid.scoreparse.domain.ScoreFact;
import com.xiyu.bid.scoreparse.domain.ScoreParseDecision;
import com.xiyu.bid.scoreparse.domain.llm.RawFactOperation;
import com.xiyu.bid.scoreparse.infrastructure.openai.ScoreGlobalMergeExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScoreFactAdjudicationAcceptanceTest {

    @Mock
    private ScoreGlobalMergeExecutor mergeExecutor;

    @Mock
    private ObjectProvider<ScoreGlobalMergeExecutor> mergeExecutorProvider;

    private ScoreGlobalMergeOrchestrator orchestrator;

    private static final String FULL_TEXT = """
            评分办法
            商务部分：
            价格合理得10分。
            售后服务承诺得10分。
            """;

    @BeforeEach
    void setUp() {
        orchestrator = new ScoreGlobalMergeOrchestrator(mergeExecutorProvider);
        org.mockito.Mockito.lenient().when(mergeExecutorProvider.getIfAvailable())
                .thenReturn(mergeExecutor);
    }

    private ScoreFact createFact(String id, String tableId, String cellId, String text, String code, String weight) {
        return new ScoreFact(
                id,
                tableId,
                List.of(cellId),
                text,
                code,
                "商务",
                new BigDecimal(weight),
                new ScoreEvidence(ScoreEvidence.Kind.EXPLICIT_WEIGHT_COLUMN, weight, cellId, 0, weight.length(), weight + "分"),
                null,
                null,
                "TABLE",
                1.0,
                ScoreFact.ValidationStatus.AUTO_APPROVED
        );
    }

    @Test
    @DisplayName("验收1: LLM 企图发明新事实（CREATE_SCORE_FACT），必须被拦截且转入人工复核，不得向事实池注入新项")
    void whenLlmAttemptsCreateScoreFact_blockedAndTurnedToReviewRequired() {
        ScoreFact fact1 = createFact("f1", "t0", "t0-r1-c1", "价格合理得10分", "1.1", "10");
        RawFactOperation createOp = new RawFactOperation(
                "f-new", "CREATE_SCORE_FACT", "技术", null, null,
                1.0, List.of("t0-r1-c1"), "凭空发明新事实", 20.0, "细则", "原文", "true");

        when(mergeExecutor.resolveRawFacts(anyList(), anyString())).thenReturn(List.of(createOp));

        var outcome = orchestrator.adjudicateFacts(List.of(fact1), FULL_TEXT);

        assertThat(outcome.invoked()).isTrue();
        assertThat(outcome.violations()).anyMatch(v -> v.contains("unknown factId: f-new") || v.contains("CREATE_SCORE_FACT"));
        assertThat(outcome.decisionStatus()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
        assertThat(outcome.facts()).hasSize(1);
        assertThat(outcome.facts()).noneMatch(f -> f.factId().equals("f-new"));
        assertThat(outcome.facts().get(0).factId()).isEqualTo("f1");
    }

    @Test
    @DisplayName("验收2: LLM 企图篡改权重（weight），必须被拦截且不回写，原事实权重完好")
    void whenLlmAttemptsToModifyWeight_blockedAndFactsNotMutated() {
        ScoreFact fact1 = createFact("f1", "t0", "t0-r1-c1", "价格合理得10分", "1.1", "10");
        RawFactOperation op = new RawFactOperation(
                "f1", "KEEP", null, null, null,
                1.0, List.of("t0-r1-c1"), "改分", 99.0, null, null, null);

        when(mergeExecutor.resolveRawFacts(anyList(), anyString())).thenReturn(List.of(op));

        var outcome = orchestrator.adjudicateFacts(List.of(fact1), FULL_TEXT);

        assertThat(outcome.violations()).anyMatch(v -> v.contains("weight"));
        assertThat(outcome.decisionStatus()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
        assertThat(outcome.facts().get(0).weight()).isEqualByComparingTo("10");
        assertThat(outcome.facts().get(0).validationStatus()).isEqualTo(ScoreFact.ValidationStatus.REVIEW_REQUIRED);
    }

    @Test
    @DisplayName("验收3: LLM 企图篡改细则文本（detail/sourceText），必须被拦截且不回写")
    void whenLlmAttemptsToModifyDetailOrSourceText_blockedAndNotMutated() {
        ScoreFact fact1 = createFact("f1", "t0", "t0-r1-c1", "价格合理得10分", "1.1", "10");
        RawFactOperation op = new RawFactOperation(
                "f1", "KEEP", null, null, null,
                1.0, List.of("t0-r1-c1"), "篡改文本", null, "篡改detail", "篡改sourceText", null);

        when(mergeExecutor.resolveRawFacts(anyList(), anyString())).thenReturn(List.of(op));

        var outcome = orchestrator.adjudicateFacts(List.of(fact1), FULL_TEXT);

        assertThat(outcome.violations()).anyMatch(v -> v.contains("detail") || v.contains("sourceText"));
        assertThat(outcome.decisionStatus()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
        assertThat(outcome.facts().get(0).sourceText()).isEqualTo("价格合理得10分");
    }

    @Test
    @DisplayName("验收4: LLM 理由引用跨表 cellId，必须被拦截转人工复核")
    void whenLlmReferencesCrossTableCells_blocked() {
        ScoreFact fact1 = createFact("f1", "t0", "t0-r1-c1", "价格合理得10分", "1.1", "10");
        RawFactOperation op = RawFactOperation.of(
                "f1", "KEEP", null, null, null,
                1.0, List.of("t99-r0-c0"), "非法跨表引用单元格");

        when(mergeExecutor.resolveRawFacts(anyList(), anyString())).thenReturn(List.of(op));

        var outcome = orchestrator.adjudicateFacts(List.of(fact1), FULL_TEXT);

        assertThat(outcome.violations()).anyMatch(v -> v.contains("cross-table cell reference"));
        assertThat(outcome.decisionStatus()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
    }

    @Test
    @DisplayName("验收5: LLM 跨表建立父子关系（LINK_PARENT），必须被拦截转人工复核")
    void whenLlmAttemptsCrossTableLinkParent_blocked() {
        ScoreFact factTable0 = createFact("f0", "t0", "t0-r1-c1", "表0价格分", "1.1", "10");
        ScoreFact factTable1 = createFact("f1", "t1", "t1-r1-c1", "表1售后分", "2.1", "10");

        RawFactOperation op = RawFactOperation.of(
                "f1", "LINK_PARENT", null, "f0", null,
                1.0, List.of("t1-r1-c1"), "跨表挂载");

        when(mergeExecutor.resolveRawFacts(anyList(), anyString())).thenReturn(List.of(op));

        var outcome = orchestrator.adjudicateFacts(List.of(factTable0, factTable1), FULL_TEXT);

        assertThat(outcome.violations()).anyMatch(v -> v.contains("cross-table link rejected"));
        assertThat(outcome.decisionStatus()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
        // 跨表父子绝不建立
        assertThat(outcome.facts().stream().filter(f -> f.factId().equals("f1")).findFirst().orElseThrow().parentFactId())
                .isNullOrEmpty();
    }

    @Test
    @DisplayName("验收6: LLM 语义维度分类置信度不足（< 0.80），拦截转人工复核且不覆盖维度")
    void whenLlmProvidesLowConfidenceClassification_blocked() {
        ScoreFact fact1 = createFact("f1", "t0", "t0-r1-c1", "价格合理得10分", "1.1", "10");
        RawFactOperation op = RawFactOperation.of(
                "f1", "CLASSIFY", "低置信维度", null, null,
                0.65, List.of("t0-r1-c1"), "置信度过低");

        when(mergeExecutor.resolveRawFacts(anyList(), anyString())).thenReturn(List.of(op));

        var outcome = orchestrator.adjudicateFacts(List.of(fact1), FULL_TEXT);

        assertThat(outcome.violations()).anyMatch(v -> v.contains("low confidence classification"));
        assertThat(outcome.decisionStatus()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
        // 原维度保持不变
        assertThat(outcome.facts().get(0).dimension()).isEqualTo("商务");
    }

    @Test
    @DisplayName("验收7: LLM 故障/超时/不可用时，fail-open 确定性链路可稳定复现 AUTO_APPROVED 或 REVIEW_REQUIRED")
    void whenLlmUnavailableOrFails_deterministicFactsPreservedReproducibly() {
        ScoreFact fact1 = createFact("f1", "t0", "t0-r1-c1", "价格合理得10分", "1.1", "10");
        // LLM 抛出异常或两连败
        when(mergeExecutor.resolveRawFacts(anyList(), anyString())).thenReturn(List.of());

        var outcome = orchestrator.adjudicateFacts(List.of(fact1), FULL_TEXT);

        assertThat(outcome.invoked()).isFalse();
        assertThat(outcome.skipReason()).isEqualTo("LLM_UNAVAILABLE");
        assertThat(outcome.facts()).hasSize(1);
        assertThat(outcome.facts().get(0).factId()).isEqualTo("f1");
        assertThat(outcome.decisionStatus()).isEqualTo(ScoreParseDecision.AUTO_APPROVED);
    }

    @Test
    @DisplayName("验收8: LLM 操作合法合规时，平滑应用层级与去重标记")
    void whenLlmOperationsAreValid_hierarchyAndDuplicatesAppliedCleanly() {
        ScoreFact parent = createFact("f-parent", "t0", "t0-r0-c0", "商务部分 20分", "1", "20");
        ScoreFact child = createFact("f-child", "t0", "t0-r1-c1", "价格条款 10分", "1.1", "10");
        ScoreFact duplicate = createFact("f-dup", "t0", "t0-r1-c2", "价格条款 10分(副本)", "1.1", "10");

        List<RawFactOperation> validOps = List.of(
                RawFactOperation.of("f-child", "LINK_PARENT", null, "f-parent", null, 0.95, List.of("t0-r1-c1"), "归属于商务"),
                RawFactOperation.of("f-child", "MARK_DUPLICATE", null, null, List.of("f-dup"), 0.95, List.of("t0-r1-c1"), "折叠重复"),
                RawFactOperation.of("f-child", "CLASSIFY", "商务/报价", null, null, 0.95, List.of("t0-r1-c1"), "分类")
        );

        when(mergeExecutor.resolveRawFacts(anyList(), anyString())).thenReturn(validOps);

        var outcome = orchestrator.adjudicateFacts(List.of(parent, child, duplicate), FULL_TEXT);

        assertThat(outcome.invoked()).isTrue();
        assertThat(outcome.violations()).isEmpty();

        ScoreFact childFact = outcome.facts().stream().filter(f -> f.factId().equals("f-child")).findFirst().orElseThrow();
        ScoreFact dupFact = outcome.facts().stream().filter(f -> f.factId().equals("f-dup")).findFirst().orElseThrow();

        assertThat(childFact.parentFactId()).isEqualTo("f-parent");
        assertThat(childFact.dimension()).isEqualTo("商务/报价");
        assertThat(dupFact.validationStatus()).isEqualTo(ScoreFact.ValidationStatus.DUPLICATE);
    }
}
