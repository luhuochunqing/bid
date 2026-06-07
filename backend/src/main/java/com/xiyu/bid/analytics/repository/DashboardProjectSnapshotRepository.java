package com.xiyu.bid.analytics.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Repository for basic project snapshot queries.
 * These queries return project data with team member IDs (not full User objects).
 * For optimized queries with full team member User data, use {@link ProjectSnapshotWithTeamRepository}.
 * For supporting queries (Task, Document, Export), use {@link SnapshotSupportingQueryRepository}.
 */
@Repository
@Transactional(readOnly = true)
public class DashboardProjectSnapshotRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public List<DashboardAnalyticsRepository.ProjectSnapshotRow> fetchProjectSnapshotRowsByTenderIds(
            Collection<Long> tenderIds
    ) {
        if (tenderIds == null || tenderIds.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery("""
                        select new com.xiyu.bid.analytics.repository.DashboardAnalyticsRepository$ProjectSnapshotRow(
                            p.id,
                            p.tenderId,
                            p.name,
                            p.status,
                            p.managerId,
                            u.fullName,
                            t.source,
                            t.budget,
                            coalesce(p.startDate, p.createdAt),
                            p.endDate,
                            tm
                        )
                        from Project p
                        left join Tender t on t.id = p.tenderId
                        left join User u on u.id = p.managerId
                        left join p.teamMembers tm
                        where p.tenderId in :tenderIds
                        order by coalesce(p.startDate, p.createdAt) desc, p.id desc
                        """, DashboardAnalyticsRepository.ProjectSnapshotRow.class)
                .setParameter("tenderIds", tenderIds)
                .getResultList();
    }

    public List<DashboardAnalyticsRepository.ProjectSnapshotRow> fetchProjectSnapshotRowsByTenderIdsAndProjectIds(
            Collection<Long> tenderIds,
            Set<Long> projectIds
    ) {
        if (tenderIds == null || tenderIds.isEmpty() || projectIds == null || projectIds.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery("""
                        select new com.xiyu.bid.analytics.repository.DashboardAnalyticsRepository$ProjectSnapshotRow(
                            p.id,
                            p.tenderId,
                            p.name,
                            p.status,
                            p.managerId,
                            u.fullName,
                            t.source,
                            t.budget,
                            coalesce(p.startDate, p.createdAt),
                            p.endDate,
                            tm
                        )
                        from Project p
                        left join Tender t on t.id = p.tenderId
                        left join User u on u.id = p.managerId
                        left join p.teamMembers tm
                        where p.id in :projectIds and p.tenderId in :tenderIds
                        order by coalesce(p.startDate, p.createdAt) desc, p.id desc
                        """, DashboardAnalyticsRepository.ProjectSnapshotRow.class)
                .setParameter("projectIds", projectIds)
                .setParameter("tenderIds", tenderIds)
                .getResultList();
    }

    public List<DashboardAnalyticsRepository.ProjectSnapshotRow> fetchProjectSnapshotRowsByDateRange(
            LocalDate startDate,
            LocalDate endDate
    ) {
        return entityManager.createQuery("""
                        select new com.xiyu.bid.analytics.repository.DashboardAnalyticsRepository$ProjectSnapshotRow(
                            p.id,
                            p.tenderId,
                            p.name,
                            p.status,
                            p.managerId,
                            u.fullName,
                            t.source,
                            t.budget,
                            coalesce(p.startDate, p.createdAt),
                            p.endDate,
                            tm
                        )
                        from Project p
                        left join Tender t on t.id = p.tenderId
                        left join User u on u.id = p.managerId
                        left join p.teamMembers tm
                        where (:startDate is null or coalesce(p.startDate, p.createdAt) >= :startDate)
                          and (:endDate is null or coalesce(p.startDate, p.createdAt) <= :endDate)
                        order by coalesce(p.startDate, p.createdAt) desc, p.id desc
                        """, DashboardAnalyticsRepository.ProjectSnapshotRow.class)
                .setParameter("startDate", startDate == null ? null : startDate.atStartOfDay())
                .setParameter("endDate", endDate == null ? null : endDate.atTime(23, 59, 59))
                .getResultList();
    }

    public List<DashboardAnalyticsRepository.ProjectSnapshotRow> fetchProjectSnapshotRowsByProjectIdsAndDateRange(
            Set<Long> projectIds,
            LocalDate startDate,
            LocalDate endDate
    ) {
        if (projectIds == null || projectIds.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery("""
                        select new com.xiyu.bid.analytics.repository.DashboardAnalyticsRepository$ProjectSnapshotRow(
                            p.id,
                            p.tenderId,
                            p.name,
                            p.status,
                            p.managerId,
                            u.fullName,
                            t.source,
                            t.budget,
                            coalesce(p.startDate, p.createdAt),
                            p.endDate,
                            tm
                        )
                        from Project p
                        left join Tender t on t.id = p.tenderId
                        left join User u on u.id = p.managerId
                        left join p.teamMembers tm
                        where p.id in :projectIds
                          and (:startDate is null or coalesce(p.startDate, p.createdAt) >= :startDate)
                          and (:endDate is null or coalesce(p.startDate, p.createdAt) <= :endDate)
                        order by coalesce(p.startDate, p.createdAt) desc, p.id desc
                        """, DashboardAnalyticsRepository.ProjectSnapshotRow.class)
                .setParameter("projectIds", projectIds)
                .setParameter("startDate", startDate == null ? null : startDate.atStartOfDay())
                .setParameter("endDate", endDate == null ? null : endDate.atTime(23, 59, 59))
                .getResultList();
    }
}
