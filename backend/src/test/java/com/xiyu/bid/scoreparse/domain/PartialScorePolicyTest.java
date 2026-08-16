package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 阶段 1 部分得分计算策略测试（spec 041 FR-011 / FR-013 / SC-003）。
 * <p>对应 spec US3 场景 2（仓库 2/4 命中权重 8 → 4 分）、场景 3（人员 3/5 权重 10 → 6 分）。
 */
class PartialScorePolicyTest {

    private final PartialScorePolicy policy = new PartialScorePolicy();

    @Test
    @DisplayName("完全匹配 → 权重满分（US3 场景 1）")
    void fullMatch_returnsWeight() {
        BigDecimal score = policy.compute(new BigDecimal("8"), "FULL", 100, "OBJECTIVE");
        assertThat(score).isEqualByComparingTo("8");
    }

    @Test
    @DisplayName("部分匹配 50% × 权重 8 → 4 分（US3 场景 2）")
    void partialMatch_halfOfWeight8_returns4() {
        BigDecimal score = policy.compute(new BigDecimal("8"), "PARTIAL", 50, "OBJECTIVE");
        assertThat(score).isEqualByComparingTo("4");
    }

    @Test
    @DisplayName("部分匹配 60% × 权重 10 → 6 分（US3 场景 3）")
    void partialMatch_threeOfFive_returns6() {
        BigDecimal score = policy.compute(new BigDecimal("10"), "PARTIAL", 60, "OBJECTIVE");
        assertThat(score).isEqualByComparingTo("6");
    }

    @Test
    @DisplayName("部分得分四舍五入取整：7 × 50% = 3.5 → 4")
    void partialScore_roundsHalfUp() {
        BigDecimal score = policy.compute(new BigDecimal("7"), "PARTIAL", 50, "OBJECTIVE");
        assertThat(score).isEqualByComparingTo("4");
    }

    @Test
    @DisplayName("部分得分开区间下界：权重 2 × 1% = 0.02 → 取整 0 → 钳位 1")
    void partialScore_clampedToLowerBound() {
        BigDecimal score = policy.compute(new BigDecimal("2"), "PARTIAL", 1, "OBJECTIVE");
        assertThat(score).isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("部分得分开区间上界：权重 5 × 99% = 4.95 → 取整 5 → 钳位 4")
    void partialScore_clampedToUpperBound() {
        BigDecimal score = policy.compute(new BigDecimal("5"), "PARTIAL", 99, "OBJECTIVE");
        assertThat(score).isEqualByComparingTo("4");
    }

    @Test
    @DisplayName("未匹配 → 0 分（US3 场景 5）")
    void noneMatch_returnsZero() {
        BigDecimal score = policy.compute(new BigDecimal("8"), "NONE", 0, "OBJECTIVE");
        assertThat(score).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("主观项 → null（即使完全匹配也零泄漏，SC-003 / US3 场景 4）")
    void subjective_returnsNull_evenFullMatch() {
        BigDecimal score = policy.compute(new BigDecimal("10"), "FULL", 100, "SUBJECTIVE");
        assertThat(score).isNull();
    }

    @Test
    @DisplayName("主观项部分匹配 → 仍 null（SC-003 主观项数字得分泄漏为 0）")
    void subjective_returnsNull_partialMatch() {
        BigDecimal score = policy.compute(new BigDecimal("10"), "PARTIAL", 60, "SUBJECTIVE");
        assertThat(score).isNull();
    }

    @Test
    @DisplayName("返回值为整数刻度（FR-013 四舍五入取整）")
    void returnedScore_hasIntegerScale() {
        BigDecimal score = policy.compute(new BigDecimal("8.50"), "PARTIAL", 33, "OBJECTIVE");
        assertThat(score.scale()).isLessThanOrEqualTo(0);
    }

    @Test
    @DisplayName("阶段2资质/仓库/品牌计分：60% 比例得 6 分")
    void stage2_certTierScoring_half() {
        BigDecimal score = policy.computeStage2Score(new BigDecimal("10"), KnowledgeCategoryPolicy.CATEGORY_CERT, 60, "OBJECTIVE");
        assertThat(score).isEqualByComparingTo("6");
    }

    @Test
    @DisplayName("阶段2人员按符合比例线性计分：3/5=60% 得 6 分")
    void stage2_personRatioScoring_linear() {
        BigDecimal score = policy.computeStage2Score(new BigDecimal("10"), KnowledgeCategoryPolicy.CATEGORY_PERSON, 60, "OBJECTIVE");
        assertThat(score).isEqualByComparingTo("6");
    }

    @Test
    @DisplayName("阶段2业绩按数量比例计分：80% 得 8 分")
    void stage2_projectRatioScoring_linear() {
        BigDecimal score = policy.computeStage2Score(new BigDecimal("10"), KnowledgeCategoryPolicy.CATEGORY_PROJECT, 80, "OBJECTIVE");
        assertThat(score).isEqualByComparingTo("8");
    }

    @Test
    @DisplayName("阶段2主观项计分强制返回 null（零泄漏）")
    void stage2_subjectiveScoring_returnsNull() {
        BigDecimal score = policy.computeStage2Score(new BigDecimal("10"), KnowledgeCategoryPolicy.CATEGORY_OTHER, 100, "SUBJECTIVE");
        assertThat(score).isNull();
    }
}
