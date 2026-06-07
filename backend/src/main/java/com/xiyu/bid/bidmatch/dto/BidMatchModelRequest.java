package com.xiyu.bid.bidmatch.dto;

import java.math.BigDecimal;
import java.util.List;

public record BidMatchModelRequest(
        Long id,
        String name,
        String description,
        List<DimensionRequest> dimensions
) {

    public record DimensionRequest(
            String code,
            String name,
            Integer weight,
            Boolean enabled,
            List<RuleRequest> rules
    ) {
    }

    public record RuleRequest(
            String code,
            String name,
            String type,
            String evidenceKey,
            List<String> keywords,
            BigDecimal minValue,
            BigDecimal maxValue,
            Integer weight,
            Boolean enabled
    ) {
    }
}
