package com.xiyu.bid.marketinsight.core;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpportunityScoringPolicyTest {

    // --- computeScore ---

    @Test
    void computeScore_HighValues_ShouldProduceHighScore() {
        var score = OpportunityScoringPolicy.computeScore(10, 1, 5000L, true);
        assertThat(score.total()).isGreaterThanOrEqualTo(70);
        assertThat(score.frequencyComponent()).isGreaterThan(0);
        assertThat(score.recencyComponent()).isGreaterThan(0);
        assertThat(score.budgetComponent()).isGreaterThan(0);
        assertThat(score.cycleComponent()).isGreaterThan(0);
    }

    @Test
    void computeScore_ZeroFrequency_ShouldProduceLowScore() {
        var score = OpportunityScoringPolicy.computeScore(0, 12, 100L, false);
        assertThat(score.total()).isLessThan(50);
    }

    @Test
    void computeScore_TotalClampedTo100() {
        // freq=20 -> 100, recency=0 -> 100, budget=10000 -> 100, cycle=true -> 80
        var score = OpportunityScoringPolicy.computeScore(20, 0, 10000L, true);
        assertThat(score.total()).isLessThanOrEqualTo(100);
    }

    @Test
    void computeScore_TotalClampedTo0() {
        // All low values
        var score = OpportunityScoringPolicy.computeScore(0, 20, 0L, false);
        assertThat(score.total()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void computeScore_FrequencyComponentCappedAt100() {
        var score = OpportunityScoringPolicy.computeScore(20, 1, 500L, true);
        assertThat(score.frequencyComponent()).isLessThanOrEqualTo(30.0); // 100 * 0.30
    }

    @Test
    void computeScore_RecencyDecreasesOverTime() {
        var recent = OpportunityScoringPolicy.computeScore(5, 1, 500L, true);
        var old = OpportunityScoringPolicy.computeScore(5, 12, 500L, true);
        assertThat(recent.recencyComponent()).isGreaterThan(old.recencyComponent());
    }

    @Test
    void computeScore_CycleBoostsScore() {
        var withCycle = OpportunityScoringPolicy.computeScore(5, 3, 500L, true);
        var withoutCycle = OpportunityScoringPolicy.computeScore(5, 3, 500L, false);
        assertThat(withCycle.total()).isGreaterThan(withoutCycle.total());
    }

    @Test
    void computeScore_BudgetScoreCappedAt100() {
        var score = OpportunityScoringPolicy.computeScore(5, 1, 999999L, true);
        assertThat(score.budgetComponent()).isLessThanOrEqualTo(25.0); // 100 * 0.25
    }

    // --- predictNextWindow ---

    @Test
    void predictNextWindow_ShouldFindNextMonthAfterCurrent() {
        var monthCounts = Map.of(3, 5, 6, 8, 9, 10, 12, 3);
        var result = OpportunityScoringPolicy.predictNextWindow(monthCounts, 4, 2026);
        // Earliest month after 4 is month 6
        assertThat(result.predictedMonth()).isEqualTo(6);
        assertThat(result.windowLabel()).isEqualTo("2026-06");
    }

    @Test
    void predictNextWindow_ShouldWrapToNextYear() {
        var monthCounts = Map.of(3, 5, 6, 8);
        var result = OpportunityScoringPolicy.predictNextWindow(monthCounts, 10, 2026);
        // No months after 10, so wrap to next year's earliest month = 3
        assertThat(result.predictedMonth()).isEqualTo(3);
        assertThat(result.windowLabel()).isEqualTo("2027-03");
    }

    @Test
    void predictNextWindow_ConfidenceShouldBeClamped() {
        var monthCounts = Map.of(3, 5, 6, 8);
        var result = OpportunityScoringPolicy.predictNextWindow(monthCounts, 4, 2026);
        assertThat(result.confidence()).isBetween(0.0, 1.0);
    }

    @Test
    void predictNextWindow_EmptyMap_ShouldReturnDefault() {
        var result = OpportunityScoringPolicy.predictNextWindow(
                Map.of(), 6, 2026);
        assertThat(result.predictedMonth()).isGreaterThan(0);
        assertThat(result.windowLabel()).isEqualTo("2026-01");
    }

    // --- classifyCycleType ---

    @Test
    void classifyCycleType_SinglePeak_ShouldReturnAnnual() {
        var monthCounts = Map.of(3, 10, 6, 1, 9, 2, 12, 1);
        var result = OpportunityScoringPolicy.classifyCycleType(monthCounts);
        assertThat(result).isEqualTo("年度集中采购");
    }

    @Test
    void classifyCycleType_QuarterlyPeaks_ShouldReturnQuarterly() {
        var monthCounts = Map.of(3, 8, 6, 7, 9, 8, 12, 7);
        var result = OpportunityScoringPolicy.classifyCycleType(monthCounts);
        assertThat(result).isEqualTo("季度规律采购");
    }

    @Test
    void classifyCycleType_Irregular_ShouldReturnIrregular() {
        var monthCounts = Map.of(1, 2, 4, 1, 7, 3, 11, 2);
        var result = OpportunityScoringPolicy.classifyCycleType(monthCounts);
        assertThat(result).isEqualTo("不定期采购");
    }

    // --- generateReasoningSummary ---

    @Test
    void generateReasoningSummary_ShouldUseTemplate() {
        var summary = OpportunityScoringPolicy.generateReasoningSummary(
                "中国石化集团", "能源电力", 12, "工具", 0.85);
        assertThat(summary).contains("中国石化集团");
        assertThat(summary).contains("能源电力");
        assertThat(summary).contains("工具");
        assertThat(summary).contains("85%");
    }

    @Test
    void generateReasoningSummary_LowFrequency_ShouldDescribeCorrectly() {
        var summary = OpportunityScoringPolicy.generateReasoningSummary(
                "某公司", "办公", 1, "办公", 0.5);
        assertThat(summary).contains("某公司");
        assertThat(summary).contains("50%");
    }
}
