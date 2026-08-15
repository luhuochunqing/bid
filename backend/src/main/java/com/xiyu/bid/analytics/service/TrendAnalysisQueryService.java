package com.xiyu.bid.analytics.service;

import com.xiyu.bid.entity.Project;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TrendAnalysisQueryService {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 查询项目趋势数据，按指定时间粒度（day/week/month/year）提取时间字段。
     * timeDimension 参数决定 SELECT 中提取哪些时间字段：
     *   - month: year, month, week=0, day=0
     *   - week:  year, month=0, week, day=0
     *   - day:   year, month, week=0, day
     *   - year:  year, month=0, week=0, day=0
     */
    List<TimeDimensionRow> fetchTimeTrendRows(
            LocalDate startDate,
            LocalDate endDate,
            String timeDimension,
            List<Long> departmentIds,
            List<Long> userIds,
            List<Long> regionIds,
            List<String> customerTypes,
            List<String> projectTypes,
            List<Project.Status> statuses
    ) {
        // 根据 timeDimension 构建 SELECT 中的时间字段表达式
        String selectExpr = switch (timeDimension != null ? timeDimension : "month") {
            case "year" -> "function('year', p.createdAt), 0, 0, 0";
            case "week" -> "function('year', p.createdAt), 0, function('week', p.createdAt, 1), 0";
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
                        where 1=1
                        """);

        if (startDate != null) {
            jpql.append(" and p.createdAt >= :startDate");
        }
        if (endDate != null) {
            jpql.append(" and p.createdAt <= :endDate");
        }
        if (projectTypes != null && !projectTypes.isEmpty()) {
            jpql.append(" and t.projectType in :projectTypes");
        }
        if (customerTypes != null && !customerTypes.isEmpty()) {
            jpql.append(" and p.customerType in :customerTypes");
        }
        if (statuses != null && !statuses.isEmpty()) {
            jpql.append(" and p.status in :statuses");
        }

        var query = entityManager.createQuery(jpql.toString(), TimeDimensionRow.class);

        if (startDate != null) {
            query.setParameter("startDate", startDate.atStartOfDay());
        }
        if (endDate != null) {
            query.setParameter("endDate", endDate.atTime(23, 59, 59));
        }
        if (projectTypes != null && !projectTypes.isEmpty()) {
            query.setParameter("projectTypes", projectTypes);
        }
        if (customerTypes != null && !customerTypes.isEmpty()) {
            query.setParameter("customerTypes", customerTypes);
        }
        if (statuses != null && !statuses.isEmpty()) {
            query.setParameter("statuses", statuses);
        }

        return query.getResultList();
    }

    /**
     * 查询项目总数（已中标+未中标+投标中+评标中）、投标中数、中标数、未中标数（按日期范围过滤）。
     */
    OverviewRow fetchOverviewRow(LocalDate startDate, LocalDate endDate) {
        StringBuilder jpql = new StringBuilder("""
                select new com.xiyu.bid.analytics.service.TrendAnalysisQueryService$OverviewRow(
                    sum(case when p.status in ('WON', 'LOST', 'BIDDING', 'EVALUATING') then 1 else 0 end),
                    sum(case when p.status = 'BIDDING' then 1 else 0 end),
                    sum(case when p.status = 'WON' then 1 else 0 end),
                    sum(case when p.status = 'LOST' then 1 else 0 end)
                )
                from Project p
                where 1=1
                """);

        if (startDate != null) {
            jpql.append(" and p.createdAt >= :startDate");
        }
        if (endDate != null) {
            jpql.append(" and p.createdAt <= :endDate");
        }

        var query = entityManager.createQuery(jpql.toString(), OverviewRow.class);

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
        String jpql = """
                select count(p)
                from Project p
                where p.createdAt >= :todayStart
                and p.createdAt <= :todayEnd
                """;

        LocalDate today = LocalDate.now();
        return entityManager.createQuery(jpql, Long.class)
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

    public record OverviewRow(
            Long totalCount,
            Long biddingCount,
            Long wonCount,
            Long notWonCount
    ) {
    }
}