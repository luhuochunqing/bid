package com.xiyu.bid.bidmatch.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BidMatchScoringPolicy {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    public ValidationResult validate(BidMatchScoringModel model) {
        List<String> errors = new ArrayList<>();
        if (model == null) {
            return ValidationResult.failed(List.of("模型不能为空"));
        }
        List<BidMatchDimension> enabledDimensions = model.dimensions().stream()
                .filter(BidMatchDimension::enabled)
                .toList();
        if (enabledDimensions.isEmpty()) {
            errors.add("至少需要启用一个评分维度");
        }
        int dimensionWeightTotal = enabledDimensions.stream()
                .mapToInt(BidMatchDimension::weight)
                .sum();
        if (!enabledDimensions.isEmpty() && dimensionWeightTotal != 100) {
            errors.add("启用维度权重合计必须为100");
        }
        enabledDimensions.forEach(dimension -> validateDimension(dimension, errors));
        return errors.isEmpty() ? ValidationResult.ok() : ValidationResult.failed(errors);
    }

    public BidMatchModelSnapshotResult createSnapshot(BidMatchScoringModel model, Long modelId, int versionNo) {
        ValidationResult validation = validate(model);
        if (!validation.valid()) {
            return BidMatchModelSnapshotResult.rejected(validation);
        }
        BidMatchScoringModel snapshotModel = new BidMatchScoringModel(
                modelId,
                model.name(),
                model.description(),
                model.dimensions(),
                model.draftRevision()
        );
        return BidMatchModelSnapshotResult.created(new BidMatchModelVersionSnapshot(modelId, versionNo, snapshotModel));
    }

    public BidMatchScoreEvaluation evaluate(BidMatchModelVersionSnapshot snapshot, MatchEvidence evidence) {
        MatchEvidence safeEvidence = evidence == null ? new MatchEvidence("", java.util.Map.of(), java.util.Map.of(), java.util.Set.of()) : evidence;
        List<BidMatchDimensionScore> dimensionScores = snapshot.model().dimensions().stream()
                .filter(BidMatchDimension::enabled)
                .map(dimension -> evaluateDimension(dimension, safeEvidence))
                .toList();
        BigDecimal totalScore = dimensionScores.stream()
                .map(score -> score.score()
                        .multiply(BigDecimal.valueOf(score.weight()))
                        .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        return new BidMatchScoreEvaluation(
                snapshot.modelId(),
                snapshot.versionNo(),
                totalScore,
                dimensionScores,
                safeEvidence.fingerprint()
        );
    }

    private ValidationResult validateDimension(BidMatchDimension dimension, List<String> errors) {
        if (isBlank(dimension.code())) {
            errors.add("启用维度编码不能为空");
        }
        if (dimension.weight() <= 0) {
            errors.add("维度 " + dimension.code() + " 的权重必须大于0");
        }
        List<BidMatchRule> enabledRules = dimension.rules().stream()
                .filter(BidMatchRule::enabled)
                .toList();
        if (enabledRules.isEmpty()) {
            errors.add("维度 " + dimension.code() + " 至少需要启用一个规则");
        }
        int ruleWeightTotal = enabledRules.stream()
                .mapToInt(BidMatchRule::weight)
                .sum();
        if (!enabledRules.isEmpty() && ruleWeightTotal != 100) {
            errors.add("维度 " + dimension.code() + " 的启用规则权重合计必须为100");
        }
        enabledRules.forEach(rule -> validateRule(dimension.code(), rule, errors));
        return errors.isEmpty() ? ValidationResult.ok() : ValidationResult.failed(errors);
    }

    private ValidationResult validateRule(String dimensionCode, BidMatchRule rule, List<String> errors) {
        if (isBlank(rule.code())) {
            errors.add("维度 " + dimensionCode + " 存在编码为空的规则");
        }
        if (rule.weight() <= 0) {
            errors.add("规则 " + rule.code() + " 的权重必须大于0");
        }
        if (isBlank(rule.evidenceKey())) {
            errors.add("规则 " + rule.code() + " 的证据键不能为空");
        }
        if (rule.type() == BidMatchRuleType.KEYWORD && rule.keywords().isEmpty()) {
            errors.add("关键词规则 " + rule.code() + " 至少需要一个关键词");
        }
        if ((rule.type() == BidMatchRuleType.QUANTITY || rule.type() == BidMatchRuleType.RANGE)
                && rule.minValue() == null) {
            errors.add("规则 " + rule.code() + " 的最小值不能为空");
        }
        if (rule.type() == BidMatchRuleType.RANGE && rule.maxValue() == null) {
            errors.add("区间规则 " + rule.code() + " 的最大值不能为空");
        }
        return errors.isEmpty() ? ValidationResult.ok() : ValidationResult.failed(errors);
    }

    private BidMatchDimensionScore evaluateDimension(BidMatchDimension dimension, MatchEvidence evidence) {
        List<BidMatchRuleScore> ruleScores = dimension.rules().stream()
                .filter(BidMatchRule::enabled)
                .map(rule -> evaluateRule(rule, evidence))
                .toList();
        BigDecimal matchedWeight = ruleScores.stream()
                .map(BidMatchRuleScore::score)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        return new BidMatchDimensionScore(
                dimension.code(),
                dimension.name(),
                dimension.weight(),
                matchedWeight,
                ruleScores
        );
    }

    private BidMatchRuleScore evaluateRule(BidMatchRule rule, MatchEvidence evidence) {
        boolean missing = !evidence.hasEvidence(rule.evidenceKey());
        boolean matched = !missing && matchesRule(rule, evidence);
        MatchRuleEvaluationStatus status = ruleStatus(missing, matched);
        BigDecimal score = matched ? BigDecimal.valueOf(rule.weight()) : BigDecimal.ZERO;
        return new BidMatchRuleScore(
                rule.code(),
                rule.name(),
                rule.type(),
                rule.evidenceKey(),
                rule.weight(),
                matched,
                status,
                score.setScale(2, RoundingMode.HALF_UP)
        );
    }

    private boolean matchesRule(BidMatchRule rule, MatchEvidence evidence) {
        return switch (rule.type()) {
            case KEYWORD -> keywordMatched(rule, evidence);
            case EXISTS -> evidence.hasEvidence(rule.evidenceKey());
            case QUANTITY -> numberAtLeast(evidence.numbers().get(rule.evidenceKey()), rule.minValue());
            case RANGE -> numberInRange(evidence.numbers().get(rule.evidenceKey()), rule.minValue(), rule.maxValue());
        };
    }

    private boolean keywordMatched(BidMatchRule rule, MatchEvidence evidence) {
        String source = evidence.texts().getOrDefault(rule.evidenceKey(), "").toLowerCase(Locale.ROOT);
        return rule.keywords().stream()
                .map(keyword -> keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT))
                .filter(keyword -> !keyword.isBlank())
                .anyMatch(source::contains);
    }

    private boolean numberAtLeast(BigDecimal value, BigDecimal minValue) {
        return value != null && minValue != null && value.compareTo(minValue) >= 0;
    }

    private boolean numberInRange(BigDecimal value, BigDecimal minValue, BigDecimal maxValue) {
        return value != null
                && minValue != null
                && maxValue != null
                && value.compareTo(minValue) >= 0
                && value.compareTo(maxValue) <= 0;
    }

    private MatchRuleEvaluationStatus ruleStatus(boolean missing, boolean matched) {
        if (missing) {
            return MatchRuleEvaluationStatus.MISSING;
        }
        return matched ? MatchRuleEvaluationStatus.MATCHED : MatchRuleEvaluationStatus.UNMATCHED;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
