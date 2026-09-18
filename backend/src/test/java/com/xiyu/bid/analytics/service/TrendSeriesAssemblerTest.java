// Input: 维度名 / 比率分子分母
// Output: 断言 CO-615 口径排除项判定与比率舍入（1 位小数、0 除保护）
// Pos: Test/趋势序列组装纯函数单测（排除项单一事实源锚定）
package com.xiyu.bid.analytics.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CO-615 趋势组装：口径排除项与比率计算")
class TrendSeriesAssemblerTest {

    @Test
    @DisplayName("排除项：竞品公司/项目状态不使用 outcome 分母，其余维度使用")
    void outcomeDenominatorExclusions() {
        assertThat(TrendSeriesAssembler.usesOutcomeDenominator("competitor")).isFalse();
        assertThat(TrendSeriesAssembler.usesOutcomeDenominator("projectStatus")).isFalse();
        assertThat(TrendSeriesAssembler.usesOutcomeDenominator("time")).isTrue();
        assertThat(TrendSeriesAssembler.usesOutcomeDenominator("dept")).isTrue();
        assertThat(TrendSeriesAssembler.usesOutcomeDenominator("customerType")).isTrue();
    }

    @Test
    @DisplayName("比率：1 位小数，0 除保护返回 0.0")
    void ratioRoundsAndGuardsZero() {
        assertThat(TrendSeriesAssembler.ratio(1, 3)).isEqualTo(33.3);
        assertThat(TrendSeriesAssembler.ratio(2, 2)).isEqualTo(100.0);
        assertThat(TrendSeriesAssembler.ratio(0, 0)).isEqualTo(0.0);
        assertThat(TrendSeriesAssembler.ratio(5, 0)).isEqualTo(0.0);
    }
}
