// Input: ScoringCriterion 结构化评分标准单元测试
// Output: 验证编号、维度、指标、权重和总分计算
// Pos: Test/biddraftagent/domain

package com.xiyu.bid.biddraftagent.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScoringCriterionTest {

    @Test
    void calculateTotalScore_sumsWeights() {
        var c1 = new ScoringCriterion("1", "价格评分", "投标报价得分", new BigDecimal("30"), ScoringCriteriaSubType.PRICE_WEIGHT);
        var c2 = new ScoringCriterion("2", "技术方案", "技术方案完整性", new BigDecimal("40"), ScoringCriteriaSubType.TECHNICAL_EVALUATION);
        var c3 = new ScoringCriterion("3", "售后服务", "售后服务体系", new BigDecimal("30"), ScoringCriteriaSubType.SERVICE_EVALUATION);

        BigDecimal total = ScoringCriterion.calculateTotalScore(List.of(c1, c2, c3));

        assertThat(total).isEqualByComparingTo(new BigDecimal("100"));
    }

    @Test
    void calculateTotalScore_empty_returnsZero() {
        assertThat(ScoringCriterion.calculateTotalScore(List.of())).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void calculateTotalScore_null_returnsZero() {
        assertThat(ScoringCriterion.calculateTotalScore(null)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void calculateTotalScore_ignoresNullWeights() {
        var c1 = new ScoringCriterion("1", "价格", "报价", null, ScoringCriteriaSubType.PRICE_WEIGHT);
        var c2 = new ScoringCriterion("2", "技术", "方案", new BigDecimal("60"), ScoringCriteriaSubType.TECHNICAL_EVALUATION);

        BigDecimal total = ScoringCriterion.calculateTotalScore(List.of(c1, c2));

        assertThat(total).isEqualByComparingTo(new BigDecimal("60"));
    }

    @Test
    void record_accessors() {
        var criterion = new ScoringCriterion("1", "价格评分", "投标报价得分", new BigDecimal("30"), ScoringCriteriaSubType.PRICE_WEIGHT);

        assertThat(criterion.itemNumber()).isEqualTo("1");
        assertThat(criterion.dimension()).isEqualTo("价格评分");
        assertThat(criterion.indicator()).isEqualTo("投标报价得分");
        assertThat(criterion.weight()).isEqualByComparingTo(new BigDecimal("30"));
        assertThat(criterion.subType()).isEqualTo(ScoringCriteriaSubType.PRICE_WEIGHT);
    }
}
