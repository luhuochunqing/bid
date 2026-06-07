package com.xiyu.bid.bidmatch.domain;

import java.util.List;

public record BidMatchDimension(
        String code,
        String name,
        int weight,
        boolean enabled,
        List<BidMatchRule> rules
) {

    public BidMatchDimension {
        rules = rules == null ? List.of() : List.copyOf(rules);
    }

    public static BidMatchDimension enabled(String code, String name, int weight, List<BidMatchRule> rules) {
        return new BidMatchDimension(code, name, weight, true, rules);
    }

    public static BidMatchDimension disabled(String code, String name, int weight, List<BidMatchRule> rules) {
        return new BidMatchDimension(code, name, weight, false, rules);
    }
}
