package com.xiyu.bid.scoreparse.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * AI 评分解析待复核候选标准人工采纳领域策略（纯核心，无框架依赖）。
 */
public final class ScoreReviewAdoptionPolicy {

    public record CandidateInput(
            String factId,
            String code,
            String dimension,
            BigDecimal weight,
            String sourceText,
            String parentFactId,
            String scoreType,
            String childAggregation,
            boolean verifiedZeroDeduction,
            List<String> sourceCellIds
    ) {
        public CandidateInput(String factId, String code, String dimension, BigDecimal weight, String sourceText,
                              String parentFactId, String scoreType, String childAggregation, boolean verifiedZeroDeduction) {
            this(factId, code, dimension, weight, sourceText, parentFactId, scoreType, childAggregation,
                    verifiedZeroDeduction, List.of());
        }
        public CandidateInput(String factId, String code, String dimension, BigDecimal weight,
                              String sourceText, String parentFactId, String scoreType, String childAggregation) {
            this(factId, code, dimension, weight, sourceText, parentFactId, scoreType, childAggregation, false);
        }
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
            sourceCellIds = sourceCellIds == null ? List.of() : sourceCellIds.stream()
                    .filter(id -> id != null && !id.isBlank()).distinct().toList();
        }
    }

    private record RuleSource(String cellId, String ruleText) {}

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
     * @param selectedFactIds 用户选中的 factId 集合（若为空则默认选择正分项及经原文核验的零分扣分规则）
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
        Set<RuleSource> usedRules = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        int index = 1;

        List<ScoreUnitPolicy.Node> nodes = facts.stream()
                .filter(Objects::nonNull)
                .filter(fact -> !filterByIds || selectedFactIds.contains(fact.factId()))
                .map(fact -> new ScoreUnitPolicy.Node(fact.factId(), fact.parentFactId(), fact.weight(),
                        "UNKNOWN".equals(fact.childAggregation()) ? "SUM" : fact.childAggregation()))
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
            if (weight == null || (weight.signum() == 0 && !fact.verifiedZeroDeduction())) {
                // 只保留正分或服务端完整来源核验的零分扣分规则；显式选择不能绕过。
                continue;
            }

            if (fact.sourceText().isBlank() || fact.sourceText().matches("[-—–－]+")) {
                return AdoptionResult.failure("评分项缺少标书原文，请核对并补全评分规则");
            }

            if (!fact.sourceCellIds().isEmpty()) {
                String ruleText = EvidenceText.normalize(fact.sourceText()).text().replaceAll("\\s+", "");
                if (fact.sourceCellIds().stream().anyMatch(cellId -> !usedRules.add(new RuleSource(cellId, ruleText)))) {
                    return AdoptionResult.failure("候选中存在重复的原文评分项，请核对后重新采纳");
                }
            }

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
                    weight,
                    "OBJECTIVE".equals(fact.scoreType()) || "SUBJECTIVE".equals(fact.scoreType())
                            ? fact.scoreType() : new ScoreTypeClassificationPolicy().classify(detail),
                    "",
                    fact.sourceText(),
                    "",
                    "MANUAL_REVIEW_ADOPTED"
            ));

            total = total.add(weight);
            index++;
        }

        if (converted.isEmpty()) {
            return AdoptionResult.failure("未选出任何有效分值的评分项（须有正分值或经原文核验的扣分规则）");
        }

        return AdoptionResult.success(converted, total);
    }
}
