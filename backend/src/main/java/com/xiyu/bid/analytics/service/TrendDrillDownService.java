// Input: EntityManager, ProjectAccessScopeService, FilterOptionsQueryService, HTTP drilldown requests
// Output: TrendDrillDownResponse DTO（部门 expand 零命中 fail-closed 返回空响应）
// Pos: Service/数据查询与下钻服务层（竞品维度中标数/wins 走「是否中标」列口径，specs/055；
//       维度过滤条件组装已拆至 TrendDrillDownFilterSupport）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.analytics.service;

import com.xiyu.bid.analytics.dto.TrendDrillDownResponse;
import com.xiyu.bid.service.ProjectAccessScopeService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * M1 趋势分析下钻查询（PRD 6.6）：按 X 轴维度值/系列名/日期范围/筛选条件查询项目列表；
 * 客户类型统一走 AnalyticsCustomerTypeFilterSupport 归一化口径（同图表分组），替代 coalesce 直匹配。
 */
@Service
@RequiredArgsConstructor
public class TrendDrillDownService {

    @PersistenceContext
    private EntityManager entityManager;
    private final ProjectAccessScopeService projectAccessScopeService;
    private final FilterOptionsQueryService filterOptionsQueryService;

    public TrendDrillDownResponse drillDown(
            String dimension,
            String axisValue,
            String seriesName,
            LocalDate startDate,
            LocalDate endDate,
            List<String> departments,
            List<String> persons,
            List<String> regions,
            List<String> customerTypes,
            List<String> projectTypes,
            List<String> statuses,
            List<String> tenderEntities,
            List<String> competitorNames,
            Integer page,
            Integer size
    ) {
        int pageSize = (size == null || size <= 0) ? 10 : Math.min(size, 200);
        int pageNum = (page == null || page <= 0) ? 1 : page;

        // 构建原生 SQL
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        Map<String, Object> params = new HashMap<>();

        // 项目级数据权限：非全局角色仅可见授权范围内项目（防御式兜底）
        Set<Long> scopeIds = AnalyticsProjectScopeSupport.scopedProjectIds(projectAccessScopeService);
        boolean allAccess = scopeIds == null;
        where.append(" AND (:allAccess = TRUE OR p.id IN (:scopeIds))");
        params.put("allAccess", allAccess);
        params.put("scopeIds", allAccess ? List.of(-1L) : scopeIds);

        // 维度值过滤；dept 轴+有勾选时改走 expand(柱名)，避免精确等值漏掉折进二级柱的三级
        boolean regionAxis = "region".equals(dimension) && axisValue != null && !axisValue.isBlank();
        boolean customerTypeAxis = "customerType".equals(dimension) && axisValue != null && !axisValue.isBlank();
        boolean hasDeptSelection = departments != null && !departments.isEmpty();
        boolean deptAxis = "dept".equals(dimension) && axisValue != null && !axisValue.isBlank();
        if (!regionAxis && !customerTypeAxis && !(deptAxis && hasDeptSelection)) {
            TrendDrillDownFilterSupport.appendDimensionFilter(where, params, dimension, axisValue);
        }

        if (startDate != null) {
            where.append(" AND p.created_at >= :startDate");
            params.put("startDate", startDate.atStartOfDay());
        }
        if (endDate != null) {
            where.append(" AND p.created_at <= :endDate");
            params.put("endDate", endDate.plusDays(1).atStartOfDay());
        }

        // 系列名过滤：投标数=全部项目（无附加状态条件，与图表口径对齐）。
        // 中标数：非竞品维度=项目级 status=WON；竞品维度（specs/055）=结果确认「是否中标」列已中标
        // （TRIM(notes) 精确匹配 CompetitorWonStatusSupport.WON_LABEL，与图表 computeCompetitorTrend 同源，
        // 避免「图上数字与下钻明细对不上」）；axisValue 缺失时回退筛选器竞品名集合，再缺省则不限竞品名。
        if ("中标数".equals(seriesName)) {
            if ("competitor".equals(dimension)) {
                String wonExists = " AND EXISTS (SELECT 1 FROM project_result prw JOIN project_result_competitor prcw"
                        + " ON prcw.result_id = prw.id WHERE prw.project_id = p.id";
                if (axisValue != null && !axisValue.isBlank()) {
                    where.append(wonExists).append(" AND prcw.name = :wonAxisVal AND TRIM(prcw.notes) = :wonLabel)");
                    params.put("wonAxisVal", axisValue);
                } else if (competitorNames != null && !competitorNames.isEmpty()) {
                    where.append(wonExists).append(" AND prcw.name IN (:competitorNames) AND TRIM(prcw.notes) = :wonLabel)");
                    params.put("competitorNames", competitorNames);
                } else {
                    where.append(wonExists).append(" AND TRIM(prcw.notes) = :wonLabel)");
                }
                params.put("wonLabel", CompetitorWonStatusSupport.WON_LABEL);
            } else {
                where.append(" AND p.status = 'WON'");
            }
        }

        // 通用筛选：客户类型与分组同口径归一化匹配；axisValue 与 customerTypes 取交集后只算一次
        if (customerTypeAxis || (customerTypes != null && !customerTypes.isEmpty())) {
            if (customerTypeAxis && customerTypes != null && !customerTypes.isEmpty() && !customerTypes.contains(axisValue)) {
                return emptyResponse(pageNum, pageSize); // 交集为空
            }
            List<String> effectiveCustomerTypes = customerTypeAxis ? List.of(axisValue) : customerTypes;
            Set<Long> customerTypeMatchedIds = AnalyticsCustomerTypeFilterSupport.customerTypeMatchedProjectIds(
                    entityManager, projectAccessScopeService, effectiveCustomerTypes, startDate, endDate);
            if (customerTypeMatchedIds.isEmpty()) return emptyResponse(pageNum, pageSize);
            where.append(" AND p.id IN (:customerTypeProjectIds)");
            params.put("customerTypeProjectIds", customerTypeMatchedIds);
        }
        TrendDrillDownFilterSupport.appendInFilter(where, params, "t.project_type", "projectTypes", projectTypes);
        TrendDrillDownFilterSupport.appendInFilter(where, params, "p.status", "statuses", statuses);
        // 区域过滤：与图表分组同口径的省份归一化匹配（见 AnalyticsRegionFilterSupport）。
        // axisValue（点击柱子省份）与 regions（筛选器省份）取交集后只计算一次，避免重复全表回捞
        if (regionAxis || (regions != null && !regions.isEmpty())) {
            if (regionAxis && regions != null && !regions.isEmpty() && !regions.contains(axisValue)) {
                return emptyResponse(pageNum, pageSize); // 交集为空
            }
            List<String> effectiveRegions = regionAxis ? List.of(axisValue) : regions;
            Set<Long> regionMatchedIds = AnalyticsRegionFilterSupport.regionMatchedProjectIds(
                    entityManager, projectAccessScopeService, effectiveRegions, startDate, endDate);
            if (regionMatchedIds.isEmpty()) return emptyResponse(pageNum, pageSize);
            where.append(" AND p.id IN (:regionProjectIds)");
            params.put("regionProjectIds", regionMatchedIds);
        }

        if (hasDeptSelection) {
            var nodes = filterOptionsQueryService.loadDepartmentNodes();
            List<String> expandedDepts = AnalyticsDepartmentHierarchySupport.expand(nodes, departments);
            // 空列表=零命中 → fail-closed 直接返回空响应，避免拼出空 IN
            if (expandedDepts == null || expandedDepts.isEmpty()) {
                return emptyResponse(pageNum, pageSize);
            }
            where.append(" AND u.department_name IN (:departments)");
            params.put("departments", expandedDepts);
            if (deptAxis) {
                List<String> axisVals = AnalyticsDepartmentHierarchySupport.expand(nodes, List.of(axisValue));
                if (axisVals == null || axisVals.isEmpty()) {
                    return emptyResponse(pageNum, pageSize);
                }
                where.append(" AND u.department_name IN (:deptAxisVals)");
                params.put("deptAxisVals", axisVals);
            }
        }

        // 人员过滤（通过 manager_id → users.full_name）
        if (persons != null && !persons.isEmpty()) {
            where.append(" AND u.full_name IN (:persons)");
            params.put("persons", persons);
        }

        // 竞品公司过滤（通过 project_result_competitor.name）
        if (competitorNames != null && !competitorNames.isEmpty()) {
            where.append(" AND EXISTS (SELECT 1 FROM project_result pr2 JOIN project_result_competitor prc2 ON prc2.result_id = pr2.id WHERE pr2.project_id = p.id AND prc2.name IN (:competitorNames))");
            params.put("competitorNames", competitorNames);
        }

        // COUNT 查询
        String countSql = "SELECT COUNT(DISTINCT p.id) FROM projects p" +
                " LEFT JOIN tenders t ON t.id = p.tender_id" +
                " LEFT JOIN project_initiation_details pid ON pid.project_id = p.id" +
                " LEFT JOIN users u ON u.id = p.manager_id" + where;

        Query countQuery = entityManager.createNativeQuery(countSql);
        params.forEach(countQuery::setParameter);
        Long total = ((Number) countQuery.getSingleResult()).longValue();

        if (total == 0) {
            return emptyResponse(pageNum, pageSize);
        }

        // 数据查询（分页）
        // 字段取值口径与项目列表页一致（ProjectListEnrichmentSupport.populateFromTender）：
        //   项目负责人 = COALESCE(pid.project_leader_name, t.project_manager_name)
        //   投标负责人 = COALESCE(pid.bidding_leader_name, t.bidding_person_name)
        int offset = (pageNum - 1) * pageSize;
        String dataSql = "SELECT p.id, p.name, p.status, p.manager_id, p.created_at," +
                " COALESCE(pid.project_leader_name, t.project_manager_name) AS manager_name," +
                " COALESCE(pid.bidding_leader_name, t.bidding_person_name) AS tech_leader_name," +
                " t.bid_opening_time AS open_time" +
                " FROM projects p" +
                " LEFT JOIN tenders t ON t.id = p.tender_id" +
                " LEFT JOIN project_initiation_details pid ON pid.project_id = p.id" +
                " LEFT JOIN users u ON u.id = p.manager_id" +
                where +
                " GROUP BY p.id, p.name, p.status, p.manager_id, p.created_at," +
                " pid.project_leader_name, t.project_manager_name," +
                " pid.bidding_leader_name, t.bidding_person_name, t.bid_opening_time" +
                " ORDER BY p.created_at DESC";

        Query dataQuery = entityManager.createNativeQuery(dataSql)
                .setFirstResult(offset)
                .setMaxResults(pageSize);
        params.forEach(dataQuery::setParameter);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = dataQuery.getResultList();

        List<TrendDrillDownResponse.DrillDownItem> items = new ArrayList<>();
        for (Object[] row : rows) {
            items.add(TrendDrillDownResponse.DrillDownItem.builder()
                    .projectId(row[0] != null ? ((Number) row[0]).longValue() : null)
                    .projectName((String) row[1])
                    .status((String) row[2])
                    .managerName((String) row[5])
                    .techLeaderName((String) row[6])
                    .openTime(row[7] != null ? ((java.util.Date) row[7]).toInstant()
                            // P2-3：固定东八区业务口径，避免服务器时区影响开标时间展示
                            .atZone(java.time.ZoneId.of("Asia/Shanghai"))
                            .toLocalDateTime() : null)
                    .build());
        }

        // 汇总：竞品 totalWins 按「是否中标」列（specs/055）；中标率非竞品/非项目状态 = 中标数/(已中标+未中标)，其余维持 中标数/投标数
        boolean isCompetitorDimension = "competitor".equals(dimension);
        long totalWins = isCompetitorDimension
                ? countCompetitorWins(where, params, axisValue != null && !axisValue.isBlank() ? axisValue : null)
                : countByStatus(where, params, "WON");
        long rateDenominator = TrendSeriesAssembler.usesOutcomeDenominator(dimension)
                ? totalWins + countByStatus(where, params, "LOST")
                : total;
        TrendDrillDownResponse.DrillDownSummary summary = TrendDrillDownResponse.DrillDownSummary.builder()
                .totalCount(total)
                .totalBids(total)
                .totalWins(totalWins)
                .winRate(TrendSeriesAssembler.ratio(totalWins, rateDenominator))
                .build();

        int totalPages = (int) Math.ceil((double) total / pageSize);

        return TrendDrillDownResponse.builder()
                .items(items)
                .summary(summary)
                .pagination(TrendDrillDownResponse.Pagination.builder()
                        .page(pageNum)
                        .size(pageSize)
                        .total(total)
                        .totalPages(totalPages)
                        .build())
                .build();
    }

    /** 竞品维度汇总：totalWins 按 notes「是否中标」口径（specs/055），axisName 非空时限定该竞品名。 */
    private long countCompetitorWins(StringBuilder where, Map<String, Object> params, String axisName) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(DISTINCT p.id) FROM projects p")
                .append(" LEFT JOIN tenders t ON t.id = p.tender_id")
                .append(" LEFT JOIN project_initiation_details pid ON pid.project_id = p.id")
                .append(" LEFT JOIN users u ON u.id = p.manager_id")
                .append(where)
                .append(" AND EXISTS (SELECT 1 FROM project_result prw JOIN project_result_competitor prcw")
                .append(" ON prcw.result_id = prw.id WHERE prw.project_id = p.id");
        if (axisName != null) {
            sql.append(" AND prcw.name = :wonSummaryAxisVal");
            params.put("wonSummaryAxisVal", axisName);
        }
        sql.append(" AND TRIM(prcw.notes) = :wonSummaryLabel)");
        Query q = entityManager.createNativeQuery(sql.toString());
        params.forEach(q::setParameter);
        q.setParameter("wonSummaryLabel", CompetitorWonStatusSupport.WON_LABEL);
        return ((Number) q.getSingleResult()).longValue();
    }

    /** 按项目状态统计当前 where 下项目数（汇总用；状态不写入共享 params，防止多次统计时参数污染）。 */
    private long countByStatus(StringBuilder where, Map<String, Object> params, String status) {
        String sql = "SELECT COUNT(DISTINCT p.id) FROM projects p" +
                " LEFT JOIN tenders t ON t.id = p.tender_id" +
                " LEFT JOIN project_initiation_details pid ON pid.project_id = p.id" +
                " LEFT JOIN users u ON u.id = p.manager_id" +
                where + " AND p.status = :summaryStatus";
        Query q = entityManager.createNativeQuery(sql);
        params.forEach(q::setParameter);
        q.setParameter("summaryStatus", status);
        return ((Number) q.getSingleResult()).longValue();
    }

    private TrendDrillDownResponse emptyResponse(int page, int size) {
        return TrendDrillDownResponse.builder()
                .items(Collections.emptyList())
                .summary(TrendDrillDownResponse.DrillDownSummary.builder()
                        .totalCount(0L).totalBids(0L).totalWins(0L).winRate(0.0).build())
                .pagination(TrendDrillDownResponse.Pagination.builder()
                        .page(page).size(size).total(0L).totalPages(0).build())
                .build();
    }
}