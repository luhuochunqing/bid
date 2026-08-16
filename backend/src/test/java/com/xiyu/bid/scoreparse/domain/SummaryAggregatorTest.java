package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 阶段 1 汇总统计测试（spec 041 FR-017 / FR-022 / SC-003）。
 * <p>合计仅客观项；主观项数字得分零泄漏；权重合计 ≠ 100（容差 ±0.5）→ weightWarning。
 */
class SummaryAggregatorTest {

    private final SummaryAggregator aggregator = new SummaryAggregator();

    @Test
    @DisplayName("混合清单汇总：合计排除主观项 + 状态计数 + 权重分组（US3 场景 6）")
    void aggregate_mixedItems() {
        SummaryAggregator.Result result = aggregator.aggregate(List.of(
                new SummaryAggregator.Item(new BigDecimal("40"), "OBJECTIVE", new BigDecimal("40"), "OK"),
                new SummaryAggregator.Item(new BigDecimal("30"), "OBJECTIVE", new BigDecimal("4"), "PENDING"),
                new SummaryAggregator.Item(new BigDecimal("20"), "OBJECTIVE", BigDecimal.ZERO, "DANGER"),
                new SummaryAggregator.Item(new BigDecimal("10"), "SUBJECTIVE", null, "PENDING")));

        assertThat(result.totalWeight()).isEqualByComparingTo("100");
        assertThat(result.totalEstScore()).isEqualByComparingTo("44");
        assertThat(result.okCount()).isEqualTo(1);
        assertThat(result.dangerCount()).isEqualTo(1);
        assertThat(result.pendingCount()).isEqualTo(2);
        assertThat(result.objectiveWeight()).isEqualByComparingTo("90");
        assertThat(result.subjectiveWeight()).isEqualByComparingTo("10");
        assertThat(result.weightWarning()).isFalse();
    }

    @Test
    @DisplayName("主观项脏数据得分不计入合计（SC-003 零泄漏防线）")
    void subjectiveDirtyScore_notCountedIntoTotal() {
        SummaryAggregator.Result result = aggregator.aggregate(List.of(
                new SummaryAggregator.Item(new BigDecimal("10"), "OBJECTIVE", new BigDecimal("6"), "PENDING"),
                // 模拟上游 bug 泄漏的主观项数字得分：汇总层必须丢弃
                new SummaryAggregator.Item(new BigDecimal("5"), "SUBJECTIVE", new BigDecimal("5"), "PENDING")));

        assertThat(result.totalEstScore()).isEqualByComparingTo("6");
        assertThat(result.subjectiveWeight()).isEqualByComparingTo("5");
    }

    @Test
    @DisplayName("权重合计 = 100 → 无警示（FR-022）")
    void weightHundred_noWarning() {
        SummaryAggregator.Result result = aggregator.aggregate(List.of(
                new SummaryAggregator.Item(new BigDecimal("60"), "OBJECTIVE", null, "PENDING"),
                new SummaryAggregator.Item(new BigDecimal("40"), "SUBJECTIVE", null, "PENDING")));
        assertThat(result.weightWarning()).isFalse();
    }

    @Test
    @DisplayName("权重合计 100.3 → 容差内无警示（±0.5）")
    void weightWithinTolerance_noWarning() {
        SummaryAggregator.Result result = aggregator.aggregate(List.of(
                new SummaryAggregator.Item(new BigDecimal("100.3"), "OBJECTIVE", null, "PENDING")));
        assertThat(result.weightWarning()).isFalse();
    }

    @Test
    @DisplayName("权重合计 95 → 警示（FR-022 不阻断，前端展示实际总分）")
    void weightNotHundred_warning() {
        SummaryAggregator.Result result = aggregator.aggregate(List.of(
                new SummaryAggregator.Item(new BigDecimal("95"), "OBJECTIVE", null, "PENDING")));
        assertThat(result.weightWarning()).isTrue();
        assertThat(result.totalWeight()).isEqualByComparingTo("95");
    }

    @Test
    @DisplayName("空清单 → 全零汇总不抛错（FR-024）")
    void emptyItems_zeroSummary() {
        SummaryAggregator.Result result = aggregator.aggregate(List.of());

        assertThat(result.totalWeight()).isEqualByComparingTo("0");
        assertThat(result.totalEstScore()).isEqualByComparingTo("0");
        assertThat(result.okCount()).isZero();
        assertThat(result.dangerCount()).isZero();
        assertThat(result.pendingCount()).isZero();
        assertThat(result.weightWarning()).isTrue();
    }

    @Test
    @DisplayName("null 得分客观项不计入合计但不抛错")
    void nullEstScore_objectiveItem_skipped() {
        SummaryAggregator.Result result = aggregator.aggregate(List.of(
                new SummaryAggregator.Item(new BigDecimal("8"), "OBJECTIVE", null, "PENDING")));

        assertThat(result.totalEstScore()).isEqualByComparingTo("0");
        assertThat(result.totalWeight()).isEqualByComparingTo("8");
    }
}
