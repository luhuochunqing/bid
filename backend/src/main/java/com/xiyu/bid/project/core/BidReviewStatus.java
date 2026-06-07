// Input: 标书审核状态常量
// Output: 三值枚举 — REVIEWING / APPROVED / REJECTED
// Pos: project/core/ - pure enum, no Spring/JPA
package com.xiyu.bid.project.core;

/**
 * 标书审核状态。PRD §3.2.3-§3.2.4。
 * <p>纯枚举，无框架依赖。</p>
 */
public enum BidReviewStatus {
    /** 待审核（已提交） */
    REVIEWING,
    /** 审核通过 */
    APPROVED,
    /** 审核驳回 */
    REJECTED
}
