package com.xiyu.bid.analytics.service;

import com.xiyu.bid.entity.Project;

/**
 * 维度查询单行数据：维度分类标签 + 项目 ID + 项目状态。
 * 由 {@link TrendAnalysisDimensionQueryService} 查询返回，
 * 由 {@link TrendAnalysisComputationService} 聚合计算投标数/中标数/中标率。
 */
public record DimensionRow(
        String category,
        Long projectId,
        Project.Status status
) {
}