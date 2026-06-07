package com.xiyu.bid.bidmatch.domain;

import java.math.BigDecimal;
import java.util.List;

public record BidMatchDimensionScore(
        String code,
        String name,
        int weight,
        BigDecimal score,
        List<BidMatchRuleScore> ruleScores
) {

    public BidMatchDimensionScore {
        ruleScores = ruleScores == null ? List.of() : List.copyOf(ruleScores);
    }
}
