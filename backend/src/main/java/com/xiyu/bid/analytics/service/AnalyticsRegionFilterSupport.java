// Input: EntityManager, ProjectAccessScopeService, 选中省份列表, 可选日期范围
// Output: 命中省份的项目 ID 集合（与图表分组同口径）
// Pos: Service/区域筛选口径支持（查询层助手，纯口径复用 RegionDimensionExtractor）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.analytics.service;

import com.xiyu.bid.service.ProjectAccessScopeService;
import jakarta.persistence.EntityManager;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 区域筛选口径支持：筛选与图表分组统一走 {@link RegionDimensionExtractor} 省份归一化口径。
 *
 * <p>历史问题：区域筛选用原始存储串的 {@code LIKE '省名%'} 前缀匹配，而图表分组用
 * Java 端省份归一化函数，两套口径在历史存量格式（无后缀短省名、自由文本）上不一致，
 * 导致"筛选后数字 ≠ 图上柱子之和"。现改为：先取回原始区域双源值，
 * 用与分组完全相同的提取函数归一化后按省份集合匹配，筛出的项目 ID 用于下游查询过滤。</p>
 */
final class AnalyticsRegionFilterSupport {

    private AnalyticsRegionFilterSupport() {
    }

    /**
     * 按省份归一化口径计算命中所选省份的项目 ID 集合。
     * 取值口径与区域维度分组一致：总部所在地非空白优先，空白回退标讯区域（{@code resolveRawLocation}），
     * 再经 {@code extractProvince} 归一化为省级全称后比对。
     * 可选日期范围下推到 {@code p.created_at}（与各查询的日期过滤同口径），
     * 用于收窄命中集；结果仍是下游结果的超集，语义安全。
     *
     * @param regionIds 选中的省级单位列表（筛选下拉选项口径，如 "江苏省"）
     * @param startDate 创建日期下界（null = 不限）
     * @param endDate   创建日期上界（null = 不限）
     * @return null 表示未启用区域筛选（regionIds 为空）；空集表示无任何命中
     */
    static Set<Long> regionMatchedProjectIds(
            EntityManager entityManager,
            ProjectAccessScopeService projectAccessScopeService,
            List<String> regionIds,
            java.time.LocalDate startDate,
            java.time.LocalDate endDate) {
        if (regionIds == null || regionIds.isEmpty()) {
            return null;
        }

        // 项目级数据权限：非全局角色仅可见授权范围内项目（防御式兜底）
        Set<Long> scopeIds = AnalyticsProjectScopeSupport.scopedProjectIds(projectAccessScopeService);
        boolean allAccess = scopeIds == null;

        StringBuilder sql = new StringBuilder("""
                select distinct p.id, pid.headquarters_location, t.region
                from projects p
                left join project_initiation_details pid on pid.project_id = p.id
                left join tenders t on t.id = p.tender_id
                where (:allAccess = true or p.id in (:scopeIds))
                """);
        if (startDate != null) {
            sql.append(" and p.created_at >= :startDate");
        }
        if (endDate != null) {
            sql.append(" and p.created_at <= :endDate");
        }

        var query = entityManager.createNativeQuery(sql.toString());
        query.setParameter("allAccess", allAccess);
        query.setParameter("scopeIds", allAccess ? List.of(-1L) : scopeIds);
        if (startDate != null) {
            query.setParameter("startDate", startDate.atStartOfDay());
        }
        if (endDate != null) {
            // 上界放宽为次日零点（与下钻主查询同口径）：命中集是下游结果的超集恒安全，
            // 若用 23:59:59 会漏掉 (23:59:59, 次日零点) 区间内被下游命中的项目（超集不变式失效）
            query.setParameter("endDate", endDate.plusDays(1).atStartOfDay());
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        Set<String> wantedProvinces = Set.copyOf(regionIds);
        Set<Long> matched = new LinkedHashSet<>();
        for (Object[] row : rows) {
            String rawLocation = RegionDimensionExtractor.resolveRawLocation(
                    (String) row[1], (String) row[2]);
            if (rawLocation != null
                    && wantedProvinces.contains(RegionDimensionExtractor.extractProvince(rawLocation))) {
                matched.add(((Number) row[0]).longValue());
            }
        }
        return matched;
    }
}
