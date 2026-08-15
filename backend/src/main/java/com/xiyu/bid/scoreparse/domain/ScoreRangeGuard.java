// Input: AI/计算产出的得分 / 权重上限
// Output: 守卫后的得分 + 有效性标记
// Pos: scoreparse/domain — 纯核心，无框架依赖
// 维护声明: 维护者按项目SOP；spec 041 FR-016

package com.xiyu.bid.scoreparse.domain;

import java.math.BigDecimal;

/**
 * 得分区间守卫（spec 041 FR-016）。
 *
 * <p>得分 ∈ [0, weight] 放行；超区间置 null 并标记 invalid（调用方记录异常日志，
 * 不阻断其余评分项）。null 输入原样放行（主观项语义）。
 */
public class ScoreRangeGuard {

    /**
     * @param score  待守卫得分（null 视为主观项语义，直接放行）
     * @param weight 权重上限
     */
    public Result guard(BigDecimal score, BigDecimal weight) {
        if (score == null) {
            return new Result(null, true);
        }
        if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(weight) > 0) {
            return new Result(null, false);
        }
        return new Result(score, true);
    }

    /**
     * @param score 守卫后得分（超区间为 null）
     * @param valid true=区间内；false=超区间已置空（FR-016 异常场景）
     */
    public record Result(BigDecimal score, boolean valid) {
    }
}
