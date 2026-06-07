// Input: 评分标准条目
// Output: 带子类型标签的单个评分标准项
// Pos: biddraftagent/domain — 评分标准条目值对象

package com.xiyu.bid.biddraftagent.domain;

/**
 * 带子类型标签的评分标准条目。
 *
 * @param text    评分标准文本
 * @param subType 子类型标签
 */
public record ScoringCriteriaItem(
    String text,
    ScoringCriteriaSubType subType
) {}
