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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 评分完整性校验策略（spec 059 Phase 4）。
 * <p>在落库与决策前统一计算并校验 7 大维度：
 * <ol>
 *   <li>声明总分 (declaredTotalScore)：从正文中提取显式总分声明（默认 100 分）</li>
 *   <li>末级权重合计 (leafWeightSum)：末级叶子事实权重总和必须精确等于声明总分</li>
 *   <li>父子重复 (parentChildOverlap)：被引用为父项的事实不得同时作为末级项计入总权重</li>
 *   <li>按大类声明的子项合计 (dimensionCategorySumMatch)：父项声明的分值必须精确等于其直接子项之和</li>
 *   <li>异常低候选数 (abnormallyLowItemCount)：末级项数过少（<3 项）且正文字数较长时拦截</li>
 *   <li>缺失证据数 (missingEvidenceCount)：必须具备单元格引用且权重证据在原文中精确定位</li>
 *   <li>结构状态 (structureStatus)：底层网格存在破损或未闭合单元格时直接阻断</li>
 * </ol>
 */
public final class ScoreCompletenessPolicy {

    private static final BigDecimal DEFAULT_TOTAL_SCORE = BigDecimal.valueOf(100);
    private static final int MIN_REASONABLE_ITEM_COUNT = 3;
    private static final int LONG_SECTION_TEXT_THRESHOLD = 300;

    private static final Pattern DECLARED_TOTAL_PATTERN = Pattern.compile(
            "(?:总分|满分|分值构成|合计|总得分)[：:\\s]*(\\d+(?:\\.\\d+)?)\\s*分"
    );

    private ScoreCompletenessPolicy() {
    }

    public static ScoreCompletenessResult evaluateCompleteness(List<ScoreFact> facts, String tenderSectionText) {
        return evaluateCompleteness(facts, tenderSectionText, true);
    }

    public static ScoreCompletenessResult evaluateCompleteness(
            List<ScoreFact> facts, String tenderSectionText, boolean structurallyValid) {
        if (!structurallyValid) {
            return failureOutcome(
                    ScoreParseDecision.STRUCTURE_INCOMPLETE, "STRUCTURE_DEFECT",
                    DEFAULT_TOTAL_SCORE, BigDecimal.ZERO, List.of("文档表格结构破损或存在未闭合单元格"),
                    List.of(), List.of(), 0);
        }
        if (facts == null || facts.isEmpty()) {
            return failureOutcome(
                    ScoreParseDecision.PARSE_FAILED, "ZERO_VALID_ITEMS",
                    DEFAULT_TOTAL_SCORE, BigDecimal.ZERO, List.of("未提取到任何评分事实"),
                    List.of(), List.of(), 0);
        }

        BigDecimal declaredTotal = extractDeclaredTotal(tenderSectionText);
        Set<String> parentIds = ScoreFact.parentIds(facts);

        List<ScoreFact> leafFacts = new ArrayList<>();
        List<ScoreFact> parentFacts = new ArrayList<>();
        List<String> defects = new ArrayList<>();
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
            if (f.sourceCellIds() == null || f.sourceCellIds().isEmpty()
                    || f.weightEvidence() == null || !f.weightEvidence().isLocatable()) {
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

        // 3. 异常低候选数校验
        int textLength = tenderSectionText == null ? 0 : tenderSectionText.trim().length();
        if (leafFacts.size() < MIN_REASONABLE_ITEM_COUNT && textLength > LONG_SECTION_TEXT_THRESHOLD) {
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
                    declaredTotal, leafWeightSum, leafFacts.size(), parentFacts.size(),
                    0, List.of(), List.copyOf(leafFacts), List.copyOf(parentFacts));
        }

        String primaryFallback = resolvePrimaryFallback(defects);
        return new ScoreCompletenessResult(
                false, ScoreParseDecision.REVIEW_REQUIRED, primaryFallback,
                declaredTotal, leafWeightSum, leafFacts.size(), parentFacts.size(),
                missingEvidenceCount, List.copyOf(defects),
                List.copyOf(leafFacts), List.copyOf(parentFacts));
    }

    private static BigDecimal extractDeclaredTotal(String text) {
        if (text == null || text.isBlank()) {
            return DEFAULT_TOTAL_SCORE;
        }
        Matcher m = DECLARED_TOTAL_PATTERN.matcher(text);
        if (m.find()) {
            try {
                BigDecimal val = new BigDecimal(m.group(1));
                if (val.compareTo(BigDecimal.ZERO) > 0 && val.compareTo(BigDecimal.valueOf(1000)) <= 0) {
                    return val.setScale(2, RoundingMode.HALF_UP);
                }
            } catch (NumberFormatException ignored) {
                // 忽略异常，降级默认
            }
        }
        return DEFAULT_TOTAL_SCORE;
    }

    private static String resolvePrimaryFallback(List<String> defects) {
        for (String d : defects) {
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
            ScoreParseDecision decision, String fallback, BigDecimal declared, BigDecimal sum,
            List<String> defects, List<ScoreFact> leaves, List<ScoreFact> parents, int missing) {
        return new ScoreCompletenessResult(
                false, decision, fallback, declared, sum,
                leaves.size(), parents.size(), missing, List.copyOf(defects),
                List.copyOf(leaves), List.copyOf(parents));
    }

    public record ScoreCompletenessResult(
            boolean complete,
            ScoreParseDecision decision,
            String fallbackReason,
            BigDecimal declaredTotalScore,
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
            map.put("leafWeightSum", leafWeightSum);
            map.put("discrepancy", declaredTotalScore.subtract(leafWeightSum).abs());
            map.put("leafCount", leafCount);
            map.put("parentCount", parentCount);
            map.put("missingEvidenceCount", missingEvidenceCount);
            map.put("defects", defects);
            return Collections.unmodifiableMap(map);
        }
    }
}
