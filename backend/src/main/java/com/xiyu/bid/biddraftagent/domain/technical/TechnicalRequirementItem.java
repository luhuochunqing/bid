// Input: 技术要点条目
// Output: 带子类型标签的单个技术要点
// Pos: biddraftagent/domain/technical — 技术要点条目值对象

package com.xiyu.bid.biddraftagent.domain.technical;

/**
 * 带子类型标签的技术要点条目。
 *
 * @param text    技术要求文本
 * @param subType 子类型标签：硬指标/功能/兼容性/加分项
 */
public record TechnicalRequirementItem(
    String text,
    TechnicalSubType subType
) {}
