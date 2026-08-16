// Input: 候选池各项权重列表 / 候选对象集合（含维度声明与章节信息）
// Output: 权重合计与维度级分值闭环校验结果
// Pos: scoreparse/domain — 纯核心，无框架依赖
// 维护声明: 维护者按项目SOP；spec 041 FR-005 / FR-022
package com.xiyu.bid.scoreparse.domain;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 权重合计与维度级分值闭环校验（spec 041 FR-005 / FR-022）。
 * <p>合计 ≠ 100（容差 ±0.5）或维度汇总与招标声明分值不符：不阻断流程，标记 weightWarning 并触发
 * 二次解析/完整性回补（needRecheck）；前端展示实际总分。
 */
public class WeightSumCheck {

    /** 容差：四舍五入误差容忍 ±0.5 分 */
    private static final BigDecimal TOLERANCE = new BigDecimal("0.5");
    private static final Pattern DECLARED_WEIGHT_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*分");

    public Result check(List<BigDecimal> weights) {
        BigDecimal total = BigDecimal.ZERO;
        if (weights != null) {
            for (BigDecimal weight : weights) {
                if (weight != null) {
                    total = total.add(weight);
                }
            }
        }
        boolean warning = total.subtract(BigDecimal.valueOf(100)).abs()
                .compareTo(TOLERANCE) > 0;
        return new Result(total, warning, warning, Collections.emptyMap());
    }

    /** 维度级分值闭环校验：计算各维度权重汇总、比对招标声明分值及整体闭环 */
    public Result checkCandidates(List<ScoreCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return new Result(BigDecimal.ZERO, true, true, Collections.emptyMap());
        }
        BigDecimal total = BigDecimal.ZERO;
        Map<String, BigDecimal> dimSums = new HashMap<>();
        Map<String, BigDecimal> declaredWeights = new HashMap<>();

        for (ScoreCandidate candidate : candidates) {
            BigDecimal w = candidate.weight() == null ? BigDecimal.ZERO : candidate.weight();
            total = total.add(w);
            String dim = candidate.dim() == null || candidate.dim().isBlank() ? "其他" : candidate.dim().trim();
            dimSums.put(dim, dimSums.getOrDefault(dim, BigDecimal.ZERO).add(w));

            if (!declaredWeights.containsKey(dim)) {
                BigDecimal declared = extractDeclaredWeight(dim, candidate.contextNote());
                if (declared != null) {
                    declaredWeights.put(dim, declared);
                }
            }
        }
        boolean totalWarning = total.subtract(BigDecimal.valueOf(100)).abs().compareTo(TOLERANCE) > 0;
        boolean dimAnomaly = dimSums.values().stream().anyMatch(w -> w.compareTo(BigDecimal.ZERO) <= 0);

        boolean declaredMismatch = false;
        for (Map.Entry<String, BigDecimal> entry : declaredWeights.entrySet()) {
            BigDecimal declared = entry.getValue();
            BigDecimal actualDimSum = dimSums.getOrDefault(entry.getKey(), BigDecimal.ZERO);
            if (actualDimSum.subtract(declared).abs().compareTo(TOLERANCE) > 0) {
                declaredMismatch = true;
                break;
            }
        }

        boolean needRecheck = totalWarning || dimAnomaly || declaredMismatch;
        return new Result(total, totalWarning, needRecheck, Collections.unmodifiableMap(dimSums));
    }

    private BigDecimal extractDeclaredWeight(String dim, String contextNote) {
        if (dim != null && !dim.isBlank() && dim.length() <= 30 && !containsScoringRuleKeywords(dim)) {
            Matcher m = DECLARED_WEIGHT_PATTERN.matcher(dim);
            if (m.find()) {
                try {
                    return new BigDecimal(m.group(1));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (contextNote != null && !contextNote.isBlank() && (contextNote.startsWith("#") || contextNote.startsWith("第") || contextNote.startsWith("【"))
                && contextNote.length() <= 40 && !containsScoringRuleKeywords(contextNote)) {
            Matcher sm = DECLARED_WEIGHT_PATTERN.matcher(contextNote);
            if (sm.find()) {
                try {
                    return new BigDecimal(sm.group(1));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return null;
    }

    private boolean containsScoringRuleKeywords(String text) {
        return text.contains("每") || text.contains("扣") || text.contains("得") || text.contains("加")
                || text.contains("项") || text.contains("个") || text.contains("少");
    }

    /**
     * @param totalWeight      实际权重合计
     * @param weightWarning    合计 ≠ 100（容差内）标记，前端展示实际总分
     * @param needRecheck      触发二次解析/完整性回补标记（FR-005）
     * @param dimensionWeights 维度分值归集
     */
    public record Result(BigDecimal totalWeight, boolean weightWarning, boolean needRecheck, Map<String, BigDecimal> dimensionWeights) {
        public Result(BigDecimal totalWeight, boolean weightWarning, boolean needRecheck) {
            this(totalWeight, weightWarning, needRecheck, Collections.emptyMap());
        }
    }
}
