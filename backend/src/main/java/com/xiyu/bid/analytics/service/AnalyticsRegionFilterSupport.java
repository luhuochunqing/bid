// Input: EntityManager, ProjectAccessScopeService, 选中省份列表
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
     *
     * @param regionIds 选中的省级单位列表（筛选下拉选项口径，如 "江苏省"）
     * @return null 表示未启用区域筛选（regionIds 为空）；空集表示无任何命中
     */
    static Set<Long> regionMatchedProjectIds(
            EntityManager entityManager,
            ProjectAccessScopeService projectAccessScopeService,
            List<String> regionIds) {
        if (regionIds == null || regionIds.isEmpty()) {
            return null;
        }

        // 项目级数据权限：非全局角色仅可见授权范围内项目（防御式兜底）
        Set<Long> scopeIds = AnalyticsProjectScopeSupport.scopedProjectIds(projectAccessScopeService);
        boolean allAccess = scopeIds == null;

        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                select distinct p.id, pid.headquarters_location, t.region
                from projects p
                left join project_initiation_details pid on pid.project_id = p.id
                left join tenders t on t.id = p.tender_id
                where (:allAccess = true or p.id in (:scopeIds))
                """)
                .setParameter("allAccess", allAccess)
                .setParameter("scopeIds", allAccess ? List.of(-1L) : scopeIds)
                .getResultList();

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
