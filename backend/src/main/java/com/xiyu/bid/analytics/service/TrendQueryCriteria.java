package com.xiyu.bid.analytics.service;

import com.xiyu.bid.entity.Project;

import java.time.LocalDate;
import java.util.List;

/**
 * M1 趋势分析查询参数封装。
 * 将 TrendAnalysisService.getEnhancedTrends 中的 9 个筛选参数统一封装，
 * 避免在 DimensionQueryService 各方法间重复传递长参数列表。
 */
record TrendQueryCriteria(
        LocalDate startDate,
        LocalDate endDate,
        List<String> departmentIds,
        List<String> userIds,
        List<String> regionIds,
        List<String> customerTypes,
        List<String> projectTypes,
        List<Project.Status> statuses,
        List<String> tenderEntities,
        List<String> competitorNames
) {
}