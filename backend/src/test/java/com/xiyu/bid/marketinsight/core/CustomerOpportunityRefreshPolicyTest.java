package com.xiyu.bid.marketinsight.core;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerOpportunityRefreshPolicyTest {

    @Test
    void evaluate_shouldProduceDeterministicEvaluationUsingExplicitReferenceTime() {
        var result = CustomerOpportunityRefreshPolicy.evaluate(
                "hash-1",
                List.of(
                        new CustomerOpportunityTenderSnapshot(
                                11L,
                                "国网江苏省电力办公设备采购项目",
                                "国网江苏省电力",
                                "hash-1",
                                "能源电力",
                                new BigDecimal("1000000.00"),
                                LocalDateTime.of(2026, 1, 5, 9, 0)),
                        new CustomerOpportunityTenderSnapshot(
                                12L,
                                "国网江苏省电力办公设备采购项目（二次）",
                                "国网江苏省电力",
                                "hash-1",
                                "能源电力",
                                new BigDecimal("500000.00"),
                                LocalDateTime.of(2026, 6, 10, 9, 0))),
                LocalDateTime.of(2026, 4, 21, 10, 0));

        assertThat(result).hasValueSatisfying(evaluation -> {
            assertThat(evaluation.purchaserHash()).isEqualTo("hash-1");
            assertThat(evaluation.purchaserName()).isEqualTo("国网江苏省电力");
            assertThat(evaluation.opportunityScore()).isBetween(0, 100);
            assertThat(evaluation.predictedWindow()).isEqualTo("2026-06");
            assertThat(evaluation.predictedBudgetMin()).isEqualByComparingTo("600000.000");
            assertThat(evaluation.predictedBudgetMax()).isEqualByComparingTo("900000.000");
            assertThat(evaluation.evidenceRecordIds()).isEqualTo("11,12");
            assertThat(evaluation.periodMonths()).isEqualTo("1,6");
        });
    }

    @Test
    void evaluate_withoutCreatedAt_shouldFallBackToIrregularLowRecencyScore() {
        var result = CustomerOpportunityRefreshPolicy.evaluate(
                "hash-2",
                List.of(new CustomerOpportunityTenderSnapshot(
                        21L,
                        "某医院检验设备采购项目",
                        "某医院",
                        "hash-2",
                        "医疗卫生",
                        null,
                        null)),
                LocalDateTime.of(2026, 4, 21, 10, 0));

        assertThat(result).hasValueSatisfying(evaluation -> {
            assertThat(evaluation.avgBudget()).isEqualByComparingTo("0.00");
            assertThat(evaluation.cycleType()).isEqualTo("不定期采购");
            assertThat(evaluation.predictedWindow()).isEqualTo("2026-01");
            assertThat(evaluation.opportunityScore()).isLessThan(40);
        });
    }

    @Test
    void evaluate_withInvalidInputs_shouldReturnEmptyInsteadOfThrowing() {
        assertThat(CustomerOpportunityRefreshPolicy.evaluate("", List.of(), LocalDateTime.now())).isEmpty();
        assertThat(CustomerOpportunityRefreshPolicy.evaluate("hash", null, LocalDateTime.now())).isEmpty();
        assertThat(CustomerOpportunityRefreshPolicy.evaluate("hash", List.of(), null)).isEmpty();
    }
}
