package com.xiyu.bid.scoreparse.domain;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class SimpleEstimateCountTest {
    @Test void explicitResourceAndUnitScoreCountsAreRecognized() {
        assertCount("需提供2个仓库", "WAREHOUSE", 2, true);
        assertCount("提供三项证书", "CERT", 3, true);
        assertCount("需提供2名人员", "PERSON", 2, true);
        assertCount("要求3份业绩合同", "PROJECT", 3, true);
        assertCount("每份品牌授权1分，最多20分", "BRAND", 20, true);
        assertCount("每提供1份合同得0.5分，最高得10分", "PROJECT", 20, true);
        assertCount("每增加一个满足资格条件的业绩（2023年起至报价截止）加20分，满分100分", "PROJECT", 5, true);
    }
    @Test void DatesAmountsAndConflictingOrInvalidCountsDefaultToPresence() {
        assertCount("2025年营收15000万元，提供仓库", "WAREHOUSE", 1, false);
        assertCount("需提供2个仓库；需提供3个仓库", "WAREHOUSE", 1, false);
        assertCount("需提供3个仓库，每个仓库5分，满分10分", "WAREHOUSE", 1, false);
        assertCount("需提供2个仓库，每个仓库得1分，最高10分", "WAREHOUSE", 1, false);
        assertCount("需提供99999999999999999个仓库", "WAREHOUSE", 1, false);
        assertCount("需提供0个仓库", "WAREHOUSE", 1, false);
        assertCount(null, "WAREHOUSE", 1, false);
    }
    private void assertCount(String detail,String category,int count,boolean explicit) {
        assertThat(ScoreItemConstraintExtractor.requiredForEstimate(detail,category,new BigDecimal("20")))
                .isEqualTo(new ScoreItemConstraintExtractor.EstimateCount(count,explicit));
    }
}
