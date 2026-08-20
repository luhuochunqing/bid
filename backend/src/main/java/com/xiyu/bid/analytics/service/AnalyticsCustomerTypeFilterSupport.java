// Input: EntityManager, ProjectAccessScopeService, 选中客户类型列表, 可选日期范围
// Output: 命中客户类型的项目 ID 集合（与图表分组同口径）
// Pos: Service/客户类型筛选口径支持（查询层助手，纯口径复用 CustomerTypeNormalizer）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.analytics.service;

import com.xiyu.bid.service.ProjectAccessScopeService;
import jakarta.persistence.EntityManager;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 客户类型筛选口径支持：筛选与图表分组统一走 {@link CustomerTypeNormalizer} 归一化口径。
 *
 * <p>历史问题：立项详情 customer_type 存英文枚举名（GOVERNMENT/CENTRAL_SOE 等），
 * 项目主表快照存中文文本，筛选用 SQL {@code coalesce(...) IN (中文值)} 直匹配原始串，
 * 英文枚举项目全部漏统计；维度分组也透传原始串导致 X 轴显示英文。现改为：
 * 先取回双源原始值，用与分组完全相同的归一化函数匹配后，
 * 筛出的项目 ID 用于下游查询过滤。</p>
 */
final class AnalyticsCustomerTypeFilterSupport {

    private AnalyticsCustomerTypeFilterSupport() {
    }

    /**
     * 按归一化口径计算命中所选客户类型的项目 ID 集合。
     * 取值口径与客户类型维度分组一致：{@code coalesce(nullif(pid.customer_type,''), p.customer_type)}，
     * 再经 {@link CustomerTypeNormalizer#normalize} 归一化为 5 种标准分类后比对。
     * 可选日期范围下推到 {@code p.created_at}（与各查询的日期过滤同口径），
     * 用于收窄命中集；结果仍是下游结果的超集，语义安全。
     *
     * @param customerTypes 选中的标准分类列表（筛选下拉选项口径，如 "央企"）
     * @param startDate     创建日期下界（null = 不限）
     * @param endDate       创建日期上界（null = 不限）
     * @return null 表示未启用客户类型筛选（customerTypes 为空）；空集表示无任何命中
     */
    static Set<Long> customerTypeMatchedProjectIds(
            EntityManager entityManager,
            ProjectAccessScopeService projectAccessScopeService,
            List<String> customerTypes,
            java.time.LocalDate startDate,
            java.time.LocalDate endDate) {
        if (customerTypes == null || customerTypes.isEmpty()) {
            return null;
        }

        // 项目级数据权限：非全局角色仅可见授权范围内项目（防御式兜底）
        Set<Long> scopeIds = AnalyticsProjectScopeSupport.scopedProjectIds(projectAccessScopeService);
        boolean allAccess = scopeIds == null;

        StringBuilder sql = new StringBuilder("""
                select distinct p.id, coalesce(nullif(pid.customer_type, ''), p.customer_type)
                from projects p
                left join project_initiation_details pid on pid.project_id = p.id
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

        Set<String> wantedTypes = Set.copyOf(customerTypes);
        Set<Long> matched = new LinkedHashSet<>();
        for (Object[] row : rows) {
            String normalized = CustomerTypeNormalizer.normalize((String) row[1]);
            if (normalized != null && wantedTypes.contains(normalized)) {
                matched.add(((Number) row[0]).longValue());
            }
        }
        return matched;
    }
}
