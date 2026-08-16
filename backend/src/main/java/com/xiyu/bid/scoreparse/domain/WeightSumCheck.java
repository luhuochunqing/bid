// Input: 候选池各项权重列表 / 候选对象集合
// Output: 权重合计与维度级分值闭环校验结果
// Pos: scoreparse/domain — 纯核心，无框架依赖
// 维护声明: 维护者按项目SOP；spec 041 FR-005 / FR-022
package com.xiyu.bid.scoreparse.domain;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 权重合计与维度级分值闭环校验（spec 041 FR-005 / FR-022）。
 * <p>合计 ≠ 100（容差 ±0.5）或维度级分值异常：不阻断流程，标记 weightWarning 并触发
 * 二次解析/完整性回补（needRecheck）；前端展示实际总分。
 */
public class WeightSumCheck {

    /** 容差：四舍五入误差容忍 ±0.5 分 */
    private static final BigDecimal TOLERANCE = new BigDecimal("0.5");

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

    /** 维度级分值闭环校验：计算各维度权重汇总及整体闭环 */
    public Result checkCandidates(List<ScoreCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return new Result(BigDecimal.ZERO, true, true, Collections.emptyMap());
        }
        BigDecimal total = BigDecimal.ZERO;
        Map<String, BigDecimal> dimSums = new HashMap<>();
        for (ScoreCandidate candidate : candidates) {
            BigDecimal w = candidate.weight() == null ? BigDecimal.ZERO : candidate.weight();
            total = total.add(w);
            String dim = candidate.dim() == null || candidate.dim().isBlank() ? "其他" : candidate.dim().trim();
            dimSums.put(dim, dimSums.getOrDefault(dim, BigDecimal.ZERO).add(w));
        }
        boolean totalWarning = total.subtract(BigDecimal.valueOf(100)).abs().compareTo(TOLERANCE) > 0;
        boolean dimAnomaly = dimSums.values().stream().anyMatch(w -> w.compareTo(BigDecimal.ZERO) <= 0);
        boolean needRecheck = totalWarning || dimAnomaly;
        return new Result(total, totalWarning, needRecheck, Collections.unmodifiableMap(dimSums));
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
