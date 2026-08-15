// Input: 权重 / 匹配 tier / 匹配比例 / 评分类别
// Output: 阶段 1 预计得分（BigDecimal 或 null）
// Pos: scoreparse/domain — 纯核心，无框架依赖
// 维护声明: 维护者按项目SOP；spec 041 FR-011 / FR-013 / SC-003

package com.xiyu.bid.scoreparse.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 阶段 1 预计得分计算策略（spec 041 FR-011 / FR-013）。
 *
 * <p>规则：
 * <ul>
 *   <li>主观项 → null（SC-003 主观项数字得分零泄漏）</li>
 *   <li>FULL → 权重满分；NONE → 0 分</li>
 *   <li>PARTIAL → 权重 × 匹配比例 / 100 四舍五入取整，并钳位到开区间 (0, weight)</li>
 * </ul>
 */
public class PartialScorePolicy {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final String TIER_FULL = "FULL";
    private static final String TIER_NONE = "NONE";
    private static final String TYPE_SUBJECTIVE = "SUBJECTIVE";

    /**
     * @param weight      评分项权重（&gt; 0，由解析闭环校验保证）
     * @param tier        匹配档位 FULL / PARTIAL / NONE
     * @param matchRatio  匹配比例 0-100
     * @param scoreType   OBJECTIVE / SUBJECTIVE
     * @return 预计得分；主观项返回 null
     */
    public BigDecimal compute(BigDecimal weight, String tier, int matchRatio, String scoreType) {
        if (TYPE_SUBJECTIVE.equals(scoreType)) {
            return null;
        }
        if (TIER_NONE.equals(tier)) {
            return BigDecimal.ZERO;
        }
        if (TIER_FULL.equals(tier)) {
            return weight;
        }
        BigDecimal raw = weight.multiply(BigDecimal.valueOf(matchRatio))
                .divide(HUNDRED, 0, RoundingMode.HALF_UP);
        return clampToOpenInterval(raw, weight);
    }

    /** FR-013 开区间 (0, weight) 钳位：下界 1（不超过 weight），上界 weight-1（不低于下界）。 */
    private BigDecimal clampToOpenInterval(BigDecimal raw, BigDecimal weight) {
        BigDecimal lower = BigDecimal.ONE.min(weight);
        BigDecimal upper = weight.subtract(BigDecimal.ONE).max(lower);
        if (raw.compareTo(lower) < 0) {
            return lower;
        }
        if (raw.compareTo(upper) > 0) {
            return upper;
        }
        return raw;
    }
}
