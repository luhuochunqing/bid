package com.xiyu.bid.scoreparse.dto;

import java.util.List;

/** 打分触发：source 缺省 MANUAL，scope 缺省 ALL。 */
public record ScoreScoringCommand(String source, String scope, List<Long> itemIds) {

    public static ScoreScoringCommand defaults() {
        return new ScoreScoringCommand("MANUAL", "ALL", List.of());
    }

    public String normalizedSource() {
        return "AUTO".equalsIgnoreCase(source) ? "AUTO" : "MANUAL";
    }

    public String normalizedScope() {
        if (scope == null || scope.isBlank()) {
            return "ALL";
        }
        return scope.toUpperCase();
    }
}
