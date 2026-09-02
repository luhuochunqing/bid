// Input: 评分规则原文语义
// Output: 评分规则类型
// Pos: scoreparse/domain — 纯核心，无框架依赖（spec 059 Phase 2）
package com.xiyu.bid.scoreparse.domain;

/** 评分规则语义，不把规则中的奖励分误当成评分项权重。 */
public enum ScoreRule {
    /** 评分项允许的最高分/封顶分。 */
    MAX_SCORE,
    /** 达成条件后的奖励分。 */
    AWARD,
    /** 未达成条件时的扣分。 */
    DEDUCTION,
    /** 额外加分。 */
    BONUS,
    /** 由公式计算的上限。 */
    FORMULA
}
