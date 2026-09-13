// Input: 多张评分表的独立完整性结果
// Output: 拆分明细 + 文档级聚合（决策取最差）
// Pos: scoreparse/domain — 纯核心 record（spec 062）
package com.xiyu.bid.scoreparse.domain;

import java.util.List;

/**
 * 多表拆分的完整输出。
 * <p>{@code documentLevel} 为文档级聚合结果，决策优先级（最差优先）：
 * PARSE_FAILED &gt; STRUCTURE_INCOMPLETE &gt; REVIEW_REQUIRED &gt; AUTO_APPROVED，
 * 用于保留任务级 decisionStatus 字段语义，不阻塞按表落库。
 */
public record TableSplitResult(
        List<TableCompleteness> tables,
        ScoreCompletenessPolicy.ScoreCompletenessResult documentLevel) {
    public TableSplitResult {
        tables = tables == null ? List.of() : List.copyOf(tables);
    }
}
