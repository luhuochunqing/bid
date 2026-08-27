// Input: 下钻原生 SQL 的 where 构建器与参数表、dimension/axisValue 等请求参数
// Output: 追加维度值过滤与 IN 过滤后的 where/params（原位修改）
// Pos: Support/下钻 SQL 条件组装支撑类（无状态静态工具，specs/055 自 TrendDrillDownService 拆出）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.analytics.service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * M1 趋势分析下钻的维度过滤条件组装器（PRD 6.6）。
 * 仅做 where 片段拼接与参数注册，不持有状态、不访问数据源；
 * 区域/客户类型的归一化预计算仍由 {@link TrendDrillDownService} 编排。
 */
public final class TrendDrillDownFilterSupport {

    private TrendDrillDownFilterSupport() {
    }

    /** 维度值过滤（axisValue 对应的维度列）；region/customerType 维度由编排方走归一化过滤，不在此处理。 */
    public static void appendDimensionFilter(StringBuilder where, Map<String, Object> params,
                                             String dimension, String axisValue) {
        if (axisValue == null || axisValue.isBlank()) {
            return;
        }
        switch (dimension) {
            case "time" -> appendTimeFilter(where, params, axisValue);
            case "dept" -> {
                where.append(" AND u.department_name = :axisVal");
                params.put("axisVal", axisValue);
            }
            case "person" -> {
                where.append(" AND u.full_name = :axisVal");
                params.put("axisVal", axisValue);
            }
            case "projectType" -> {
                where.append(" AND t.project_type = :axisVal");
                params.put("axisVal", axisValue);
            }
            case "projectStatus" -> {
                where.append(" AND p.status = :axisVal");
                params.put("axisVal", axisValue);
            }
            case "tenderEntity" -> {
                where.append(" AND t.purchaser_name = :axisVal");
                params.put("axisVal", axisValue);
            }
            case "competitor" -> {
                where.append(" AND EXISTS (SELECT 1 FROM project_result pr2 JOIN project_result_competitor prc2 ON prc2.result_id = pr2.id WHERE pr2.project_id = p.id AND prc2.name = :axisVal)");
                params.put("axisVal", axisValue);
            }
            default -> {
            }
        }
    }

    /**
     * 时间轴格式分派：日 "YYYY-MM-DD"、ISO 周 "YYYY-Wxx"、月 "YYYY-MM"、年 "YYYY"，其余回退月匹配。
     */
    private static void appendTimeFilter(StringBuilder where, Map<String, Object> params, String axisValue) {
        if (axisValue.matches("\\d{4}-\\d{2}-\\d{2}")) {
            where.append(" AND DATE_FORMAT(p.created_at, '%Y-%m-%d') = :axisVal");
            params.put("axisVal", axisValue);
        } else if (axisValue.matches("\\d{4}-W\\d+")) {
            // ISO 周 "2026-W12" → YEARWEEK(p.created_at,1) 返回 YYYYWW
            String year = axisValue.substring(0, 4);
            String week = String.format("%02d", Integer.parseInt(axisValue.substring(6)));
            where.append(" AND YEARWEEK(p.created_at, 1) = :axisWeekVal");
            params.put("axisWeekVal", year + week);
        } else if (axisValue.matches("\\d{4}")) {
            where.append(" AND YEAR(p.created_at) = :axisVal");
            params.put("axisVal", axisValue);
        } else {
            // "YYYY-MM" 与 fallback 统一月格式
            where.append(" AND DATE_FORMAT(p.created_at, '%Y-%m') = :axisVal");
            params.put("axisVal", axisValue);
        }
    }

    /** 通用 IN 过滤：values 非空时追加 column IN (:paramName)。 */
    public static void appendInFilter(StringBuilder where, Map<String, Object> params,
                                      String column, String paramName, List<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        where.append(" AND ").append(column).append(" IN (:").append(paramName).append(")");
        params.put(paramName, values);
    }

    /**
     * 部门轴两级展示口径（与图表 bucketMapper 同源）：axisValue 是分桶标签
     * （勾选有二级的一级时为二级部门名），反解回所有映射到该标签的部门名集合做 IN 匹配，
     * 避免「图上柱子数字 ≠ 下钻明细条数」的口径分裂。仅当同时存在部门筛选选择时由
     * {@link TrendDrillDownService} 调用；无选中时仍走 appendDimensionFilter 的旧精确等值。
     */
    public static void appendDeptAxisFilter(StringBuilder where, Map<String, Object> params,
                                            AnalyticsDepartmentHierarchySupport.Prepared dept, String axisValue) {
        var mapper = dept.bucketMapper();
        Set<String> matchNames = new java.util.LinkedHashSet<>();
        matchNames.add(axisValue);
        for (String orgName : dept.allOrgNames()) {
            if (mapper.apply(orgName).equals(axisValue)) {
                matchNames.add(orgName);
            }
        }
        where.append(" AND u.department_name IN (:deptAxisVals)");
        params.put("deptAxisVals", matchNames);
    }
}
