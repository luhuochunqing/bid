// Input: 下钻原生 SQL 的 where 构建器与参数表、dimension/axisValue 等请求参数
// Output: 追加维度值过滤与 IN 过滤后的 where/params（原位修改）
// Pos: Support/下钻 SQL 条件组装支撑类（无状态静态工具，specs/055 自 TrendDrillDownService 拆出）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.analytics.service;

import java.util.List;
import java.util.Map;

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
     * 部门轴两级展示口径（与图表分桶同源）：axisValue 是分桶标签
     * （勾选有二级的一级时为二级部门名），反解回所有归属该桶的部门名集合做 IN 匹配，
     * 避免「图上柱子数字 ≠ 下钻明细条数」的口径分裂。反解直接读 {@link
     * AnalyticsDepartmentHierarchySupport.Prepared#namesInBucket} 倒排（与正向映射同次构造），
     * 不再由本方遍历全量组织名反推——那样等于把同一口径写两遍，一侧演进另一侧会静默漂移。
     *
     * <p>仅当存在部门筛选选择时由 {@link TrendDrillDownService} 调用；无选中时仍走
     * {@link #appendDimensionFilter} 的旧精确等值。</p>
     */
    public static void appendDeptAxisFilter(StringBuilder where, Map<String, Object> params,
                                            AnalyticsDepartmentHierarchySupport.Prepared dept, String axisValue) {
        where.append(" AND u.department_name IN (:deptAxisVals)");
        params.put("deptAxisVals", dept.namesInBucket(axisValue));
    }
}
