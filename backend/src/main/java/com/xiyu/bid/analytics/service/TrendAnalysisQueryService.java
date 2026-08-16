package com.xiyu.bid.analytics.service;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.service.ProjectAccessScopeService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.IsoFields;
import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TrendAnalysisQueryService {

    /** 业务口径固定为东八区，避免服务器时区偏移导致"今日新增"口径漂移 */
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    @PersistenceContext
    private EntityManager entityManager;

    private final ProjectAccessScopeService projectAccessScopeService;

    /**
     * 查询项目趋势数据，按指定时间粒度（day/week/month/year）提取时间字段。
     * timeDimension 参数决定 SELECT 中提取哪些时间字段：
     *   - month: year, month, week=0, day=0
     *   - week:  ISO 周基准年/周号（Java 端从 createdAt 计算），month=0, day=0
     *   - day:   year, month, week=0, day
     *   - year:  year, month=0, week=0, day=0
     */
    List<TimeDimensionRow> fetchTimeTrendRows(
            TrendQueryCriteria criteria,
            String timeDimension
    ) {
        // P1-6：周维度在 Java 端按 ISO 周计算（与 buildContinuousKeys 的 IsoFields、
        // 下钻 YEARWEEK(date, 1) 口径对齐）。MySQL WEEK() 默认 mode 0（周日开始、0-53）
        // 与 ISO 周不一致；且 Hibernate function('week', ...) 仅支持单参数（lessons §114），
        // 无法传 mode，故改为取 createdAt 后在 Java 端计算。
        if ("week".equals(timeDimension)) {
            return fetchWeeklyRows(criteria).stream()
                    .map(r -> {
                        LocalDate date = r.createdAt() != null ? r.createdAt().toLocalDate() : null;
                        return new TimeDimensionRow(
                                date != null ? date.get(IsoFields.WEEK_BASED_YEAR) : null,
                                0,
                                date != null ? date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) : null,
                                0,
                                r.projectId(),
                                r.status());
                    })
                    .toList();
        }

        // 项目级数据权限：非全局角色仅可见授权范围内项目（防御式兜底）
        Set<Long> scopeIds = AnalyticsProjectScopeSupport.scopedProjectIds(projectAccessScopeService);
        boolean allAccess = scopeIds == null;

        // 根据 timeDimension 构建 SELECT 中的时间字段表达式
        String selectExpr = switch (timeDimension != null ? timeDimension : "month") {
            case "year" -> "function('year', p.createdAt), 0, 0, 0";
            case "day"  -> "function('year', p.createdAt), function('month', p.createdAt), 0, function('day', p.createdAt)";
            default     -> "function('year', p.createdAt), function('month', p.createdAt), 0, 0";
        };

        // 构建动态 SELECT + WHERE 条件
        StringBuilder jpql = new StringBuilder(
                "select new com.xiyu.bid.analytics.service.TrendAnalysisQueryService$TimeDimensionRow(")
                .append(selectExpr)
                .append("""
                        , p.id, p.status
                        )
                        from Project p
                        left join Tender t on t.id = p.tenderId
                        where (:allAccess = true or p.id in :scopeIds)
                        """);

        if (criteria.startDate() != null) {
            jpql.append(" and p.createdAt >= :startDate");
        }
        if (criteria.endDate() != null) {
            jpql.append(" and p.createdAt <= :endDate");
        }
        if (criteria.projectTypes() != null && !criteria.projectTypes().isEmpty()) {
            jpql.append(" and t.projectType in :projectTypes");
        }
        if (criteria.customerTypes() != null && !criteria.customerTypes().isEmpty()) {
            jpql.append(" and p.customerType in :customerTypes");
        }
        if (criteria.statuses() != null && !criteria.statuses().isEmpty()) {
            jpql.append(" and p.status in :statuses");
        }

        var query = entityManager.createQuery(jpql.toString(), TimeDimensionRow.class);

        query.setParameter("allAccess", allAccess);
        query.setParameter("scopeIds", allAccess ? Set.of(-1L) : scopeIds);
        if (criteria.startDate() != null) {
            query.setParameter("startDate", criteria.startDate().atStartOfDay());
        }
        if (criteria.endDate() != null) {
            query.setParameter("endDate", criteria.endDate().atTime(23, 59, 59));
        }
        if (criteria.projectTypes() != null && !criteria.projectTypes().isEmpty()) {
            query.setParameter("projectTypes", criteria.projectTypes());
        }
        if (criteria.customerTypes() != null && !criteria.customerTypes().isEmpty()) {
            query.setParameter("customerTypes", criteria.customerTypes());
        }
        if (criteria.statuses() != null && !criteria.statuses().isEmpty()) {
            query.setParameter("statuses", criteria.statuses());
        }

        return query.getResultList();
    }

    /**
     * 周维度专用：取 createdAt 原始时间戳，ISO 周字段在 Java 端计算（P1-6）。
     */
    private List<WeeklyRawRow> fetchWeeklyRows(TrendQueryCriteria criteria) {
        // 项目级数据权限：非全局角色仅可见授权范围内项目（防御式兜底）
        Set<Long> scopeIds = AnalyticsProjectScopeSupport.scopedProjectIds(projectAccessScopeService);
        boolean allAccess = scopeIds == null;

        StringBuilder jpql = new StringBuilder("""
                select new com.xiyu.bid.analytics.service.TrendAnalysisQueryService$WeeklyRawRow(
                    p.createdAt, p.id, p.status
                )
                from Project p
                left join Tender t on t.id = p.tenderId
                where (:allAccess = true or p.id in :scopeIds)
                """);

        if (criteria.startDate() != null) {
            jpql.append(" and p.createdAt >= :startDate");
        }
        if (criteria.endDate() != null) {
            jpql.append(" and p.createdAt <= :endDate");
        }
        if (criteria.projectTypes() != null && !criteria.projectTypes().isEmpty()) {
            jpql.append(" and t.projectType in :projectTypes");
        }
        if (criteria.customerTypes() != null && !criteria.customerTypes().isEmpty()) {
            jpql.append(" and p.customerType in :customerTypes");
        }
        if (criteria.statuses() != null && !criteria.statuses().isEmpty()) {
            jpql.append(" and p.status in :statuses");
        }

        var query = entityManager.createQuery(jpql.toString(), WeeklyRawRow.class);
        query.setParameter("allAccess", allAccess);
        query.setParameter("scopeIds", allAccess ? Set.of(-1L) : scopeIds);
        if (criteria.startDate() != null) {
            query.setParameter("startDate", criteria.startDate().atStartOfDay());
        }
        if (criteria.endDate() != null) {
            query.setParameter("endDate", criteria.endDate().atTime(23, 59, 59));
        }
        if (criteria.projectTypes() != null && !criteria.projectTypes().isEmpty()) {
            query.setParameter("projectTypes", criteria.projectTypes());
        }
        if (criteria.customerTypes() != null && !criteria.customerTypes().isEmpty()) {
            query.setParameter("customerTypes", criteria.customerTypes());
        }
        if (criteria.statuses() != null && !criteria.statuses().isEmpty()) {
            query.setParameter("statuses", criteria.statuses());
        }

        return query.getResultList();
    }

    /**
     * 查询项目总数（已中标+未中标+投标中+评标中）、投标中数、中标数、未中标数（按日期范围过滤）。
     */
    OverviewRow fetchOverviewRow(LocalDate startDate, LocalDate endDate) {
        Set<Long> scopeIds = AnalyticsProjectScopeSupport.scopedProjectIds(projectAccessScopeService);
        boolean allAccess = scopeIds == null;
        StringBuilder jpql = new StringBuilder("""
                select new com.xiyu.bid.analytics.service.TrendAnalysisQueryService$OverviewRow(
                    sum(case when p.status in ('WON', 'LOST', 'BIDDING', 'EVALUATING') then 1 else 0 end),
                    sum(case when p.status = 'BIDDING' then 1 else 0 end),
                    sum(case when p.status = 'WON' then 1 else 0 end),
                    sum(case when p.status = 'LOST' then 1 else 0 end)
                )
                from Project p
                where (:allAccess = true or p.id in :scopeIds)
                """);

        if (startDate != null) {
            jpql.append(" and p.createdAt >= :startDate");
        }
        if (endDate != null) {
            jpql.append(" and p.createdAt <= :endDate");
        }

        var query = entityManager.createQuery(jpql.toString(), OverviewRow.class);

        query.setParameter("allAccess", allAccess);
        query.setParameter("scopeIds", allAccess ? Set.of(-1L) : scopeIds);
        if (startDate != null) {
            query.setParameter("startDate", startDate.atStartOfDay());
        }
        if (endDate != null) {
            query.setParameter("endDate", endDate.atTime(23, 59, 59));
        }

        return query.getSingleResult();
    }

    /**
     * 查询今日新增项目数（PRD §3.1 投标总数卡片底部"今日新增"显示）。
     * 不受全局日期筛选范围影响，固定查询今天创建的项目数。
     */
    long fetchTodayNewCount() {
        Set<Long> scopeIds = AnalyticsProjectScopeSupport.scopedProjectIds(projectAccessScopeService);
        boolean allAccess = scopeIds == null;
        String jpql = """
                select count(p)
                from Project p
                where (:allAccess = true or p.id in :scopeIds)
                and p.createdAt >= :todayStart
                and p.createdAt <= :todayEnd
                """;

        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        return entityManager.createQuery(jpql, Long.class)
                .setParameter("allAccess", allAccess)
                .setParameter("scopeIds", allAccess ? Set.of(-1L) : scopeIds)
                .setParameter("todayStart", today.atStartOfDay())
                .setParameter("todayEnd", today.atTime(23, 59, 59))
                .getSingleResult();
    }

    public record TimeDimensionRow(
            Integer year,
            Integer month,
            Integer week,
            Integer day,
            Long projectId,
            Project.Status status
    ) {
    }

    /** 周维度中间行：仅携带原始 createdAt，ISO 周字段由 Java 端计算（P1-6） */
    public record WeeklyRawRow(
            LocalDateTime createdAt,
            Long projectId,
            Project.Status status
    ) {
    }

    public record OverviewRow(
            Long totalCount,
            Long biddingCount,
            Long wonCount,
            Long notWonCount
    ) {
    }
}