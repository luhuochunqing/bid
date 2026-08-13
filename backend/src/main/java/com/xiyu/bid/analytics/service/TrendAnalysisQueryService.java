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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TrendAnalysisQueryService {

    @PersistenceContext
    private EntityManager entityManager;
    private final ProjectAccessScopeService projectAccessScopeService;

    /**
     * 查询项目趋势数据，按时间（年-月）分组。
     */
    List<TimeDimensionRow> fetchTimeTrendRows(
            LocalDate startDate,
            LocalDate endDate,
            List<Long> departmentIds,
            List<Long> userIds,
            List<Long> regionIds,
            List<String> customerTypes,
            List<String> projectTypes,
            List<Project.Status> statuses
    ) {
        Set<Long> projectIds = scopedProjectIds();
        if (projectIds != null && projectIds.isEmpty()) {
            return List.of();
        }
        Set<Long> queryProjectIds = projectIds == null ? Set.of() : projectIds;

        // 构建动态 WHERE 条件
        StringBuilder jpql = new StringBuilder("""
                select new com.xiyu.bid.analytics.service.TrendAnalysisQueryService$TimeDimensionRow(
                    function('year', p.createdAt),
                    function('month', p.createdAt),
                    p.id,
                    p.status
                )
                from Project p
                left join Tender t on t.id = p.tenderId
                where (:allAccess = true or p.id in :projectIds)
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

        var query = entityManager.createQuery(jpql.toString(), TimeDimensionRow.class)
                .setParameter("allAccess", projectIds == null)
                .setParameter("projectIds", queryProjectIds);

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
     * 查询项目总数、投标中数、中标数（按日期范围过滤）。
     */
    OverviewRow fetchOverviewRow(LocalDate startDate, LocalDate endDate) {
        Set<Long> projectIds = scopedProjectIds();
        if (projectIds != null && projectIds.isEmpty()) {
            return new OverviewRow(0L, 0L, 0L, 0L);
        }
        Set<Long> queryProjectIds = projectIds == null ? Set.of() : projectIds;

        StringBuilder jpql = new StringBuilder("""
                select new com.xiyu.bid.analytics.service.TrendAnalysisQueryService$OverviewRow(
                    count(p),
                    sum(case when p.status = 'BIDDING' then 1 else 0 end),
                    sum(case when p.status = 'WON' then 1 else 0 end),
                    0L
                )
                from Project p
                where (:allAccess = true or p.id in :projectIds)
                """);

        if (startDate != null) {
            jpql.append(" and p.createdAt >= :startDate");
        }
        if (endDate != null) {
            jpql.append(" and p.createdAt <= :endDate");
        }

        var query = entityManager.createQuery(jpql.toString(), OverviewRow.class)
                .setParameter("allAccess", projectIds == null)
                .setParameter("projectIds", queryProjectIds);

        if (startDate != null) {
            query.setParameter("startDate", startDate.atStartOfDay());
        }
        if (endDate != null) {
            query.setParameter("endDate", endDate.atTime(23, 59, 59));
        }

        return query.getSingleResult();
    }

    private Set<Long> scopedProjectIds() {
        if (projectAccessScopeService.currentUserHasAdminAccess()) {
            return null;
        }
        List<Long> allowedIds = projectAccessScopeService.getAllowedProjectIdsForCurrentUser();
        if (allowedIds == null || allowedIds.isEmpty()) {
            return Set.of();
        }
        return allowedIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public record TimeDimensionRow(
            Integer year,
            Integer month,
            Long projectId,
            Project.Status status
    ) {
    }

    public record OverviewRow(
            Long totalCount,
            Long biddingCount,
            Long wonCount,
            Long reserved
    ) {
    }
}