package com.xiyu.bid.bidmatch.domain;

import java.math.BigDecimal;

public record BidMatchRuleScore(
        String code,
        String name,
        BidMatchRuleType type,
        String evidenceKey,
        int weight,
        boolean matched,
        MatchRuleEvaluationStatus status,
        BigDecimal score
) {
}
