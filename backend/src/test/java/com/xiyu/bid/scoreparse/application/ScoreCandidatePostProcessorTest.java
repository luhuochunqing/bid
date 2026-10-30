// Input: mocked OpenAiScoreAnalyzer(LLM 边界) + 真实编排器(无 executor 环境=fail-open/可stub Detailed)
// Output: PostProcessor 治理链行为——归并委托、run-summary 装配、0 项失败、回补不误触发（spec 057）
// Pos: Test/scoreparse/application

package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.RecallOutcome;
import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import com.xiyu.bid.scoreparse.domain.ScoreParseDecision;
import com.xiyu.bid.scoreparse.infrastructure.openai.OpenAiScoreAnalyzer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScoreCandidatePostProcessorTest {

    @Mock
    private OpenAiScoreAnalyzer scoreAnalyzer;

    @Mock
    private ScoreGlobalMergeOrchestrator orchestrator;

    private ScoreCandidatePostProcessor processor;

    private ScoreCandidate candidate(String code, String dim, String detail, String weight) {
        return new ScoreCandidate(code, dim, detail, new BigDecimal(weight),
                "OBJECTIVE", null, detail, "P47", "SEMANTIC");
    }

    private RecallOutcome recallOf(List<ScoreCandidate> cands, int skipped) {
        return new RecallOutcome(cands, skipped,
                java.util.Map.of("lane1", cands.size(), "lane2", 0, "lane3", 0, "lane4", 0),
                0, 0, "test-model");
    }

    private static final ScoreCandidatePostProcessor.StageReporter NOOP = (p, s) -> {
    };

    @BeforeEach
    void setUp() {
        processor = new ScoreCandidatePostProcessor(scoreAnalyzer, orchestrator,
                com.xiyu.bid.scoreparse.domain.ScoreVocabularyRegistry.builder().build());
        // 缺省：归并未裁决（fail-open 保持启发式结果），各用例按需覆盖
        lenient().when(orchestrator.applyGlobalMergeDetailed(anyList(), anyString(), any()))
                .thenReturn(ScoreGlobalMergeOrchestrator.MergeStats.notInvoked("POOL_UNDER_TWO"));
    }

    @Test
    void delegatedMergeOutput_becomesFinalList_withSummaryJson() {
        List<ScoreCandidate> merged = List.of(candidate("M1", "技术", "方案完整可行得10分。注：投标人须自行提供相关承诺。", "10"));
        lenient().when(orchestrator.applyGlobalMergeDetailed(anyList(), anyString(), any()))
                .thenReturn(new ScoreGlobalMergeOrchestrator.MergeStats(
                        merged, true, null, true, 1, 0, 2));
        var balanced = List.of(
                candidate("A1", "资质", "具备 CMMI 认证证书", "60"),
                candidate("A2", "业绩", "近三年业绩合同证明材料齐全有效", "40"));

        var out = processor.process("t1", "评分办法文本", recallOf(balanced, 1),
                List.of(candidate(null, "表格", "表格直抽行示例条款", "0") // 权重0会被 filterInvalidWeights 剔除
                ), NOOP);

        assertThat(out.failMessage()).isNull();
        // spec 059 Phase 4: 取消自动归一化，保留原始权重 10，决策为 REVIEW_REQUIRED
        assertThat(out.candidates()).extracting(ScoreCandidate::code).containsExactly("M1");
        assertThat(out.candidates()).extracting(ScoreCandidate::weight).containsExactly(new BigDecimal("10"));
        assertThat(out.originalTotal()).isEqualByComparingTo("10");
        assertThat(out.decisionStatus()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
        assertThat(out.fallbackReason()).isEqualTo("WEIGHT_SUM_MISMATCH");
        assertThat(out.runSummaryJson())
                .contains("\"invoked\":true", "\"truncated\":true",
                        "\"poolBackedKept\":1", "\"restored\":2", "\"llmModel\":\"test-model\"");
    }

    @Test
    void fallbackWhenMergeNotInvoked_keepsHeuristicList() {
        lenient().when(orchestrator.applyGlobalMergeDetailed(anyList(), anyString(), any()))
                .thenReturn(ScoreGlobalMergeOrchestrator.MergeStats.notInvoked("SECTION_LOCATION_FAILED"));
        var heuristic = List.of(candidate("H1", "商务", "服务方案针对性强得满分", "100"));

        var out = processor.process("t2", "全文", recallOf(heuristic, 0), List.of(), NOOP);

        assertThat(out.candidates()).isEqualTo(heuristic);
        assertThat(out.runSummaryJson()).contains("\"invoked\":false");
    }

    @Test
    void zeroValidItems_failMessageCarried_forCallerFailTask() {
        var out = processor.process("t3", "空文档", recallOf(List.of(), 0), List.of(), NOOP);
        assertThat(out.failMessage()).isNotBlank();
        assertThat(out.candidates()).isEmpty();
    }

    @Test
    void balancedWeights_gapRecheckNeverTriggered() {
        var balanced = List.of(
                candidate("A1", "资质", "具备 CMMI 认证证书", "60"),
                candidate("A2", "业绩", "近三年业绩合同证明材料齐全有效", "40"));

        verify(scoreAnalyzer, never()).recheckGaps(anyString(), anyList());
    }

    @Test
    void zeroValidItems_withExternalSoftwareDeclaration_customFailMessage() {
        String docText = "2.2 详细评审标准\n详见ZBJ格式采购文件“评审项目”页签。\n2.2.3 评分标准：详见ZBJ格式采购文件“评审项目”页签。";
        var out = processor.process("t5", docText, recallOf(List.of(), 0), List.of(), NOOP);
        assertThat(out.failMessage()).contains("ZBJ");
        assertThat(out.candidates()).isEmpty();
    }

    @Test
    void gapRecheckItems_actAsReviewSuggestions_doNotFillWeights() {
        // 总分 80（有缺口），触发 gap recheck
        var unbalanced = List.of(
                candidate("A1", "资质", "具备 CMMI 认证证书", "50"),
                candidate("A2", "业绩", "近三年业绩合同证明材料齐全有效", "30"));
        // LLM 回补召回了 20 分的项
        when(scoreAnalyzer.recheckGaps(anyString(), anyList())).thenReturn(List.of(
                candidate("A3", "技术", "技术方案完整得20分", "20")));

        var out = processor.process("t6", "全文", recallOf(unbalanced, 0), List.of(), NOOP);

        // spec 059 Phase 3/4：LLM 回补项仅作为复核建议，不填补确定性事实权重缺口，保持触发复核
        assertThat(out.decisionStatus()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
        assertThat(out.fallbackReason()).isIn("RESORT_TO_GAP_RECHECK", "WEIGHT_SUM_MISMATCH");
        // 确保未直接拼接 A3 成为 100 分直接 AUTO_APPROVED
        assertThat(out.decisionStatus()).isNotEqualTo(ScoreParseDecision.AUTO_APPROVED);
    }

    @Test
    void priceFormulaCandidate_withZeroWeight_notDroppedByFilterInvalidWeights() {
        ScoreCandidate formulaItem = new ScoreCandidate("", "折扣率评价",
                "算术平均值法：评审价格得分=100–100×n×|应答人评审价格-基准价|/基准价",
                BigDecimal.ZERO, "OBJECTIVE", null, "折扣率评价 原文", "table-row-1",
                "STRUCTURED_TABLE", "报价", "CELL");

        var out = processor.process("t7", "全文", recallOf(List.of(), 0), List.of(formulaItem), NOOP);

        assertThat(out.failMessage()).isNull();
        assertThat(out.candidates()).containsExactly(formulaItem);
    }

    @Test
    void fallbackPostProcessorCannotRelabelLegacyCandidatesAsPrimary() {
        String doc = "评分办法：总分100分。1.财务状况10分：审计报告无保留意见。2.业绩90分：具备同类业绩。";
        var validCandidates = List.of(
                candidate("1", "财务状况", "审计报告无保留意见。", "10"),
                candidate("2", "业绩", "具备同类业绩。", "90"));

        var out = processor.process("t8", doc, recallOf(validCandidates, 0), List.of(), NOOP);

        assertThat(out.decisionStatus()).isEqualTo(ScoreParseDecision.AUTO_APPROVED);
        assertThat(out.pipelineVersion()).isEqualTo(com.xiyu.bid.scoreparse.domain.ScoreParsePipeline.LEGACY_FALLBACK);
        assertThat(out.candidates()).hasSize(2);
        verify(orchestrator).applyGlobalMergeDetailed(anyList(), anyString(), any());
    }
}
