// Input: 结构化评分标准条目
// Output: 带编号、维度、指标、权重、子类型标签的评分标准项
// Pos: biddraftagent/domain — 评分标准条目值对象

package com.xiyu.bid.biddraftagent.domain;

import java.math.BigDecimal;

/**
 * 结构化评分标准条目，按蓝图要求包含编号、维度、具体指标、权重。
 *
 * @param itemNumber 评分项编号（如"1"、"2.1"）
 * @param dimension  评分维度（如"价格评分"、"技术方案"）
 * @param indicator  具体指标描述
 * @param weight     权重（如 30 表示 30% 或 30 分）
 * @param subType    子类型标签（价格权重/技术评价等）
 */
public record ScoringCriterion(
    String itemNumber,
    String dimension,
    String indicator,
    BigDecimal weight,
    ScoringCriteriaSubType subType
) {

    /** 计算总分：累加所有条目的 weight */
    public static BigDecimal calculateTotalScore(java.util.List<ScoringCriterion> criteria) {
        if (criteria == null || criteria.isEmpty()) return BigDecimal.ZERO;
        return criteria.stream()
                .map(ScoringCriterion::weight)
                .filter(w -> w != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
