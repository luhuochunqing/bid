package com.xiyu.bid.bidmatch.dto;

import java.math.BigDecimal;
import java.util.List;

public record BidMatchModelResponse(
        Long id,
        String name,
        String description,
        String status,
        long draftRevision,
        Long activeVersionId,
        Integer activeVersionNo,
        List<DimensionResponse> dimensions,
        List<String> validationErrors
) {

    public record DimensionResponse(
            String code,
            String name,
            int weight,
            boolean enabled,
            List<RuleResponse> rules
    ) {
    }

    public record RuleResponse(
            String code,
            String name,
            String type,
            String evidenceKey,
            List<String> keywords,
            BigDecimal minValue,
            BigDecimal maxValue,
            int weight,
            boolean enabled
    ) {
    }
}
