// Input: 权重 / 匹配 tier / 匹配比例 / 评分类别
// Output: 阶段 1 预计得分（BigDecimal 或 null）
// Pos: scoreparse/domain — 纯核心，无框架依赖
// 维护声明: 维护者按项目SOP；spec 041 FR-011 / 简单粗估 / SC-003

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
 *   <li>PARTIAL → 权重 × 匹配比例 / 100 保留两位小数，限制到 [0, weight]</li>
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
        return estimateByCount(weight, Math.max(0, Math.min(100, matchRatio)), 100);
    }

    /** Knowledge-library estimate: round the final score once, without a minimum one-point clamp. */
    public BigDecimal estimateByCount(BigDecimal weight, int matchedCount, int requiredCount) {
        int target = Math.max(1, requiredCount);
        int matched = Math.min(Math.max(0, matchedCount), target);
        return weight.multiply(BigDecimal.valueOf(matched))
                .divide(BigDecimal.valueOf(target), 2, RoundingMode.HALF_UP);
    }

    /**
     * 阶段 2 按类型公式计分（PRD §3.4）：
     * 资质/仓库/品牌/人员/业绩等客观项按 weight × ratio / 100 四舍五入取整；主观项强制返回 null。
     */
    public BigDecimal computeStage2Score(BigDecimal weight, String category, Integer matchRatio, String scoreType) {
        if (TYPE_SUBJECTIVE.equals(scoreType) || weight == null || matchRatio == null) {
            return null;
        }
        int ratio = Math.max(0, Math.min(100, matchRatio));
        if (ratio == 0) {
            return BigDecimal.ZERO;
        }
        if (ratio >= 100) {
            return weight;
        }

        BigDecimal calculated = weight.multiply(BigDecimal.valueOf(ratio))
                .divide(HUNDRED, 0, RoundingMode.HALF_UP);
        return calculated.min(weight).max(BigDecimal.ZERO);
    }
}
