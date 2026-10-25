package com.xiyu.bid.scoreparse.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses and evaluates rules where an initial quantity scores zero and only increments earn points. */
public final class IncrementalCountScorePolicy {

    private static final Pattern ZERO_SCORE_BASE = Pattern.compile(
            "(?:具有|提供|具备|完成)?\\s*(\\d+|[一二两三四五六七八九十]+)\\s*(?:个|项|份)"
                    + ".{0,80}?(?:不得分|得\\s*0(?:\\.0+)?\\s*分)",
            Pattern.DOTALL);
    private static final Pattern INCREMENT_AND_CAP = Pattern.compile(
            "每增加\\s*(\\d+|[一二两三四五六七八九十]+)\\s*(?:个|项|份)"
                    + ".{0,40}?得\\s*(\\d+(?:\\.\\d+)?)\\s*分"
                    + "[,，\\s]*?(?:最高|满分|最多).{0,20}?得?\\s*(\\d+(?:\\.\\d+)?)\\s*分",
            Pattern.DOTALL);

    private IncrementalCountScorePolicy() {
    }

    public static Optional<Rule> parse(String detail) {
        if (detail == null || detail.isBlank()) {
            return Optional.empty();
        }
        Matcher baseMatcher = ZERO_SCORE_BASE.matcher(detail);
        Matcher incrementMatcher = INCREMENT_AND_CAP.matcher(detail);
        if (!baseMatcher.find() || !incrementMatcher.find()) {
            return Optional.empty();
        }
        try {
            int baseCount = parseCount(baseMatcher.group(1));
            int incrementCount = parseCount(incrementMatcher.group(1));
            BigDecimal pointsPerIncrement = new BigDecimal(incrementMatcher.group(2));
            BigDecimal maximumScore = new BigDecimal(incrementMatcher.group(3));
            if (baseCount < 0 || incrementCount <= 0
                    || pointsPerIncrement.signum() <= 0 || maximumScore.signum() <= 0) {
                return Optional.empty();
            }
            return Optional.of(new Rule(baseCount, incrementCount, pointsPerIncrement, maximumScore));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private static int parseCount(String value) {
        if (value.matches("\\d+")) {
            return Integer.parseInt(value);
        }
        return switch (value) {
            case "一" -> 1;
            case "二", "两" -> 2;
            case "三" -> 3;
            case "四" -> 4;
            case "五" -> 5;
            case "六" -> 6;
            case "七" -> 7;
            case "八" -> 8;
            case "九" -> 9;
            case "十" -> 10;
            default -> throw new NumberFormatException("无法识别数量");
        };
    }

    public record Rule(int zeroScoreBaseCount, int incrementCount,
                       BigDecimal pointsPerIncrement, BigDecimal maximumScore) {

        public int requiredCountForMaximumScore() {
            int increments = maximumScore.divide(pointsPerIncrement, 0, RoundingMode.CEILING).intValueExact();
            return Math.addExact(zeroScoreBaseCount, Math.multiplyExact(increments, incrementCount));
        }

        public BigDecimal score(int qualifiedCount, BigDecimal weight, String scoreType) {
            if ("SUBJECTIVE".equals(scoreType)) {
                return null;
            }
            int additionalCount = Math.max(0, qualifiedCount - zeroScoreBaseCount);
            int completedIncrements = additionalCount / incrementCount;
            BigDecimal calculated = pointsPerIncrement.multiply(BigDecimal.valueOf(completedIncrements));
            BigDecimal cap = weight == null ? maximumScore : maximumScore.min(weight);
            return calculated.min(cap).max(BigDecimal.ZERO);
        }
    }
}
