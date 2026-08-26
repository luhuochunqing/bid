// Input: ScoreMergeResultGuard（guard 方法）
// Output: 归并裁决结果双向防御行为验证（spec 055 US1 / 边界情况）
// Pos: Test/scoreparse/domain

package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreMergeResultGuardTest {

    private static final String SECTION_TEXT = """
            价格部分：
            不平衡报价评审：评标委员会针对各投标人分项报价中的单项折扣率进行评价，报价合理得4-5分。5分
            投标人提供上架商品价格管控方案：重点阐述商品定价策略。优秀得4分。4分
            技术方案：方案完整可行得10分。注：投标人须自行提供相关承诺。
            """;

    private ScoreCandidate poolItem(String detail, String weight) {
        return new ScoreCandidate("", "价格部分", detail,
                weight == null ? null : new BigDecimal(weight), null, null, detail, "P47", null);
    }

    @Test
    void guard_hallucinatedDetailDropped_backedDetailKept() {
        List<ScoreCandidate> merged = List.of(
                new ScoreCandidate("", "价格部分",
                        "不平衡报价评审：评标委员会针对各投标人分项报价中的单项折扣率进行评价，报价合理得4-5分。",
                        new BigDecimal("5"), null, null, "原文", "P47", null),
                new ScoreCandidate("", "价格部分",
                        "AI编造的评分项：完全不存在的条款内容得9分。",
                        new BigDecimal("9"), null, null, "原文", "P47", null));
        ScoreMergeResultGuard.Outcome outcome = ScoreMergeResultGuard.guard(
                List.of(poolItem("不平衡报价评审：评标委员会针对各投标人分项报价中的单项折扣率进行评价，报价合理得4-5分。", "5")),
                merged, SECTION_TEXT);
        assertThat(outcome.accepted()).hasSize(1);
        assertThat(outcome.accepted().get(0).detail()).contains("不平衡报价评审");
        assertThat(outcome.hallucinationDropped()).isEqualTo(1);
    }

    @Test
    void guard_highConfidencePoolItemMissingFromMerge_restored() {
        // LLM 归并漏掉了"上架商品价格管控方案"（有原文背书 + weight 非空）→ 回补
        List<ScoreCandidate> merged = List.of(
                new ScoreCandidate("", "价格部分",
                        "不平衡报价评审：评标委员会针对各投标人分项报价中的单项折扣率进行评价，报价合理得4-5分。",
                        new BigDecimal("5"), null, null, "原文", "P47", null));
        List<ScoreCandidate> pool = List.of(
                poolItem("不平衡报价评审：评标委员会针对各投标人分项报价中的单项折扣率进行评价，报价合理得4-5分。", "5"),
                poolItem("投标人提供上架商品价格管控方案：重点阐述商品定价策略。优秀得4分。", "4"),
                poolItem("无背书低置信项", null));
        ScoreMergeResultGuard.Outcome outcome = ScoreMergeResultGuard.guard(pool, merged, SECTION_TEXT);
        assertThat(outcome.accepted()).hasSize(2);
        assertThat(outcome.accepted().get(1).detail()).contains("上架商品价格管控方案");
        assertThat(outcome.restoredCount()).isEqualTo(1);
    }

    @Test
    void guard_mergedDetailSupersetOfPoolItem_coversIt() {
        // 归并输出 detail 是池项 detail 的超集（更完整版本）→ 视为已覆盖，不回补
        List<ScoreCandidate> merged = List.of(
                new ScoreCandidate("", "技术方案",
                        "技术方案：方案完整可行得10分。注：投标人须自行提供相关承诺。",
                        new BigDecimal("10"), null, null, "原文", "P47", null));
        ScoreMergeResultGuard.Outcome outcome = ScoreMergeResultGuard.guard(
                List.of(poolItem("技术方案：方案完整可行得10分。", "10")), merged, SECTION_TEXT);
        assertThat(outcome.accepted()).hasSize(1);
        assertThat(outcome.restoredCount()).isZero();
    }

    @Test
    void guard_nullOrEmptyInputs_handled() {
        assertThat(ScoreMergeResultGuard.guard(null, List.of(), SECTION_TEXT).accepted()).isEmpty();
        assertThat(ScoreMergeResultGuard.guard(List.of(poolItem("x", "1"), poolItem("y", "2")), null, SECTION_TEXT)
                .accepted()).isEmpty();
        assertThat(ScoreMergeResultGuard.guard(List.of(poolItem("x", "1")), List.of(), null).accepted()).isEmpty();
    }
}
