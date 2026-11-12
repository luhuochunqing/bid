package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class PartialScorePolicyTest {
    private final PartialScorePolicy policy = new PartialScorePolicy();

    @Test void simpleEstimateRetainsHalfAPointInsteadOfClampingToOne() {
        assertThat(policy.compute(new BigDecimal("1"), "PARTIAL", 50, "OBJECTIVE")).isEqualByComparingTo("0.50");
    }

    @Test void countRatioRoundsOnlyTheFinalScoreAndCapsAtWeight() throws Exception {
        try (var in=getClass().getResourceAsStream("/scoreparse-golden/simple-estimate-v1.json")) {
            assertThat(in).isNotNull();
            var cases=new com.fasterxml.jackson.databind.ObjectMapper().readTree(in).path("cases");
            for (var c:cases) assertThat(policy.estimateByCount(new BigDecimal(c.path("weight").asText()),
                    c.path("matched").asInt(),c.path("required").asInt())).as(c.toString())
                    .isEqualByComparingTo(c.path("score").asText());
        }
    }

    @Test void percentageCompatibilityUsesTwoDecimalsWithoutTheOldOnePointClamp() {
        assertThat(policy.compute(new BigDecimal("7"),"PARTIAL",50,"OBJECTIVE")).isEqualByComparingTo("3.50");
        assertThat(policy.compute(new BigDecimal("2"),"PARTIAL",1,"OBJECTIVE")).isEqualByComparingTo("0.02");
        assertThat(policy.compute(new BigDecimal("5"),"PARTIAL",99,"OBJECTIVE")).isEqualByComparingTo("4.95");
        assertThat(policy.compute(BigDecimal.TEN,"NONE",0,"OBJECTIVE")).isZero();
        assertThat(policy.compute(BigDecimal.TEN,"FULL",100,"SUBJECTIVE")).isNull();
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
