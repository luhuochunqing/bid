// Input: 候选池各项权重列表
// Output: 权重合计闭环校验结果
// Pos: scoreparse/domain — 纯核心，无框架依赖
// 维护声明: 维护者按项目SOP；spec 041 FR-005 / FR-022

package com.xiyu.bid.scoreparse.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * 权重合计闭环校验（spec 041 FR-005 / FR-022）。
 * <p>合计 ≠ 100（容差 ±0.5）：不阻断流程，标记 weightWarning 并触发
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
        return new Result(total, warning, warning);
    }

    /**
     * @param totalWeight   实际权重合计
     * @param weightWarning 合计 ≠ 100（容差内）标记，前端展示实际总分
     * @param needRecheck   触发二次解析/完整性回补标记（FR-005）
     */
    public record Result(BigDecimal totalWeight, boolean weightWarning, boolean needRecheck) {
    }
}
