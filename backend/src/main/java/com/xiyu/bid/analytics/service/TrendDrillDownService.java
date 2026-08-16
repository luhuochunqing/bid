package com.xiyu.bid.analytics.service;

import com.xiyu.bid.analytics.dto.TrendDrillDownResponse;
import com.xiyu.bid.service.ProjectAccessScopeService;
import jakarta.persistence.EntityManager;
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
 * M1 趋势分析下钻查询（PRD 6.6）。
 * 根据当前 X 轴维度值、系列名、日期范围和其他筛选条件查询项目列表。
 */
@Service
@RequiredArgsConstructor
public class TrendDrillDownService {

    private final EntityManager entityManager;
    private final ProjectAccessScopeService projectAccessScopeService;

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

        // 维度值过滤（axisValue 对应的维度列）
        appendDimensionFilter(where, params, dimension, axisValue);

        // 日期范围
        if (startDate != null) {
            where.append(" AND p.created_at >= :startDate");
            params.put("startDate", startDate.atStartOfDay());
        }
        if (endDate != null) {
            where.append(" AND p.created_at <= :endDate");
            params.put("endDate", endDate.plusDays(1).atStartOfDay());
        }

        // 系列名过滤：投标数=已中标(WON)+未中标(LOST)，中标数=WON
        if ("投标数".equals(seriesName)) {
            where.append(" AND p.status IN ('WON', 'LOST')");
        } else if ("中标数".equals(seriesName)) {
            where.append(" AND p.status = 'WON'");
        }

        // 通用筛选条件
        appendInFilter(where, params, "p.customer_type", "customerTypes", customerTypes);
        appendInFilter(where, params, "t.project_type", "projectTypes", projectTypes);
        appendInFilter(where, params, "p.status", "statuses", statuses);
        appendInFilter(where, params, "t.purchaser_name", "tenderEntities", tenderEntities);
        appendInFilter(where, params, "pid.headquarters_location", "regions", regions);

        // 部门过滤（通过 manager_id → users.department_name）
        if (departments != null && !departments.isEmpty()) {
            where.append(" AND u.department_name IN (:departments)");
            params.put("departments", departments);
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

        // 汇总统计
        long totalWins = countByStatus(where, params);
        TrendDrillDownResponse.DrillDownSummary summary = TrendDrillDownResponse.DrillDownSummary.builder()
                .totalCount(total)
                .totalBids(total)
                .totalWins(totalWins)
                .winRate(total > 0 ? Math.round(totalWins * 1000.0 / total) / 10.0 : 0.0)
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

    private long countByStatus(StringBuilder where, Map<String, Object> params) {
        // 已通过 seriesName='中标数' 强制 status='WON' 的查询场景无需重复统计；
        // 此处用于汇总：基于当前 where 条件统计 WON 状态项目数（参数化避免 SQL 注入）
        String sql = "SELECT COUNT(DISTINCT p.id) FROM projects p" +
                " LEFT JOIN tenders t ON t.id = p.tender_id" +
                " LEFT JOIN project_initiation_details pid ON pid.project_id = p.id" +
                " LEFT JOIN users u ON u.id = p.manager_id" +
                where + " AND p.status = :wonStatus";
        Query q = entityManager.createNativeQuery(sql);
        params.forEach(q::setParameter);
        q.setParameter("wonStatus", "WON");
        return ((Number) q.getSingleResult()).longValue();
    }

    private void appendDimensionFilter(StringBuilder where, Map<String, Object> params,
                                       String dimension, String axisValue) {
        if (axisValue == null || axisValue.isBlank()) return;
        switch (dimension) {
            case "time" -> {
                // axisValue 格式判断：日/周/月/年
                if (axisValue.matches("\\d{4}-\\d{2}-\\d{2}")) {
                    // 日格式 "2026-03-15"
                    where.append(" AND DATE_FORMAT(p.created_at, '%Y-%m-%d') = :axisVal");
                } else if (axisValue.matches("\\d{4}-W\\d+")) {
                    // 周格式 "2026-W12" → YEARWEEK(p.created_at,1) 返回 YYYYWW
                    String year = axisValue.substring(0, 4);
                    String week = String.format("%02d", Integer.parseInt(axisValue.substring(6)));
                    where.append(" AND YEARWEEK(p.created_at, 1) = :axisWeekVal");
                    params.put("axisWeekVal", year + week);
                    return; // 提前返回，避免执行下面的 params.put
                } else if (axisValue.matches("\\d{4}-\\d{2}")) {
                    // 月格式 "2026-03"（原逻辑）
                    where.append(" AND DATE_FORMAT(p.created_at, '%Y-%m') = :axisVal");
                } else if (axisValue.matches("\\d{4}")) {
                    // 年格式 "2026"
                    where.append(" AND YEAR(p.created_at) = :axisVal");
                } else {
                    // fallback 月格式
                    where.append(" AND DATE_FORMAT(p.created_at, '%Y-%m') = :axisVal");
                }
                params.put("axisVal", axisValue);
            }
            case "dept" -> {
                where.append(" AND u.department_name = :axisVal");
                params.put("axisVal", axisValue);
            }
            case "person" -> {
                where.append(" AND u.full_name = :axisVal");
                params.put("axisVal", axisValue);
            }
            case "region" -> {
                where.append(" AND pid.headquarters_location LIKE :axisVal");
                params.put("axisVal", axisValue + "%");
            }
            case "customerType" -> {
                where.append(" AND p.customer_type = :axisVal");
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
            default -> {}
        }
    }

    private void appendInFilter(StringBuilder where, Map<String, Object> params,
                                String column, String paramName, List<String> values) {
        if (values == null || values.isEmpty()) return;
        where.append(" AND ").append(column).append(" IN (:").append(paramName).append(")");
        params.put(paramName, values);
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