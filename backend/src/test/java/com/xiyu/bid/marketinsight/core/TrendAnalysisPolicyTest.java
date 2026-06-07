package com.xiyu.bid.marketinsight.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TrendAnalysisPolicyTest {

    // --- computeTrend ---

    @Test
    void computeTrend_PositiveGrowth_ShouldReturnUp() {
        var result = TrendAnalysisPolicy.computeTrend(
                "办公", 120, 80, 500000L);
        assertThat(result.trend()).isEqualTo("up");
        assertThat(result.growth()).isGreaterThan(0);
    }

    @Test
    void computeTrend_NegativeGrowth_ShouldReturnDown() {
        var result = TrendAnalysisPolicy.computeTrend(
                "办公", 50, 80, 500000L);
        assertThat(result.trend()).isEqualTo("down");
    }

    @Test
    void computeTrend_SmallChange_ShouldReturnStable() {
        // 80 -> 82 = 2.5% growth, within [-10, 10]
        var result = TrendAnalysisPolicy.computeTrend(
                "办公", 82, 80, 500000L);
        assertThat(result.trend()).isEqualTo("stable");
    }

    @Test
    void computeTrend_ZeroPrevious_ShouldReturn100Growth() {
        var result = TrendAnalysisPolicy.computeTrend(
                "办公", 10, 0, 50000L);
        assertThat(result.growth()).isEqualTo(100);
    }

    @Test
    void computeTrend_BothZero_ShouldReturn0Growth() {
        var result = TrendAnalysisPolicy.computeTrend(
                "办公", 0, 0, 0L);
        assertThat(result.growth()).isEqualTo(0);
        assertThat(result.trend()).isEqualTo("stable");
    }

    @Test
    void computeTrend_HotLevel_ShouldCapAt5() {
        // count=300 -> +3, growth>30 -> +2, base=1, total=6, capped at 5
        var result = TrendAnalysisPolicy.computeTrend(
                "办公", 300, 100, 5000000L);
        assertThat(result.hotLevel()).isLessThanOrEqualTo(5);
    }

    @Test
    void computeTrend_HotLevel_BaseIs1() {
        // count=40 (no bonus: <50), growth=0 (stable, no bonus), base=1
        var result = TrendAnalysisPolicy.computeTrend(
                "办公", 40, 40, 50000L);
        assertThat(result.hotLevel()).isEqualTo(1);
    }

    @Test
    void computeTrend_HotLevel_Count50Plus1() {
        // count=55 -> +1, base=1, total=2
        var result = TrendAnalysisPolicy.computeTrend(
                "办公", 55, 55, 50000L);
        assertThat(result.hotLevel()).isEqualTo(2);
    }

    @Test
    void computeTrend_ShouldPopulateFields() {
        var result = TrendAnalysisPolicy.computeTrend(
                "能源电力", 100, 80, 2000000L);
        assertThat(result.industry()).isEqualTo("能源电力");
        assertThat(result.count()).isEqualTo(100);
        assertThat(result.amount()).isEqualTo(2000000L);
    }

    // --- computePurchaserPattern ---

    @Test
    void computePurchaserPattern_ShouldExtractNameFromTitle() {
        var tenders = List.of(
                new TrendAnalysisPolicy.PurchaserTenderRecord(
                        "中国石化集团2024年采购项目", 100L, 2024, 3),
                new TrendAnalysisPolicy.PurchaserTenderRecord(
                        "中国石化集团2025年采购项目", 200L, 2025, 6));
        var result = TrendAnalysisPolicy.computePurchaserPattern(tenders);
        assertThat(result.name()).isEqualTo("中国石化集团");
    }

    @Test
    void computePurchaserPattern_ShouldComputeFrequency() {
        var tenders = List.of(
                new TrendAnalysisPolicy.PurchaserTenderRecord(
                        "某公司采购一", 100L, 2024, 3),
                new TrendAnalysisPolicy.PurchaserTenderRecord(
                        "某公司采购二", 200L, 2024, 6),
                new TrendAnalysisPolicy.PurchaserTenderRecord(
                        "某公司采购三", 150L, 2024, 9));
        var result = TrendAnalysisPolicy.computePurchaserPattern(tenders);
        assertThat(result.frequency()).isEqualTo(3);
    }

    @Test
    void computePurchaserPattern_ShouldExtractTop3Months() {
        var tenders = List.of(
                new TrendAnalysisPolicy.PurchaserTenderRecord(
                        "某公司采购", 100L, 2024, 3),
                new TrendAnalysisPolicy.PurchaserTenderRecord(
                        "某公司采购", 100L, 2024, 3),
                new TrendAnalysisPolicy.PurchaserTenderRecord(
                        "某公司采购", 100L, 2024, 6),
                new TrendAnalysisPolicy.PurchaserTenderRecord(
                        "某公司采购", 100L, 2024, 9),
                new TrendAnalysisPolicy.PurchaserTenderRecord(
                        "某公司采购", 100L, 2024, 12));
        var result = TrendAnalysisPolicy.computePurchaserPattern(tenders);
        // 3月 appears twice, others once. Top 3 months by frequency.
        assertThat(result.period()).contains("3月");
    }

    @Test
    void computePurchaserPattern_ShouldComputeAvgBudget() {
        var tenders = List.of(
                new TrendAnalysisPolicy.PurchaserTenderRecord(
                        "某公司采购", 100L, 2024, 3),
                new TrendAnalysisPolicy.PurchaserTenderRecord(
                        "某公司采购", 200L, 2024, 6));
        var result = TrendAnalysisPolicy.computePurchaserPattern(tenders);
        assertThat(result.avgBudget()).isEqualTo(150L);
    }

    @Test
    void computePurchaserPattern_OpportunityScore_Frequency12OrMore() {
        var tenders = java.util.stream.IntStream.rangeClosed(1, 12)
                .mapToObj(i -> new TrendAnalysisPolicy.PurchaserTenderRecord(
                        "某公司采购" + i, 100L, 2024, i % 12 + 1))
                .toList();
        var result = TrendAnalysisPolicy.computePurchaserPattern(tenders);
        assertThat(result.opportunity()).isEqualTo(5);
    }

    @Test
    void computePurchaserPattern_OpportunityScore_Frequency3To5() {
        var tenders = List.of(
                new TrendAnalysisPolicy.PurchaserTenderRecord(
                        "某公司采购", 100L, 2024, 3),
                new TrendAnalysisPolicy.PurchaserTenderRecord(
                        "某公司采购", 100L, 2024, 6),
                new TrendAnalysisPolicy.PurchaserTenderRecord(
                        "某公司采购", 100L, 2024, 9));
        var result = TrendAnalysisPolicy.computePurchaserPattern(tenders);
        assertThat(result.opportunity()).isEqualTo(3);
    }

    @Test
    void computePurchaserPattern_OpportunityScore_Frequency1() {
        var tenders = List.of(
                new TrendAnalysisPolicy.PurchaserTenderRecord(
                        "某公司采购", 100L, 2024, 3));
        var result = TrendAnalysisPolicy.computePurchaserPattern(tenders);
        assertThat(result.opportunity()).isEqualTo(1);
    }

    // --- generateForecastTips ---

    @Test
    void generateForecastTips_ShouldGenerateForHotOrUp() {
        var trends = List.of(
                new TrendAnalysisPolicy.TrendResult("办公", 80, 500000L, 50L, "up", 3),
                new TrendAnalysisPolicy.TrendResult("能源电力", 50, 300000L, 5L, "stable", 1));
        var tips = TrendAnalysisPolicy.generateForecastTips(trends);
        assertThat(tips).hasSize(1);
        assertThat(tips.get(0).text()).contains("办公");
    }

    @Test
    void generateForecastTips_ShouldLimitTo4() {
        var trends = List.of(
                new TrendAnalysisPolicy.TrendResult("办公", 80, 500L, 50L, "up", 3),
                new TrendAnalysisPolicy.TrendResult("工具", 200, 600L, 60L, "up", 4),
                new TrendAnalysisPolicy.TrendResult("焊接", 150, 400L, 40L, "up", 3),
                new TrendAnalysisPolicy.TrendResult("消防", 100, 300L, 30L, "up", 3),
                new TrendAnalysisPolicy.TrendResult("数据中心", 90, 200L, 20L, "up", 3));
        var tips = TrendAnalysisPolicy.generateForecastTips(trends);
        assertThat(tips).hasSize(4);
    }

    @Test
    void generateForecastTips_UpColor_ShouldBeGreen() {
        var trends = List.of(
                new TrendAnalysisPolicy.TrendResult("办公", 80, 500L, 50L, "up", 3));
        var tips = TrendAnalysisPolicy.generateForecastTips(trends);
        assertThat(tips.get(0).color()).isEqualTo("#67c23a");
    }

    @Test
    void generateForecastTips_StableColor_ShouldBeOrange() {
        var trends = List.of(
                new TrendAnalysisPolicy.TrendResult("办公", 80, 500L, 5L, "stable", 3));
        var tips = TrendAnalysisPolicy.generateForecastTips(trends);
        assertThat(tips.get(0).color()).isEqualTo("#e6a23c");
    }

    @Test
    void generateForecastTips_DownColor_ShouldBeRed() {
        var trends = List.of(
                new TrendAnalysisPolicy.TrendResult("办公", 50, 500L, -20L, "down", 3));
        var tips = TrendAnalysisPolicy.generateForecastTips(trends);
        assertThat(tips.get(0).color()).isEqualTo("#f56c6c");
    }

    @Test
    void generateForecastTips_ShouldSkipColdAndStable() {
        var trends = List.of(
                new TrendAnalysisPolicy.TrendResult("办公", 50, 500L, 5L, "stable", 1));
        var tips = TrendAnalysisPolicy.generateForecastTips(trends);
        assertThat(tips).isEmpty();
    }
}
