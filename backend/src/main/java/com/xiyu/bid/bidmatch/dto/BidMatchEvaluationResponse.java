package com.xiyu.bid.bidmatch.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record BidMatchEvaluationResponse(
        Long id,
        Long tenderId,
        Long modelId,
        Long modelVersionId,
        int modelVersionNo,
        BigDecimal totalScore,
        boolean stale,
        String evidenceFingerprint,
        String evidenceSnapshotJson,
        String modelSnapshotJson,
        LocalDateTime evaluatedAt,
        List<DimensionScoreResponse> dimensionScores
) {

    public record DimensionScoreResponse(
            String code,
            String name,
            int weight,
            BigDecimal score,
            List<RuleScoreResponse> ruleScores
    ) {
    }

    public record RuleScoreResponse(
            String code,
            String name,
            String type,
            String evidenceKey,
            int weight,
            boolean matched,
            String status,
            BigDecimal score
    ) {
    }
}
