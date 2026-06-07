package com.xiyu.bid.casework.domain.model;

/**
 * AI 智能案例推荐的匹配评分结果（不可变值对象）。
 *
 * @param caseId          案例 ID
 * @param score           匹配分数 (0-100)
 * @param scoreLabel      分数标签："优质"(>=85)、"良好"(>=60)、"一般"
 * @param matchReason     匹配原因描述，如 "评分项匹配、类别一致、项目类型一致"
 * @param highlightedText 高亮后的应答文本（含 &lt;mark&gt; 标签）
 */
public record KnowledgeCaseMatchScore(
        Long caseId,
        int score,
        String scoreLabel,
        String matchReason,
        String highlightedText
) {
}
