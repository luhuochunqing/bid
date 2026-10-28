// Input: ScoreFact collection + tender text + structure validity
// Output: immutable ScoreCompletenessResult assessing 7 completeness dimensions
// Pos: scoreparse/domain — pure core, zero framework dependencies (spec 059 Phase 4)
package com.xiyu.bid.scoreparse.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 评分完整性校验策略（spec 059 Phase 4）。
 * <p>在落库与决策前统一计算并校验 7 大维度：
 * <ol>
 *   <li>声明总分 (declaredTotalScore)：结构化汇总行优先，正文兜底，最后默认 100 分</li>
 *   <li>末级权重合计 (leafWeightSum)：末级叶子事实权重总和必须精确等于声明总分</li>
 *   <li>父子重复 (parentChildOverlap)：被引用为父项的事实不得同时作为末级项计入总权重</li>
 *   <li>按大类声明的子项合计 (dimensionCategorySumMatch)：父项声明的分值必须精确等于其直接子项之和</li>
 *   <li>异常低候选数 (abnormallyLowItemCount)：文档级兜底时拦截（&lt;3 项且正文较长）；自带结构化总分声明的独立表不适用本条</li>
 *   <li>缺失证据数 (missingEvidenceCount)：必须具备单元格引用且权重证据在原文中精确定位</li>
 *   <li>结构状态 (structureStatus)：底层网格存在破损或未闭合单元格时直接阻断</li>
 * </ol>
 */
public final class ScoreCompletenessPolicy {
    private static final BigDecimal DEFAULT_TOTAL_SCORE = BigDecimal.valueOf(100);
    private static final int MIN_REASONABLE_ITEM_COUNT = 3;
    private static final int LONG_SECTION_TEXT_THRESHOLD = 300;
    private ScoreCompletenessPolicy() { }

    public static ScoreCompletenessResult evaluateCompleteness(List<ScoreFact> facts, String tenderSectionText) {
        return evaluateCompleteness(facts, tenderSectionText, true);
    }
    public static ScoreCompletenessResult evaluateCompleteness(
            List<ScoreFact> facts, String tenderSectionText, boolean structurallyValid) {
        return evaluateCompleteness(facts, tenderSectionText, structurallyValid, List.of());
    }
    public static ScoreCompletenessResult evaluateCompleteness(
            List<ScoreFact> facts,
            String tenderSectionText,
            boolean structurallyValid,
            List<ScoreTableSummaryPolicy.DeclaredTotalEvidence> structuredTotalEvidence) {
        if (!structurallyValid) {
            return failureOutcome(
                    ScoreParseDecision.STRUCTURE_INCOMPLETE, "STRUCTURE_DEFECT",
                    DEFAULT_TOTAL_SCORE, DeclaredTotalSource.DEFAULT_100,
                    BigDecimal.ZERO, List.of("文档表格结构破损或存在未闭合单元格"),
                    List.of(), List.of(), 0);
        }
        if (facts == null || facts.isEmpty()) {
            return failureOutcome(
                    ScoreParseDecision.PARSE_FAILED, "ZERO_VALID_ITEMS",
                    DEFAULT_TOTAL_SCORE, DeclaredTotalSource.DEFAULT_100,
                    BigDecimal.ZERO, List.of("未提取到任何评分事实"),
                    List.of(), List.of(), 0);
        }

        DeclaredTotalResolution declaredTotalResolution = resolveDeclaredTotal(
                structuredTotalEvidence, tenderSectionText);
        BigDecimal declaredTotal = declaredTotalResolution.value();
        Set<String> parentIds = ScoreFact.parentIds(facts);

        List<ScoreFact> leafFacts = new ArrayList<>();
        List<ScoreFact> parentFacts = new ArrayList<>();
        List<String> defects = new ArrayList<>(declaredTotalResolution.defects());
        int missingEvidenceCount = 0;

        for (ScoreFact f : facts) {
            if (f == null) {
                continue;
            }
            if (parentIds.contains(f.factId())) {
                parentFacts.add(f);
            } else {
                leafFacts.add(f);
            }
            boolean optionalUnboundedPriceFormula = (f.weight() == null
                    || f.weight().compareTo(BigDecimal.ZERO) == 0)
                    && ScoreTableSectionPolicy.isPriceFormula(List.of(f.dimension(), f.sourceText()));
            if (!optionalUnboundedPriceFormula && (f.sourceCellIds() == null || f.sourceCellIds().isEmpty()
                    || f.weightEvidence() == null || !f.weightEvidence().isLocatable())) {
                missingEvidenceCount++;
            }
        }
        if (missingEvidenceCount > 0) {
            defects.add(String.format("存在 %d 个事实缺失原文单元格证据或分值定位", missingEvidenceCount));
        }

        // 1. 父子重复校验：被引用的父项严禁作为叶子计分
        for (ScoreFact leaf : leafFacts) {
            if (parentIds.contains(leaf.factId())) {
                defects.add(String.format("父子重复: 父项 [%s] 不能与子项同时作为末级项计分", leaf.factId()));
            }
        }

        // 2. 按大类声明的子项合计校验：父项若自带声明分值，其子项权重合计必须精确相等
        Map<String, List<ScoreFact>> childrenByParentId = new LinkedHashMap<>();
        for (ScoreFact f : facts) {
            if (f != null && f.parentFactId() != null && !f.parentFactId().isBlank()) {
                childrenByParentId.computeIfAbsent(f.parentFactId(), k -> new ArrayList<>()).add(f);
            }
        }
        for (ScoreFact parent : parentFacts) {
            if (parent.weight() != null && parent.weight().compareTo(BigDecimal.ZERO) > 0) {
                List<ScoreFact> children = childrenByParentId.getOrDefault(parent.factId(), List.of());
                if (!children.isEmpty()) {
                    BigDecimal childrenSum = children.stream()
                            .map(c -> c.weight() == null ? BigDecimal.ZERO : c.weight())
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    if (childrenSum.compareTo(parent.weight()) != 0) {
                        defects.add(String.format("大类分值不自洽: 父项 [%s(%s)] 声明 %s分，但直接子项合计为 %s分",
                                parent.factId(), parent.dimension(), parent.weight().stripTrailingZeros().toPlainString(),
                                childrenSum.stripTrailingZeros().toPlainString()));
                    }
                }
            }
        }

        // 3. 异常低候选数校验（仅文档级兜底适用）：textLength 是整份文档正文；有本表结构化总分声明的独立表以声明-权重一致性判漏抽。
        int textLength = tenderSectionText == null ? 0 : tenderSectionText.trim().length();
        if (leafFacts.size() < MIN_REASONABLE_ITEM_COUNT
                && declaredTotalResolution.source() != DeclaredTotalSource.STRUCTURED_SUMMARY_ROW
                && textLength > LONG_SECTION_TEXT_THRESHOLD) {
            defects.add(String.format("评分项数量异常偏低: 末级项仅 %d 项，但正文包含 %d 字长评分章节",
                    leafFacts.size(), textLength));
        }

        // 4. 末级权重合计校验
        BigDecimal leafWeightSum = leafFacts.stream()
                .map(f -> f.weight() == null ? BigDecimal.ZERO : f.weight())
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        if (leafWeightSum.compareTo(declaredTotal) != 0) {
            defects.add(String.format("总权重不符预期: 末级权重合计为 %s分，声明总分为 %s分（差额 %s分）",
                    leafWeightSum.stripTrailingZeros().toPlainString(),
                    declaredTotal.stripTrailingZeros().toPlainString(),
                    declaredTotal.subtract(leafWeightSum).stripTrailingZeros().toPlainString()));
        }

        if (defects.isEmpty()) {
            return new ScoreCompletenessResult(
                    true, ScoreParseDecision.AUTO_APPROVED, null,
                    declaredTotal, declaredTotalResolution.source(),
                    leafWeightSum, leafFacts.size(), parentFacts.size(),
                    0, List.of(), List.copyOf(leafFacts), List.copyOf(parentFacts));
        }

        String primaryFallback = resolvePrimaryFallback(defects);
        return new ScoreCompletenessResult(
                false, ScoreParseDecision.REVIEW_REQUIRED, primaryFallback,
                declaredTotal, declaredTotalResolution.source(),
                leafWeightSum, leafFacts.size(), parentFacts.size(),
                missingEvidenceCount, List.copyOf(defects),
                List.copyOf(leafFacts), List.copyOf(parentFacts));
    }

    private static DeclaredTotalResolution resolveDeclaredTotal(
            List<ScoreTableSummaryPolicy.DeclaredTotalEvidence> structuredEvidence,
            String text) {
        Map<String, BigDecimal> structuredValues = new LinkedHashMap<>();
        if (structuredEvidence != null) {
            for (ScoreTableSummaryPolicy.DeclaredTotalEvidence evidence : structuredEvidence) {
                if (evidence == null || evidence.value() == null) {
                    continue;
                }
                BigDecimal normalized = evidence.value().setScale(2, RoundingMode.HALF_UP);
                structuredValues.putIfAbsent(normalized.stripTrailingZeros().toPlainString(), normalized);
            }
        }
        if (!structuredValues.isEmpty()) {
            BigDecimal first = structuredValues.values().iterator().next();
            if (structuredValues.size() == 1) {
                return new DeclaredTotalResolution(
                        first, DeclaredTotalSource.STRUCTURED_SUMMARY_ROW, List.of());
            }
            String values = String.join(", ", structuredValues.keySet());
            return new DeclaredTotalResolution(
                    first,
                    DeclaredTotalSource.STRUCTURED_SUMMARY_ROW,
                    List.of("结构化声明总分冲突: 评分表汇总行同时声明 [" + values + "] 分，禁止自动选择"));
        }

        var sourceTotal = TextScoringTotalEvidence.textTotal(text);
        if (sourceTotal.value() != null) {
            return new DeclaredTotalResolution(sourceTotal.value(), DeclaredTotalSource.TENDER_TEXT, List.of());
        }
        if ("CONFLICT".equals(sourceTotal.source())) {
            return new DeclaredTotalResolution(DEFAULT_TOTAL_SCORE, DeclaredTotalSource.DEFAULT_100,
                    List.of("正文声明总分冲突，禁止自动选择"));
        }
        return new DeclaredTotalResolution(DEFAULT_TOTAL_SCORE, DeclaredTotalSource.DEFAULT_100, List.of());
    }

    private static String resolvePrimaryFallback(List<String> defects) {
        for (String d : defects) {
            if (d.contains("结构化声明总分冲突")) {
                return "DECLARED_TOTAL_CONFLICT";
            }
            if (d.contains("父子重复")) {
                return "PARENT_CHILD_DUPLICATE";
            }
            if (d.contains("大类分值不自洽")) {
                return "CATEGORY_SUM_MISMATCH";
            }
            if (d.contains("总权重不符预期")) {
                return "WEIGHT_SUM_MISMATCH";
            }
            if (d.contains("缺失原文单元格证据")) {
                return "MISSING_EVIDENCE";
            }
            if (d.contains("评分项数量异常偏低")) {
                return "ABNORMALLY_LOW_COUNT";
            }
        }
        return "COMPLETENESS_DEFECT";
    }

    private static ScoreCompletenessResult failureOutcome(
            ScoreParseDecision decision, String fallback, BigDecimal declared,
            DeclaredTotalSource declaredTotalSource, BigDecimal sum,
            List<String> defects, List<ScoreFact> leaves, List<ScoreFact> parents, int missing) {
        return new ScoreCompletenessResult(
                false, decision, fallback, declared, declaredTotalSource, sum,
                leaves.size(), parents.size(), missing, List.copyOf(defects),
                List.copyOf(leaves), List.copyOf(parents));
    }
    public enum DeclaredTotalSource {
        STRUCTURED_SUMMARY_ROW,
        TENDER_TEXT,
        DEFAULT_100,
        UNKNOWN
    }

    private record DeclaredTotalResolution(BigDecimal value, DeclaredTotalSource source, List<String> defects) {
    }
    public record ScoreCompletenessResult(
            boolean complete,
            ScoreParseDecision decision,
            String fallbackReason,
            BigDecimal declaredTotalScore,
            DeclaredTotalSource declaredTotalSource,
            BigDecimal leafWeightSum,
            int leafCount,
            int parentCount,
            int missingEvidenceCount,
            List<String> defects,
            List<ScoreFact> leafFacts,
            List<ScoreFact> parentFacts
    ) {
        public ScoreCompletenessResult {
            defects = defects == null ? List.of() : List.copyOf(defects);
            leafFacts = leafFacts == null ? List.of() : List.copyOf(leafFacts);
            parentFacts = parentFacts == null ? List.of() : List.copyOf(parentFacts);
        }

        public Map<String, Object> toReviewSnapshot() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("complete", complete);
            map.put("decision", decision.name());
            map.put("fallbackReason", fallbackReason);
            map.put("declaredTotalScore", declaredTotalScore);
            map.put("declaredTotalSource", declaredTotalSource.name());
            map.put("leafWeightSum", leafWeightSum);
            map.put("discrepancy", declaredTotalScore == null || leafWeightSum == null
                    ? null : declaredTotalScore.subtract(leafWeightSum).abs());
            map.put("leafCount", leafCount);
            map.put("parentCount", parentCount);
            map.put("missingEvidenceCount", missingEvidenceCount);
            map.put("defects", defects);
            return Collections.unmodifiableMap(map);
        }
    }
}
