// Input: 趋势分析 9 个筛选参数（日期/部门/人员/区域/客户类型/项目类型/状态/投标主体/竞品）
// Output: 不可变查询条件 record，供 DimensionQueryService 与下钻层复用
// Pos: Service/查询参数封装
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

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