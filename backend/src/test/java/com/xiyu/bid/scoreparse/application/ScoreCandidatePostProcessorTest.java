// Input: mocked OpenAiScoreAnalyzer(LLM 边界) + 真实编排器(无 executor 环境=fail-open/可stub Detailed)
// Output: PostProcessor 治理链行为——归并委托、run-summary 装配、0 项失败、回补不误触发（spec 057）
// Pos: Test/scoreparse/application

package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.RecallOutcome;
import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
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
        // 归并输出 1 项触发 spec047 归一化 → 清单被放大副本替换（weight 10→100），code 保持
        assertThat(out.candidates()).extracting(ScoreCandidate::code).containsExactly("M1");
        assertThat(out.originalTotal()).isEqualByComparingTo("10");
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
}
