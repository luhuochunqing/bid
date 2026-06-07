package com.xiyu.bid.bidmatch.domain;

import java.math.BigDecimal;
import java.util.List;

public record BidMatchRule(
        String code,
        String name,
        BidMatchRuleType type,
        String evidenceKey,
        List<String> keywords,
        BigDecimal minValue,
        BigDecimal maxValue,
        int weight,
        boolean enabled
) {

    public BidMatchRule {
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
    }

    public static BidMatchRule keywordAny(
            String code,
            String name,
            String evidenceKey,
            List<String> keywords,
            int weight
    ) {
        return new BidMatchRule(
                code,
                name,
                BidMatchRuleType.KEYWORD,
                evidenceKey,
                keywords,
                null,
                null,
                weight,
                true
        );
    }

    public static BidMatchRule exists(String code, String name, String evidenceKey, int weight) {
        return new BidMatchRule(
                code,
                name,
                BidMatchRuleType.EXISTS,
                evidenceKey,
                List.of(),
                null,
                null,
                weight,
                true
        );
    }

    public static BidMatchRule quantityAtLeast(
            String code,
            String name,
            String evidenceKey,
            BigDecimal minValue,
            int weight
    ) {
        return new BidMatchRule(
                code,
                name,
                BidMatchRuleType.QUANTITY,
                evidenceKey,
                List.of(),
                minValue,
                null,
                weight,
                true
        );
    }

    public static BidMatchRule range(
            String code,
            String name,
            String evidenceKey,
            BigDecimal minValue,
            BigDecimal maxValue,
            int weight
    ) {
        return new BidMatchRule(
                code,
                name,
                BidMatchRuleType.RANGE,
                evidenceKey,
                List.of(),
                minValue,
                maxValue,
                weight,
                true
        );
    }
}
