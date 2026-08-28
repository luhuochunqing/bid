// Input: 趋势分析各维度查询共用的筛选参数（时间/部门已展开名集/区域/客户类型等）
// Output: 不可变参数载体，供 DimensionQueryService 拼装 JPQL 与参数表
// Pos: Support/M1 趋势分析查询参数对象（specs/055 自长参数列表收敛而来）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.analytics.service;

import com.xiyu.bid.entity.Project;

import java.time.LocalDate;
import java.util.List;

/**
 * M1 趋势分析查询参数封装。
 * 将 TrendAnalysisService.getEnhancedTrends 中的 9 个筛选参数统一封装，
 * 避免在 DimensionQueryService 各方法间重复传递长参数列表。
 *
 * <p><b>注意</b>：{@code departmentMatchNames} 已由 {@code AnalyticsDepartmentHierarchySupport}
 * 按部门两级口径展开（选中 ∪ 全部后代），<b>不是</b>用户原始勾选集；null = 未启用部门筛选。
 * 新调用方不得直接塞原始勾选名，否则「勾父含后代」口径会在该轴静默丢失。</p>
 */
record TrendQueryCriteria(
        LocalDate startDate,
        LocalDate endDate,
        List<String> departmentMatchNames,
        List<String> userIds,
        List<String> regionIds,
        List<String> customerTypes,
        List<String> projectTypes,
        List<Project.Status> statuses,
        List<String> tenderEntities,
        List<String> competitorNames
) {
}