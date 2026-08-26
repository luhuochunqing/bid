// Input: 竞品字典状态常量
// Output: 二值枚举 — ACTIVE / DISABLED
// Pos: competitionintel/entity/ - pure enum, no Spring/JPA
package com.xiyu.bid.competitionintel.entity;

/**
 * 竞品状态。specs/055 竞品字典治理。
 * <p>软停用：DISABLED 不出现在录入下拉，但历史快照与已有关联不受影响。</p>
 */
public enum CompetitorStatus {
    /** 启用（默认，录入下拉可见） */
    ACTIVE,
    /** 停用（软删除，录入下拉不可见） */
    DISABLED
}
