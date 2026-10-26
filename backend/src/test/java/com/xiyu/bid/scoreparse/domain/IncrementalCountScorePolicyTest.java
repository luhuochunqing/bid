package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class IncrementalCountScorePolicyTest {

    private static final String PROD_CASE_DETAIL = "满足资格条件的业绩得基础分60分，在此基础上每增加一个满足资格条件的业绩（2023年1月1日至首次报价截止日，以合同签订时间为准）加20分，满分100分。 | 5%";
    private static final BigDecimal ITEM_WEIGHT = new BigDecimal("5");

    @Test
    void parse_recognizesBaseScoreAndIncrementRule() {
        var ruleOpt = IncrementalCountScorePolicy.parse(PROD_CASE_DETAIL);
        assertThat(ruleOpt).isPresent();
        var rule = ruleOpt.get();

        assertThat(rule.baseScore()).isEqualByComparingTo("60");
        assertThat(rule.pointsPerIncrement()).isEqualByComparingTo("20");
        assertThat(rule.maximumScore()).isEqualByComparingTo("100");
        assertThat(rule.requiredCountForMaximumScore()).isEqualTo(3);
    }

    @ParameterizedTest(name = "qualifiedCount={0} -> expectedScore={1}")
    @CsvSource({
            "0, 0.00",
            "1, 3.00",
            "2, 4.00",
            "3, 5.00",
            "5, 5.00"
    })
    void score_computesScaledScoreAccordingToItemWeight(int qualifiedCount, String expectedScore) {
        var rule = IncrementalCountScorePolicy.parse(PROD_CASE_DETAIL).orElseThrow();
        BigDecimal score = rule.score(qualifiedCount, ITEM_WEIGHT);
        assertThat(score).isEqualByComparingTo(expectedScore);
    }

    @Test
    void classify_marksProdCaseAsObjective() {
        ScoreTypeClassificationPolicy classifier = new ScoreTypeClassificationPolicy();
        assertThat(classifier.classify(PROD_CASE_DETAIL, "SUBJECTIVE")).isEqualTo("OBJECTIVE");
    }

    @Test
    void extractProjectRequiredCount_returnsThreeForProdCase() {
        Integer required = ScoreItemConstraintExtractor.extractProjectRequiredCount(PROD_CASE_DETAIL, ITEM_WEIGHT);
        assertThat(required).isEqualTo(3);
    }
}
