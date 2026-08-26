// Input: ScoreGlobalMergeExecutor（mock LLM 边界）
// Output: 成功映射 / 两连败 fail-open / 前置跳过 / 单败重试 行为验证（spec 055 FR-3）
// Pos: Test/scoreparse/infrastructure/openai

package com.xiyu.bid.scoreparse.infrastructure.openai;

import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiBidAgentConfigurationResolver;
import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiBidAgentRequestConfig;
import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiStructuredOutputService;
import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScoreGlobalMergeExecutorTest {

    @Mock
    private OpenAiStructuredOutputService structuredOutputService;
    @Mock
    private OpenAiBidAgentConfigurationResolver configurationResolver;

    private ScoreGlobalMergeExecutor executor;

    private ScoreCandidate poolItem(String detail, String weight) {
        return new ScoreCandidate("", "价格部分", detail,
                weight == null ? null : new BigDecimal(weight), null, null, detail, "P47", null);
    }

    @BeforeEach
    void setUp() {
        executor = new ScoreGlobalMergeExecutor(structuredOutputService, configurationResolver);
    }

    private ScoreGlobalMergeOutput outputWith(String detail, double weight) {
        ScoreGlobalMergeOutput output = new ScoreGlobalMergeOutput();
        ScoreGlobalMergeOutput.MergedItem item = new ScoreGlobalMergeOutput.MergedItem();
        item.code = "S9";
        item.dim = "技术服务部分";
        item.detail = detail;
        item.weight = weight;
        item.scoreTypeGuess = "SUBJECTIVE";
        item.sourceText = detail;
        output.items = List.of(item);
        output.summary = new ScoreGlobalMergeOutput.Summary();
        return output;
    }

    @Test
    void resolve_success_mapsToCandidatesWithGlobalMergePattern() {
        when(configurationResolver.resolve(any())).thenReturn(mock(OpenAiBidAgentRequestConfig.class));
        when(structuredOutputService.request(contains("硬性规则"), eq(ScoreGlobalMergeOutput.class),
                any(), any())).thenReturn(outputWith("方案优得1分", 1));
        List<ScoreCandidate> result = executor.resolve(
                List.of(poolItem("方案优得1分", "1"), poolItem("方案优得1分残缺版", null)), "原文：方案优得1分");
        assertThat(result).hasSize(1);
        ScoreCandidate candidate = result.get(0);
        assertThat(candidate.semanticPattern()).isEqualTo("GLOBAL_MERGE");
        assertThat(candidate.weight()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(candidate.dim()).isEqualTo("技术服务部分");
    }

    @Test
    void resolve_llmFailsTwice_returnsEmpty() {
        when(configurationResolver.resolve(any())).thenReturn(mock(OpenAiBidAgentRequestConfig.class));
        when(structuredOutputService.request(any(), eq(ScoreGlobalMergeOutput.class), any(), any()))
                .thenThrow(new RuntimeException("boom"));
        List<ScoreCandidate> result = executor.resolve(
                List.of(poolItem("a得1分", "1"), poolItem("b得2分", "2")), "原文");
        assertThat(result).isEmpty();
        verify(structuredOutputService, times(2))
                .request(any(), eq(ScoreGlobalMergeOutput.class), any(), any());
    }

    @Test
    void resolve_firstAttemptFailsSecondSucceeds_retries() {
        when(configurationResolver.resolve(any())).thenReturn(mock(OpenAiBidAgentRequestConfig.class));
        when(structuredOutputService.request(any(), eq(ScoreGlobalMergeOutput.class), any(), any()))
                .thenThrow(new RuntimeException("transient"))
                .thenReturn(outputWith("方案优得1分", 1));
        List<ScoreCandidate> result = executor.resolve(
                List.of(poolItem("方案优得1分", "1"), poolItem("另一项得2分", "2")), "原文");
        assertThat(result).hasSize(1);
    }

    @Test
    void resolve_poolTooSmallOrBlankSection_skipsLlm() {
        assertThat(executor.resolve(List.of(poolItem("a得1分", "1")), "原文")).isEmpty();
        assertThat(executor.resolve(List.of(), "原文")).isEmpty();
        assertThat(executor.resolve(null, "原文")).isEmpty();
        assertThat(executor.resolve(
                List.of(poolItem("a得1分", "1"), poolItem("b得2分", "2")), "  ")).isEmpty();
        verify(structuredOutputService, times(0)).request(any(), any(), any(), any());
    }

    @Test
    void resolve_nullOrBlankDimInOutput_resolvesFallbackDim() {
        when(configurationResolver.resolve(any())).thenReturn(mock(OpenAiBidAgentRequestConfig.class));
        ScoreGlobalMergeOutput output = outputWith("具备相关资质证书", 10);
        output.items.get(0).dim = null;
        when(structuredOutputService.request(any(), eq(ScoreGlobalMergeOutput.class), any(), any()))
                .thenReturn(output);

        List<ScoreCandidate> result = executor.resolve(
                List.of(poolItem("具备相关资质证书", "10"), poolItem("另一项", "20")), "原文：具备相关资质证书");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).dim()).isNotBlank();
        assertThat(result.get(0).dim()).isEqualTo("具备相关资质证书");
    }

    @Test
    void summarizePool_truncatesDetailAndEscapesQuotes() {
        String longDetail = "长".repeat(300) + "\"引号\"";
        String json = ScoreGlobalMergeExecutor.summarizePool(List.of(poolItem(longDetail, "5")));
        assertThat(json).contains("\"weight\":5");
        assertThat(json.length()).isLessThan(500);
        assertThat(json).doesNotContain("引号"); // 截断在 200 字处，引号部分已被截掉且转义安全
    }
}
