package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class Project22SupplierPerformanceScoringAcceptanceTest {

    private static final BigDecimal WEIGHT = new BigDecimal("7");
    private static final String DETAIL = "供应商具有近三年内（2023年1月1日起至今，供应商具有1项大型集团型国央企电子商城办公用品服务业绩的不得分，"
            + "每增加1个得1分，最高得7分。";

    @ParameterizedTest(name = "qualified={0} -> score={1}")
    @CsvSource({"1,0", "2,1", "8,7", "10,7"})
    void exactProject22Rule_scoresOnlyAdditionalQualifiedPerformances(int qualifiedCount,
                                                                      String expectedScore) {
        String scoreType = new ScoreTypeClassificationPolicy().classify(DETAIL, "SUBJECTIVE");
        BigDecimal actual = IncrementalCountScorePolicy.parse(DETAIL)
                .map(rule -> rule.score(qualifiedCount, WEIGHT, scoreType))
                .orElse(null);

        assertThat(actual).isEqualByComparingTo(expectedScore);
    }

    @org.junit.jupiter.api.Test
    void exactProject22Rule_requiresEightQualifiedPerformancesForFullScore() {
        assertThat(ScoreItemConstraintExtractor.extractProjectRequiredCount(DETAIL, WEIGHT))
                .isEqualTo(8);
    }
}
