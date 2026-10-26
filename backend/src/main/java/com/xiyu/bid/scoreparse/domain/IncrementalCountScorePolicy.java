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
            "每\\s*增加\\s*(\\d+|[一二两三四五六七八九十]+)\\s*(?:个|项|份)"
                    + ".{0,40}?得\\s*(\\d+(?:\\.\\d+)?)\\s*分"
                    + "[,，\\s]*?(?:最高|满分|最多).{0,20}?得?\\s*(\\d+(?:\\.\\d+)?)\\s*分",
            Pattern.DOTALL);

    /** 基础分模式（如：满足资格条件的业绩得基础分60分，在此基础上每增加一个...加20分，满分100分） */
    private static final Pattern BASE_SCORE_PATTERN = Pattern.compile(
            "(?:得)?基础分\\s*(\\d+(?:\\.\\d+)?)\\s*分");
    private static final Pattern BASE_INCREMENT_AND_CAP = Pattern.compile(
            "每\\s*增加\\s*(\\d+|[一二两三四五六七八九十]+)\\s*(?:个|项|份)"
                    + ".{0,80}?(?:加|得)\\s*(\\d+(?:\\.\\d+)?)\\s*分"
                    + "[,，\\s]*?(?:最高|满分|最多).{0,20}?得?\\s*(\\d+(?:\\.\\d+)?)\\s*分",
            Pattern.DOTALL);

    private IncrementalCountScorePolicy() {
    }

    public static Optional<Rule> parse(String detail) {
        if (detail == null || detail.isBlank()) {
            return Optional.empty();
        }

        // 1. 尝试基础分模式（基础分 > 0）
        Matcher baseScoreMatcher = BASE_SCORE_PATTERN.matcher(detail);
        Matcher baseIncrementMatcher = BASE_INCREMENT_AND_CAP.matcher(detail);
        if (baseScoreMatcher.find() && baseIncrementMatcher.find()) {
            BigDecimal baseScore = new BigDecimal(baseScoreMatcher.group(1));
            Optional<Integer> incrementCountOpt = parseCount(baseIncrementMatcher.group(1));
            if (incrementCountOpt.isPresent() && baseScore.signum() > 0) {
                int increment = incrementCountOpt.get();
                BigDecimal pointsPerIncrement = new BigDecimal(baseIncrementMatcher.group(2));
                BigDecimal maximumScore = new BigDecimal(baseIncrementMatcher.group(3));
                if (increment > 0 && pointsPerIncrement.signum() > 0 && maximumScore.compareTo(baseScore) >= 0) {
                    return Optional.of(new Rule(1, increment, pointsPerIncrement, maximumScore, baseScore));
                }
            }
        }

        // 2. 尝试零分基数模式（首项不得分，纯递增）
        Matcher baseMatcher = ZERO_SCORE_BASE.matcher(detail);
        Matcher incrementMatcher = INCREMENT_AND_CAP.matcher(detail);
        if (!baseMatcher.find() || !incrementMatcher.find()) {
            return Optional.empty();
        }
        Optional<Integer> baseCount = parseCount(baseMatcher.group(1));
        Optional<Integer> incrementCount = parseCount(incrementMatcher.group(1));
        if (baseCount.isEmpty() || incrementCount.isEmpty()) {
            return Optional.empty();
        }
        int base = baseCount.orElse(0);
        int increment = incrementCount.orElse(0);
        BigDecimal pointsPerIncrement = new BigDecimal(incrementMatcher.group(2));
        BigDecimal maximumScore = new BigDecimal(incrementMatcher.group(3));
        if (increment <= 0 || pointsPerIncrement.signum() <= 0 || maximumScore.signum() <= 0) {
            return Optional.empty();
        }
        BigDecimal increments = maximumScore.divide(pointsPerIncrement, 0, RoundingMode.CEILING);
        BigDecimal requiredCount = increments.multiply(BigDecimal.valueOf(increment))
                .add(BigDecimal.valueOf(base));
        if (requiredCount.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0) {
            return Optional.empty();
        }
        return Optional.of(new Rule(base, increment, pointsPerIncrement, maximumScore, BigDecimal.ZERO));
    }

    static Optional<Integer> parseCount(String value) {
        if (value.matches("\\d+")) {
            BigDecimal parsed = new BigDecimal(value);
            return parsed.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) <= 0
                    ? Optional.of(parsed.intValue())
                    : Optional.empty();
        }
        Integer parsed = switch (value) {
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
            default -> null;
        };
        return Optional.ofNullable(parsed);
    }

    public record Rule(int zeroScoreBaseCount, int incrementCount,
                       BigDecimal pointsPerIncrement, BigDecimal maximumScore,
                       BigDecimal baseScore) {

        public Rule(int zeroScoreBaseCount, int incrementCount,
                    BigDecimal pointsPerIncrement, BigDecimal maximumScore) {
            this(zeroScoreBaseCount, incrementCount, pointsPerIncrement, maximumScore, BigDecimal.ZERO);
        }

        public int requiredCountForMaximumScore() {
            BigDecimal pointsNeeded = maximumScore.subtract(baseScore != null ? baseScore : BigDecimal.ZERO);
            if (pointsNeeded.signum() <= 0) {
                return zeroScoreBaseCount;
            }
            int increments = pointsNeeded.divide(pointsPerIncrement, 0, RoundingMode.CEILING).intValue();
            return zeroScoreBaseCount + increments * incrementCount;
        }

        // REUSE-OK: backend/src/main/java/com/xiyu/bid/biddraftagent/domain/MaterialMatchScoringPolicy.java — 该方法计算评分项数量阶梯，另一策略计算物料匹配度，输入与业务语义不同
        // REUSE-OK: backend/src/main/java/com/xiyu/bid/casework/domain/policy/KnowledgeCaseMatchPolicy.java — 该方法按合格数量计算阶梯分，另一策略对知识案例与条件计算相似度，输入与返回类型不同
        public BigDecimal score(int qualifiedCount, BigDecimal weight) {
            if (qualifiedCount <= 0) {
                return BigDecimal.ZERO;
            }
            BigDecimal base = baseScore != null ? baseScore : BigDecimal.ZERO;
            int additionalCount = Math.max(0, qualifiedCount - zeroScoreBaseCount);
            int completedIncrements = additionalCount / incrementCount;
            BigDecimal calculated = base.add(pointsPerIncrement.multiply(BigDecimal.valueOf(completedIncrements)));
            BigDecimal cappedRaw = calculated.min(maximumScore).max(BigDecimal.ZERO);

            if (base.signum() > 0 && maximumScore.compareTo(new BigDecimal("10")) > 0
                    && weight != null && weight.compareTo(new BigDecimal("10")) <= 0) {
                // 条款按百分制定义（如满分100分），在评分表中占 weight 权重（如5分）
                return weight.multiply(cappedRaw)
                        .divide(maximumScore, 2, RoundingMode.HALF_UP);
            }

            BigDecimal cap = weight == null ? maximumScore : maximumScore.min(weight);
            return cappedRaw.min(cap);
        }
    }
}
