// Input: OpenAiScoreAnalyzer 的四路召回入口 + mock LLM/chunker 依赖
// Output: 召回一/三传 null section 不抛 NPE（回归）+ LLM chunk 失败重试/跳过可见性（spec 046 US3）
// Pos: Test/scoreparse/infrastructure/openai

package com.xiyu.bid.scoreparse.infrastructure.openai;

import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiBidAgentConfigurationResolver;
import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiBidAgentRequestConfig;
import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiStructuredOutputService;
import com.xiyu.bid.docinsight.domain.DocumentChunk;
import com.xiyu.bid.docinsight.domain.StructuralDocumentChunker;
import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 回归测试：召回一/召回三调用 toCandidate(criterion, null)，section 为 null 时
 * 直接访问 section.sectionTitle() 抛 NPE，导致整个解析任务异常终止
 * （2026-08-17 项目 226 线上事故，召回三有候选即必炸）。
 *
 * <p>spec 046 US3：召回四单 chunk LLM 失败重试恰好 1 次；两连败跳过该 chunk
 * （skippedChunks 计数）不阻断后续 chunk；recallCandidates 返回 {@link RecallOutcome}。
 */
@ExtendWith(MockitoExtension.class)
class OpenAiScoreAnalyzerTest {

    @Mock
    private OpenAiStructuredOutputService structuredOutputService;

    @Mock
    private OpenAiBidAgentConfigurationResolver configurationResolver;

    @Mock
    private StructuralDocumentChunker structuralChunker;

    /** 单行文本：行首编号 + 维度 + 条件得分语义（同时命中召回一提取与召回三语义段落过滤）。 */
    private static final String SCORE_LINE = "1 技术方案完整性 提供完整的实施方案得10分";

    /** 中性全文：无评分特征词，召回一/二/三产出为空，候选只来自 mock 的 LLM 输出。 */
    private static final String NEUTRAL_TEXT = "第一章 项目概述。本项目为信息化系统建设，工期二十四个月。";

    @Test
    void recallCandidates_nullSectionDoesNotThrowNpe() {
        OpenAiScoreAnalyzer analyzer = new OpenAiScoreAnalyzer(
                structuredOutputService, configurationResolver, structuralChunker);
        // chunker 返回空 → 召回四走"切片为空跳过"分支，不触发 LLM 调用（metadata 为 null，用 any() 匹配）
        when(structuralChunker.chunk(any(), any())).thenReturn(List.of());

        List<ScoreCandidate> candidates =
                analyzer.recallCandidates(SCORE_LINE, null, null).candidates();

        // 召回一/三应从该行产出候选（不为空），且 section 相关字段（contextNote/sourceText/location）为空串而非 NPE
        assertThat(candidates).isNotEmpty();
        assertThat(candidates.get(0).contextNote()).isNotNull();
        assertThat(candidates.get(0).sourceText()).isNotNull();
        assertThat(candidates.get(0).location()).isNotNull();
    }

    @Test
    void recallCandidates_retryOnceSucceeds_chunkNotSkipped() {
        OpenAiScoreAnalyzer analyzer = new OpenAiScoreAnalyzer(
                structuredOutputService, configurationResolver, structuralChunker);
        stubChunks("第一个片段：资质要求描述");
        when(configurationResolver.resolve(anyString())).thenReturn(requestConfig());
        // 首次调用抛异常 → 重试第二次返回有效输出
        when(structuredOutputService.request(anyString(), eq(ScoreCandidateOutput.class), any(), anyString()))
                .thenThrow(new RuntimeException("first attempt down"))
                .thenReturn(llmOutput("具备CMMI5级认证证书的得5分"));

        RecallOutcome outcome = analyzer.recallCandidates(NEUTRAL_TEXT, null, null);

        assertThat(outcome.skippedChunks()).isZero();
        assertThat(outcome.candidates())
                .extracting(ScoreCandidate::detail)
                .contains("具备CMMI5级认证证书的得5分");
        // 重试恰好 1 次：总共 2 次调用
        verify(structuredOutputService, times(2))
                .request(anyString(), eq(ScoreCandidateOutput.class), any(), anyString());
    }

    @Test
    void recallCandidates_consecutiveFailures_skipChunkAndContinueNext() {
        OpenAiScoreAnalyzer analyzer = new OpenAiScoreAnalyzer(
                structuredOutputService, configurationResolver, structuralChunker);
        stubChunks("第一个片段：资质要求描述", "第二个片段：业绩要求描述");
        when(configurationResolver.resolve(anyString())).thenReturn(requestConfig());
        // chunk1 两次尝试均失败（跳过）→ chunk2 第一次尝试即成功
        when(structuredOutputService.request(anyString(), eq(ScoreCandidateOutput.class), any(), anyString()))
                .thenThrow(new RuntimeException("fail #1"))
                .thenThrow(new RuntimeException("fail #2"))
                .thenReturn(llmOutput("近三年类似项目业绩每个得2分"));

        RecallOutcome outcome = analyzer.recallCandidates(NEUTRAL_TEXT, null, null);

        assertThat(outcome.skippedChunks()).isEqualTo(1);
        assertThat(outcome.candidates())
                .extracting(ScoreCandidate::detail)
                .contains("近三年类似项目业绩每个得2分");
        // chunk1 共 2 次尝试（重试 1 次）+ chunk2 1 次 = 3 次
        verify(structuredOutputService, times(3))
                .request(anyString(), eq(ScoreCandidateOutput.class), any(), anyString());
    }

    @Test
    void recallCandidates_allChunksSucceed_noSkips() {
        OpenAiScoreAnalyzer analyzer = new OpenAiScoreAnalyzer(
                structuredOutputService, configurationResolver, structuralChunker);
        stubChunks("第一个片段：体系认证描述", "第二个片段：人员证书描述");
        when(configurationResolver.resolve(anyString())).thenReturn(requestConfig());
        when(structuredOutputService.request(anyString(), eq(ScoreCandidateOutput.class), any(), anyString()))
                .thenReturn(llmOutput("具备ISO9001质量管理体系认证的得3分"))
                .thenReturn(llmOutput("项目经理具备PMP证书的得2分"));

        RecallOutcome outcome = analyzer.recallCandidates(NEUTRAL_TEXT, null, null);

        assertThat(outcome.skippedChunks()).isZero();
        assertThat(outcome.candidates())
                .extracting(ScoreCandidate::detail)
                .contains("具备ISO9001质量管理体系认证的得3分", "项目经理具备PMP证书的得2分");
    }

    private void stubChunks(String... texts) {
        List<DocumentChunk> chunks = new ArrayList<>();
        for (String text : texts) {
            chunks.add(new DocumentChunk(text, List.of()));
        }
        when(structuralChunker.chunk(any(), any())).thenReturn(chunks);
    }

    private static ScoreCandidateOutput llmOutput(String detail) {
        ScoreCandidateOutput.Candidate candidate = new ScoreCandidateOutput.Candidate();
        candidate.code = "A1";
        candidate.dim = "资质";
        candidate.detail = detail;
        candidate.weight = 5.0;
        ScoreCandidateOutput output = new ScoreCandidateOutput();
        output.candidates = new ArrayList<>(List.of(candidate));
        return output;
    }

    /** OpenAiBidAgentApiStyle 为包私有枚举，scoreparse 测试包无法直接构造，mock record 透传即可。 */
    private static OpenAiBidAgentRequestConfig requestConfig() {
        return mock(OpenAiBidAgentRequestConfig.class);
    }
}
