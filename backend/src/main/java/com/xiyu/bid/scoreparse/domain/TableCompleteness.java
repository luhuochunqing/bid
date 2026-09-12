// Input: 单张评分表的分组事实与独立校验结果
// Output: 每张独立评分表一份完整性结果（含无证据合并组）
// Pos: scoreparse/domain — 纯核心 record（spec 062）
package com.xiyu.bid.scoreparse.domain;

/**
 * 单张评分表的多表拆分完整性结果。
 * <p>{@code tableId} 为来源评分表 ID；无本表结构化总分证据时合并为
 * {@code NO_DECLARED_TOTAL} 组走正文/默认 100 兜底。
 */
public record TableCompleteness(
        String tableId,
        boolean hasOwnDeclaredTotal,
        ScoreCompletenessPolicy.ScoreCompletenessResult completeness) {
    public TableCompleteness {
        tableId = tableId == null ? "" : tableId;
    }
}
