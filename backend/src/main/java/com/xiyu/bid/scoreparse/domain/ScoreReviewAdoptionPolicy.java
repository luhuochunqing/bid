package com.xiyu.bid.scoreparse.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * AI 评分解析待复核候选标准人工采纳领域策略（纯核心，无框架依赖）。
 */
public final class ScoreReviewAdoptionPolicy {

    /** Returns whether a human-confirmed candidate sum fits within its verified scheme total. */
    public static boolean isWithinDeclaredTotal(BigDecimal candidateTotal, BigDecimal declaredTotal) {
        return candidateTotal != null && declaredTotal != null
                && candidateTotal.compareTo(BigDecimal.ZERO) > 0
                && candidateTotal.compareTo(declaredTotal) <= 0;
    }

    public record CandidateInput(
            String factId,
            String code,
            String dimension,
            BigDecimal weight,
            String sourceText,
            String parentFactId,
            String scoreType,
            String childAggregation
    ) {
        public CandidateInput(String factId, String code, String dimension, BigDecimal weight,
                              String sourceText, String parentFactId, String scoreType) {
            this(factId, code, dimension, weight, sourceText, parentFactId, scoreType, null);
        }
        public CandidateInput(String factId, String code, String dimension, BigDecimal weight,
                              String sourceText, String parentFactId) {
            this(factId, code, dimension, weight, sourceText, parentFactId, null, null);
        }
        public CandidateInput {
            factId = factId == null ? "" : factId.trim();
            code = code == null ? "" : code.trim();
            dimension = dimension == null ? "" : dimension.trim();
            sourceText = sourceText == null ? "" : sourceText.trim();
            parentFactId = parentFactId == null ? "" : parentFactId.trim();
            scoreType = scoreType == null ? "" : scoreType.trim().toUpperCase();
            childAggregation = ChildAggregation.legacyDefault(childAggregation).name();
        }
    }

    public record AdoptionResult(
            boolean success,
            List<ScoreCandidate> candidates,
            BigDecimal totalWeight,
            String errorMessage
    ) {
        // REUSE-OK: backend/src/main/java/com/xiyu/bid/util/PasswordValidator.java — 评分采纳结果静态工厂，非密码验证
        public static AdoptionResult failure(String message) {
            return new AdoptionResult(false, List.of(), BigDecimal.ZERO, message);
        }

        // REUSE-OK: backend/src/main/java/com/xiyu/bid/util/PasswordValidator.java — 评分采纳结果静态工厂，非密码验证
        public static AdoptionResult success(List<ScoreCandidate> candidates, BigDecimal totalWeight) {
            return new AdoptionResult(true, List.copyOf(candidates), totalWeight, null);
        }
    }

    /**
     * 评估并转换候选评分事实为可持久化的 ScoreCandidate。
     *
     * @param facts 全部可用候选事实
     * @param selectedFactIds 用户选中的 factId 集合（若为空则默认选择所有权重大于 0 的项）
     * @param adjustedWeights 用户手动微调的权重映射（factId -> weight，可选）
     * @return 采纳评估结果
     */
    public AdoptionResult adopt(
            List<CandidateInput> facts,
            Set<String> selectedFactIds,
            Map<String, BigDecimal> adjustedWeights
    ) {
        if (facts == null || facts.isEmpty()) {
            return AdoptionResult.failure("候选评分事实为空，无法采纳");
        }

        Map<String, BigDecimal> adjustments = adjustedWeights == null ? Map.of() : adjustedWeights;
        boolean filterByIds = selectedFactIds != null && !selectedFactIds.isEmpty();

        List<ScoreCandidate> converted = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int index = 1;

        List<ScoreUnitPolicy.Node> nodes = facts.stream()
                .filter(Objects::nonNull)
                .map(fact -> new ScoreUnitPolicy.Node(fact.factId(), fact.parentFactId(), fact.weight(),
                        fact.childAggregation()))
                .toList();
        Set<String> effectiveUnitIds = ScoreUnitPolicy.effectiveUnitIds(nodes);

        for (CandidateInput fact : facts) {
            if (fact == null || !effectiveUnitIds.contains(fact.factId())) {
                continue;
            }
            if (filterByIds && !selectedFactIds.contains(fact.factId())) {
                continue;
            }

            BigDecimal weight = adjustments.getOrDefault(fact.factId(), fact.weight());
            if (weight != null && weight.compareTo(BigDecimal.ZERO) < 0) {
                return AdoptionResult.failure("候选评分项分值不能为负数");
            }
            if (weight == null || weight.compareTo(BigDecimal.ZERO) <= 0) {
                // 如果用户没有显式勾选特定 ID，自动跳过分值为 0 的模板占位项
                if (!filterByIds) {
                    continue;
                }
            }

            if (fact.sourceText().isBlank() || "—".equals(fact.sourceText())) {
                return AdoptionResult.failure("评分项缺少标书原文，请补充完整评分规则或重新解析后再采纳");
            }
            BigDecimal effectiveWeight = weight == null ? BigDecimal.ZERO : weight;
            String dim = fact.dimension();
            if (dim.isBlank()) {
                dim = !fact.code().isBlank() ? "评分项 " + fact.code() : "评分项 " + index;
            }

            String code = fact.code().isBlank() ? String.valueOf(index) : fact.code();
            String detail = fact.sourceText();

            converted.add(new ScoreCandidate(
                    code,
                    dim,
                    detail,
                    effectiveWeight,
                    "OBJECTIVE".equals(fact.scoreType()) || "SUBJECTIVE".equals(fact.scoreType())
                            ? fact.scoreType() : new ScoreTypeClassificationPolicy().classify(detail),
                    "",
                    fact.sourceText(),
                    "",
                    "MANUAL_REVIEW_ADOPTED"
            ));

            total = total.add(effectiveWeight);
            index++;
        }

        if (converted.isEmpty()) {
            return AdoptionResult.failure("未选出任何有效分值的评分项（分值必须大于 0）");
        }

        return AdoptionResult.success(converted, total);
    }
}
