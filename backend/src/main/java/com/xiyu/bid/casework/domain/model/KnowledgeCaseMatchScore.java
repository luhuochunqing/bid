package com.xiyu.bid.casework.domain.model;

/**
 * AI 智能案例推荐的匹配评分结果（不可变值对象）。
 *
 * @param caseId              案例 ID
 * @param score               匹配分数 (0-100)
 * @param scoreLabel          分数标签："优质"(>=85)、"良好"(>=60)、"一般"
 * @param matchReason         匹配原因描述，如 "评分项匹配、类别一致、项目类型一致"
 * @param highlightedText     高亮后的应答文本（含 &lt;mark&gt; 标签）
 * @param scoringTitle        评分项标题
 * @param requirementRaw      评分项原始需求
 * @param responseText        应答原文
 * @param responseTextSummary 应答摘要
 * @param sourceProjectName   来源项目名称
 * @param customerType        客户类型
 * @param projectType         项目类型
 * @param bidResult           中标结果
 * @param reuseCount          复用次数
 */
public record KnowledgeCaseMatchScore(
        Long caseId,
        int score,
        String scoreLabel,
        String matchReason,
        String highlightedText,
        String scoringTitle,
        String requirementRaw,
        String responseText,
        String responseTextSummary,
        String sourceProjectName,
        String customerType,
        String projectType,
        String bidResult,
        Integer reuseCount
) {
    public KnowledgeCaseMatchScore(
            Long caseId,
            int score,
            String scoreLabel,
            String matchReason,
            String highlightedText
    ) {
        this(caseId, score, scoreLabel, matchReason, highlightedText,
                null, null, null, null, null, null, null, null, 0);
    }
}

