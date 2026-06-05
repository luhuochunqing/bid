package com.xiyu.bid.analytics.repository;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Repository
@Transactional(readOnly = true)
public class DashboardSummaryAnalyticsRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public DashboardAnalyticsRepository.OverviewSnapshot fetchOverviewSnapshot() {
        Long totalTenders = entityManager.createQuery("select count(t) from Tender t", Long.class)
                .getSingleResult();
        BigDecimal totalBudget = entityManager.createQuery("select sum(t.budget) from Tender t", BigDecimal.class)
                .getSingleResult();
        Long activeProjects = entityManager.createQuery(
                        "select count(p) from Project p where p.status not in :terminal",
                        Long.class)
                .setParameter("terminal",
                        List.of(Project.Status.WON, Project.Status.LOST, Project.Status.FAILED, Project.Status.ABANDONED))
                .getSingleResult();
        Long pendingTasks = entityManager.createQuery(
                        "select count(t) from Task t where t.status = :todo",
                        Long.class)
                .setParameter("todo", com.xiyu.bid.entity.Task.Status.TODO)
                .getSingleResult();
        Long bidTenderCount = entityManager.createQuery(
                        "select count(t) from Tender t where t.status = :bidded",
                        Long.class)
                .setParameter("bidded", Tender.Status.WON)
                .getSingleResult();
        Long winningProjectCount = entityManager.createQuery(
                        "select count(p) from Project p where p.status in :winningStatuses",
                        Long.class)
                .setParameter(
                        "winningStatuses",
                        List.of(Project.Status.BIDDING, Project.Status.EVALUATING)
                )
                .getSingleResult();

        return overview(
                totalTenders,
                totalBudget,
                activeProjects,
                pendingTasks,
                bidTenderCount,
                winningProjectCount
        );
    }

    public DashboardAnalyticsRepository.OverviewSnapshot fetchOverviewSnapshotByProjectIds(Set<Long> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) {
            return emptyOverview();
        }
        Long totalTenders = entityManager.createQuery("""
                        select count(distinct t.id)
                        from Project p
                        left join Tender t on t.id = p.tenderId
                        where p.id in :projectIds and t.id is not null
                        """, Long.class)
                .setParameter("projectIds", projectIds)
                .getSingleResult();
        BigDecimal totalBudget = entityManager.createQuery("""
                        select sum(coalesce(p.budget, t.budget))
                        from Project p
                        left join Tender t on t.id = p.tenderId
                        where p.id in :projectIds
                        """, BigDecimal.class)
                .setParameter("projectIds", projectIds)
                .getSingleResult();
        Long activeProjects = entityManager.createQuery("""
                        select count(p)
                        from Project p
                        where p.id in :projectIds and p.status not in :terminal
                        """, Long.class)
                .setParameter("projectIds", projectIds)
                .setParameter("terminal",
                        List.of(Project.Status.WON, Project.Status.LOST, Project.Status.FAILED, Project.Status.ABANDONED))
                .getSingleResult();
        Long pendingTasks = entityManager.createQuery("""
                        select count(t)
                        from Task t
                        where t.projectId in :projectIds and t.status = :todo
                        """, Long.class)
                .setParameter("projectIds", projectIds)
                .setParameter("todo", com.xiyu.bid.entity.Task.Status.TODO)
                .getSingleResult();
        Long bidTenderCount = entityManager.createQuery("""
                        select count(distinct t.id)
                        from Project p
                        join Tender t on t.id = p.tenderId
                        where p.id in :projectIds and t.status = :bidded
                        """, Long.class)
                .setParameter("projectIds", projectIds)
                .setParameter("bidded", Tender.Status.WON)
                .getSingleResult();
        Long winningProjectCount = entityManager.createQuery("""
                        select count(p)
                        from Project p
                        where p.id in :projectIds and p.status in :winningStatuses
                        """, Long.class)
                .setParameter("projectIds", projectIds)
                .setParameter(
                        "winningStatuses",
                        List.of(Project.Status.BIDDING, Project.Status.EVALUATING)
                )
                .getSingleResult();

        return overview(
                totalTenders,
                totalBudget,
                activeProjects,
                pendingTasks,
                bidTenderCount,
                winningProjectCount
        );
    }

    public List<DashboardAnalyticsRepository.MonthlyTrendRow> fetchTenderTrends() {
        return entityManager.createQuery("""
                        select new com.xiyu.bid.analytics.repository.DashboardAnalyticsRepository$MonthlyTrendRow(
                            year(t.createdAt),
                            month(t.createdAt),
                            count(t),
                            sum(t.budget)
                        )
                        from Tender t
                        group by year(t.createdAt), month(t.createdAt)
                        order by year(t.createdAt), month(t.createdAt)
                        """, DashboardAnalyticsRepository.MonthlyTrendRow.class)
                .getResultList();
    }

    public List<DashboardAnalyticsRepository.MonthlyTrendRow> fetchTenderTrendsByProjectIds(Set<Long> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery("""
                        select new com.xiyu.bid.analytics.repository.DashboardAnalyticsRepository$MonthlyTrendRow(
                            year(t.createdAt),
                            month(t.createdAt),
                            count(distinct t.id),
                            sum(t.budget)
                        )
                        from Project p
                        join Tender t on t.id = p.tenderId
                        where p.id in :projectIds
                        group by year(t.createdAt), month(t.createdAt)
                        order by year(t.createdAt), month(t.createdAt)
                        """, DashboardAnalyticsRepository.MonthlyTrendRow.class)
                .setParameter("projectIds", projectIds)
                .getResultList();
    }

    public List<DashboardAnalyticsRepository.MonthlyTrendRow> fetchProjectTrends() {
        return entityManager.createQuery("""
                        select new com.xiyu.bid.analytics.repository.DashboardAnalyticsRepository$MonthlyTrendRow(
                            year(p.createdAt),
                            month(p.createdAt),
                            count(p),
                            null
                        )
                        from Project p
                        group by year(p.createdAt), month(p.createdAt)
                        order by year(p.createdAt), month(p.createdAt)
                        """, DashboardAnalyticsRepository.MonthlyTrendRow.class)
                .getResultList();
    }

    public List<DashboardAnalyticsRepository.MonthlyTrendRow> fetchProjectTrendsByProjectIds(Set<Long> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery("""
                        select new com.xiyu.bid.analytics.repository.DashboardAnalyticsRepository$MonthlyTrendRow(
                            year(p.createdAt),
                            month(p.createdAt),
                            count(p),
                            null
                        )
                        from Project p
                        where p.id in :projectIds
                        group by year(p.createdAt), month(p.createdAt)
                        order by year(p.createdAt), month(p.createdAt)
                        """, DashboardAnalyticsRepository.MonthlyTrendRow.class)
                .setParameter("projectIds", projectIds)
                .getResultList();
    }

    public List<DashboardAnalyticsRepository.StatusCountRow> fetchStatusDistribution() {
        return entityManager.createQuery("""
                        select new com.xiyu.bid.analytics.repository.DashboardAnalyticsRepository$StatusCountRow(
                            t.status,
                            count(t)
                        )
                        from Tender t
                        group by t.status
                        """, DashboardAnalyticsRepository.StatusCountRow.class)
                .getResultList();
    }

    public List<DashboardAnalyticsRepository.StatusCountRow> fetchStatusDistributionByProjectIds(Set<Long> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery("""
                        select new com.xiyu.bid.analytics.repository.DashboardAnalyticsRepository$StatusCountRow(
                            t.status,
                            count(distinct t.id)
                        )
                        from Project p
                        join Tender t on t.id = p.tenderId
                        where p.id in :projectIds
                        group by t.status
                        """, DashboardAnalyticsRepository.StatusCountRow.class)
                .setParameter("projectIds", projectIds)
                .getResultList();
    }

    public List<DashboardAnalyticsRepository.SourceAggregateRow> fetchSourceAggregates(int limit) {
        var query = entityManager.createQuery("""
                        select new com.xiyu.bid.analytics.repository.DashboardAnalyticsRepository$SourceAggregateRow(
                            t.source,
                            count(t),
                            sum(case when t.status = :bidded then 1 else 0 end),
                            sum(t.budget)
                        )
                        from Tender t
                        where t.source is not null
                        group by t.source
                        order by count(t) desc, t.source asc
                        """, DashboardAnalyticsRepository.SourceAggregateRow.class)
                .setParameter("bidded", Tender.Status.WON);
        if (limit > 0 && limit < Integer.MAX_VALUE) {
            query.setMaxResults(limit);
        }
        return query.getResultList();
    }

    public List<DashboardAnalyticsRepository.SourceAggregateRow> fetchSourceAggregatesByProjectIds(
            Set<Long> projectIds,
            int limit
    ) {
        if (projectIds == null || projectIds.isEmpty()) {
            return List.of();
        }
        var query = entityManager.createQuery("""
                        select new com.xiyu.bid.analytics.repository.DashboardAnalyticsRepository$SourceAggregateRow(
                            t.source,
                            count(distinct t.id),
                            sum(case when t.status = :bidded then 1 else 0 end),
                            sum(t.budget)
                        )
                        from Project p
                        join Tender t on t.id = p.tenderId
                        where p.id in :projectIds and t.source is not null
                        group by t.source
                        order by count(distinct t.id) desc, t.source asc
                        """, DashboardAnalyticsRepository.SourceAggregateRow.class)
                .setParameter("projectIds", projectIds)
                .setParameter("bidded", Tender.Status.WON);
        if (limit > 0 && limit < Integer.MAX_VALUE) {
            query.setMaxResults(limit);
        }
        return query.getResultList();
    }

    private DashboardAnalyticsRepository.OverviewSnapshot overview(
            Long totalTenders,
            BigDecimal totalBudget,
            Long activeProjects,
            Long pendingTasks,
            Long bidTenderCount,
            Long winningProjectCount
    ) {
        return new DashboardAnalyticsRepository.OverviewSnapshot(
                totalTenders == null ? 0L : totalTenders,
                totalBudget == null ? BigDecimal.ZERO : totalBudget,
                activeProjects == null ? 0L : activeProjects,
                pendingTasks == null ? 0L : pendingTasks,
                bidTenderCount == null ? 0L : bidTenderCount,
                winningProjectCount == null ? 0L : winningProjectCount
        );
    }

    private DashboardAnalyticsRepository.OverviewSnapshot emptyOverview() {
        return new DashboardAnalyticsRepository.OverviewSnapshot(0L, BigDecimal.ZERO, 0L, 0L, 0L, 0L);
    }
}
