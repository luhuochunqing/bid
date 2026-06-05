package com.xiyu.bid.bidmatch.domain;

import java.math.BigDecimal;
import java.util.List;

public record BidMatchScoreEvaluation(
        Long modelId,
        int modelVersionNo,
        BigDecimal totalScore,
        List<BidMatchDimensionScore> dimensionScores,
        String evidenceFingerprint
) {

    public BidMatchScoreEvaluation {
        dimensionScores = dimensionScores == null ? List.of() : List.copyOf(dimensionScores);
    }
}
