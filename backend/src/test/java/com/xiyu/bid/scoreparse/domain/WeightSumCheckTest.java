// Input: WeightSumCheck（check 方法）
// Output: 权重合计闭环校验行为验证（spec 041 FR-005 / FR-022）
// Pos: Test/scoreparse/domain

package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WeightSumCheckTest {

    private final WeightSumCheck check = new WeightSumCheck();

    @Test
    void check_sumEquals100_noWarning() {
        WeightSumCheck.Result result = check.check(List.of(
                new BigDecimal("60"), new BigDecimal("40")
        ));
        assertThat(result.totalWeight()).isEqualByComparingTo("100");
        assertThat(result.weightWarning()).isFalse();
        assertThat(result.needRecheck()).isFalse();
    }

    @Test
    void check_sumNot100_weightWarningAndRecheck() {
        // 合计≠100：标记 weightWarning（FR-022）+ 触发二次解析/回补标记（FR-005）
        WeightSumCheck.Result result = check.check(List.of(
                new BigDecimal("60"), new BigDecimal("30")
        ));
        assertThat(result.totalWeight()).isEqualByComparingTo("90");
        assertThat(result.weightWarning()).isTrue();
        assertThat(result.needRecheck()).isTrue();
    }

    @Test
    void check_sumExceeds100_warning() {
        WeightSumCheck.Result result = check.check(List.of(
                new BigDecimal("70"), new BigDecimal("50")
        ));
        assertThat(result.totalWeight()).isEqualByComparingTo("120");
        assertThat(result.weightWarning()).isTrue();
    }

    @Test
    void check_nullWeights_ignoredInSum() {
        // 缺失权重的候选由 Validation 层丢弃；此处 null 不参与合计
        WeightSumCheck.Result result = check.check(java.util.Arrays.asList(
                new BigDecimal("60"), null, new BigDecimal("40")
        ));
        assertThat(result.totalWeight()).isEqualByComparingTo("100");
        assertThat(result.weightWarning()).isFalse();
    }

    @Test
    void check_emptyList_zeroSum_warning() {
        WeightSumCheck.Result result = check.check(List.of());
        assertThat(result.totalWeight()).isEqualByComparingTo("0");
        assertThat(result.weightWarning()).isTrue();
    }

    @Test
    void check_tolerancePointFive_noWarning() {
        // 容差 ±0.5 分：四舍五入误差不告警
        WeightSumCheck.Result result = check.check(List.of(
                new BigDecimal("33.3"), new BigDecimal("33.3"), new BigDecimal("33.4")
        ));
        assertThat(result.weightWarning()).isFalse();
    }
}
