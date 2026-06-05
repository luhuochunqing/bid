package com.xiyu.bid.casework.domain.model;

/**
 * AI 智能案例推荐的匹配条件（不可变值对象）。
 *
 * @param projectId        当前项目 ID
 * @param scoringItemTitle 选中的评分项标题
 * @param scoringCategory  评分项类别，如 "技术"/"商务"/"实施服务"/"资质业绩"
 * @param projectType      项目类型
 * @param customerType     客户类型
 * @param industry         行业
 * @param region           地区
 * @param keyword          用户输入的额外关键词
 */
public record KnowledgeCaseMatchCriteria(
        Long projectId,
        String scoringItemTitle,
        String scoringCategory,
        String projectType,
        String customerType,
        String industry,
        String region,
        String keyword
) {
}
