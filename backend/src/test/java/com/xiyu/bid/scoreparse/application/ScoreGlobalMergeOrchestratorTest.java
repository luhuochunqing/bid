// Input: ScoreGlobalMergeOrchestrator（mock executor LLM 边界）
// Output: fail-open 语义验证（spec 055 US1-AC4 / FR-3 / FR-6）
// Pos: Test/scoreparse/application

package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import com.xiyu.bid.scoreparse.infrastructure.openai.ScoreGlobalMergeExecutor;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScoreGlobalMergeOrchestratorTest {

    @Mock
    private ScoreGlobalMergeExecutor mergeExecutor;

    @Mock
    private ObjectProvider<ScoreGlobalMergeExecutor> mergeExecutorProvider;

    private ScoreGlobalMergeOrchestrator orchestrator;

    private ScoreCandidate poolItem(String detail, String weight) {
        return new ScoreCandidate("", "价格部分", detail,
                weight == null ? null : new BigDecimal(weight), null, null, detail, "P47", null);
    }

    /** 章节原文含两个独立条款（detail 可被守卫原文背书） */
    private static final String FULL_TEXT = """
            评分办法
            价格部分：
            不平衡报价评审：报价合理得4-5分。
            投标人提供上架商品价格管控方案：优秀得4分。
            """;

    @BeforeEach
    void setUp() {
        orchestrator = new ScoreGlobalMergeOrchestrator(mergeExecutorProvider);
        org.mockito.Mockito.lenient().when(mergeExecutorProvider.getIfAvailable())
                .thenReturn(mergeExecutor);
    }

    @Test
    void apply_executorBeanMissing_e2eProfile_degradesToHeuristicPool() {
        // C1 修复回归：e2e profile 无 executor Bean → 懒解析 null → fail-open 原池返回
        when(mergeExecutorProvider.getIfAvailable()).thenReturn(null);
        List<ScoreCandidate> pool = List.of(
                poolItem("不平衡报价评审：报价合理得4-5分。", "5"),
                poolItem("投标人提供上架商品价格管控方案：优秀得4分。", "4"));

        assertThat(orchestrator.applyGlobalMerge(pool, FULL_TEXT)).isSameAs(pool);
        verify(mergeExecutor, never()).resolve(anyList(), anyString(), any());
    }

    @Test
    void apply_mergeSucceeds_returnsGuardAccepted() {
        List<ScoreCandidate> pool = List.of(
                poolItem("不平衡报价评审：报价合理得4-5分。", "5"),
                poolItem("投标人提供上架商品价格管控方案：优秀得4分。", "4"));
        when(mergeExecutor.resolve(anyList(), anyString(), any())).thenReturn(List.of(
                new ScoreCandidate("", "价格部分", "不平衡报价评审：报价合理得4-5分。",
                        new BigDecimal("5"), null, null, "原文", "P47", "GLOBAL_MERGE"),
                new ScoreCandidate("", "价格部分", "投标人提供上架商品价格管控方案：优秀得4分。",
                        new BigDecimal("4"), null, null, "原文", "P47", "GLOBAL_MERGE")));

        List<ScoreCandidate> result = orchestrator.applyGlobalMerge(pool, FULL_TEXT);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ScoreCandidate::detail)
                .contains("不平衡报价评审：报价合理得4-5分。", "投标人提供上架商品价格管控方案：优秀得4分。");
    }

    @Test
    void apply_llmUnavailable_fallsBackToHeuristicPool() {
        List<ScoreCandidate> pool = List.of(
                poolItem("不平衡报价评审：报价合理得4-5分。", "5"),
                poolItem("投标人提供上架商品价格管控方案：优秀得4分。", "4"));
        when(mergeExecutor.resolve(anyList(), anyString(), any())).thenReturn(List.of());

        assertThat(orchestrator.applyGlobalMerge(pool, FULL_TEXT)).isSameAs(pool);
    }

    @Test
    void apply_guardDropsAll_restoresHighConfidencePoolItems() {
        List<ScoreCandidate> pool = List.of(
                poolItem("不平衡报价评审：报价合理得4-5分。", "5"),
                poolItem("投标人提供上架商品价格管控方案：优秀得4分。", "4"),
                new ScoreCandidate("", "价格部分", "低置信无背书项", null, null, null, "", "P47", null));
        // 全是幻觉（章节原文中不存在）→ 守卫丢弃后回补池中高置信项，低置信项不回补
        when(mergeExecutor.resolve(anyList(), anyString(), any())).thenReturn(List.of(
                new ScoreCandidate("", "价格部分", "凭空捏造的评分条款得9分。",
                        new BigDecimal("9"), null, null, "原文", "P47", "GLOBAL_MERGE")));

        List<ScoreCandidate> result = orchestrator.applyGlobalMerge(pool, FULL_TEXT);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ScoreCandidate::detail)
                .contains("不平衡报价评审：报价合理得4-5分。", "投标人提供上架商品价格管控方案：优秀得4分。")
                .doesNotContain("低置信无背书项");
    }

    @Test
    void apply_poolUnderTwo_skipsLlm() {
        List<ScoreCandidate> pool = List.of(poolItem("唯一项得5分", "5"));

        assertThat(orchestrator.applyGlobalMerge(pool, FULL_TEXT)).isSameAs(pool);
        assertThat(orchestrator.applyGlobalMerge(null, FULL_TEXT)).isNull();
        verify(mergeExecutor, never()).resolve(anyList(), anyString(), any());
    }

    @Test
    void apply_sectionLocatorFails_skipsAdjudication_noLlmCall() {
        // spec 057 FR-01：无评分章节时禁用全文前缀裁决（旧降级会拿错语料误杀真项+污染幻觉计数）
        String noSectionText = "不平衡报价评审：报价合理得4-5分。\n投标人提供上架商品价格管控方案：优秀得4分。";
        List<ScoreCandidate> pool = List.of(
                poolItem("不平衡报价评审：报价合理得4-5分。", "5"),
                poolItem("投标人提供上架商品价格管控方案：优秀得4分。", "4"));

        List<ScoreCandidate> result = orchestrator.applyGlobalMerge(pool, noSectionText);

        assertThat(result).isSameAs(pool);
        verify(mergeExecutor, never()).resolve(anyList(), anyString(), any());
    }

    @Test
    void apply_truncatedCorpus_logsTruncatedFlag() {
        // spec 057 FR-03：定位成功但拼接超限 → truncated 标记进摘要日志（此处仅断言主流程不受影响）
        StringBuilder big = new StringBuilder("评分办法\n价格部分：\n");
        while (big.length() < 13_000) {
            big.append("不平衡报价评审：报价合理得4-5分。\n");
        }
        List<ScoreCandidate> pool = List.of(
                poolItem("不平衡报价评审：报价合理得4-5分。", "5"),
                poolItem("投标人提供上架商品价格管控方案：优秀得4分。", "4"));
        when(mergeExecutor.resolve(anyList(), anyString(), any())).thenReturn(List.of(
                new ScoreCandidate("", "价格部分", "不平衡报价评审：报价合理得4-5分。",
                        new BigDecimal("5"), null, null, "原文", "P47", "GLOBAL_MERGE")));

        List<ScoreCandidate> result = orchestrator.applyGlobalMerge(pool, big.toString());

        assertThat(result).hasSize(2); // 归并采纳1 + 池背书回补1，主流程不因截断改变 fail-open 行为
    }
}
