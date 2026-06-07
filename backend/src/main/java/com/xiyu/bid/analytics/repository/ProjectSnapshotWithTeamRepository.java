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
 * Repository for project snapshot queries with full team member User data.
 * These optimized queries eliminate N+1 by fetching project + team members in a single query.
 */
@Repository
@Transactional(readOnly = true)
public class ProjectSnapshotWithTeamRepository {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Optimized query that fetches project snapshots with FULL team member User data.
     */
    public List<DashboardAnalyticsRepository.ProjectSnapshotRowWithUsers> fetchProjectSnapshotRowsWithUsersByTenderIds(
            Collection<Long> tenderIds
    ) {
        if (tenderIds == null || tenderIds.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery("""
                        select new com.xiyu.bid.analytics.repository.DashboardAnalyticsRepository$ProjectSnapshotRowWithUsers(
                            p.id,
                            p.tenderId,
                            p.name,
                            p.status,
                            p.managerId,
                            manager.fullName,
                            t.source,
                            t.budget,
                            coalesce(p.startDate, p.createdAt),
                            p.endDate,
                            tmMember.id,
                            tmMember.id,
                            tmMember.fullName,
                            tmMember.username,
                            tmMember.roleProfile.code
                        )
                        from Project p
                        left join Tender t on t.id = p.tenderId
                        left join User manager on manager.id = p.managerId
                        left join User tmMember on tmMember.id member of p.teamMembers
                        where p.tenderId in :tenderIds
                        order by coalesce(p.startDate, p.createdAt) desc, p.id desc
                        """, DashboardAnalyticsRepository.ProjectSnapshotRowWithUsers.class)
                .setParameter("tenderIds", tenderIds)
                .getResultList();
    }

    public List<DashboardAnalyticsRepository.ProjectSnapshotRowWithUsers> fetchProjectSnapshotRowsWithUsersByTenderIdsAndProjectIds(
            Collection<Long> tenderIds,
            Set<Long> projectIds
    ) {
        if (tenderIds == null || tenderIds.isEmpty() || projectIds == null || projectIds.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery("""
                        select new com.xiyu.bid.analytics.repository.DashboardAnalyticsRepository$ProjectSnapshotRowWithUsers(
                            p.id,
                            p.tenderId,
                            p.name,
                            p.status,
                            p.managerId,
                            manager.fullName,
                            t.source,
                            t.budget,
                            coalesce(p.startDate, p.createdAt),
                            p.endDate,
                            tmMember.id,
                            tmMember.id,
                            tmMember.fullName,
                            tmMember.username,
                            tmMember.roleProfile.code
                        )
                        from Project p
                        left join Tender t on t.id = p.tenderId
                        left join User manager on manager.id = p.managerId
                        left join User tmMember on tmMember.id member of p.teamMembers
                        where p.id in :projectIds and p.tenderId in :tenderIds
                        order by coalesce(p.startDate, p.createdAt) desc, p.id desc
                        """, DashboardAnalyticsRepository.ProjectSnapshotRowWithUsers.class)
                .setParameter("projectIds", projectIds)
                .setParameter("tenderIds", tenderIds)
                .getResultList();
    }

    public List<DashboardAnalyticsRepository.ProjectSnapshotRowWithUsers> fetchProjectSnapshotRowsWithUsersByDateRange(
            LocalDate startDate,
            LocalDate endDate
    ) {
        return entityManager.createQuery("""
                        select new com.xiyu.bid.analytics.repository.DashboardAnalyticsRepository$ProjectSnapshotRowWithUsers(
                            p.id,
                            p.tenderId,
                            p.name,
                            p.status,
                            p.managerId,
                            manager.fullName,
                            t.source,
                            t.budget,
                            coalesce(p.startDate, p.createdAt),
                            p.endDate,
                            tmMember.id,
                            tmMember.id,
                            tmMember.fullName,
                            tmMember.username,
                            tmMember.roleProfile.code
                        )
                        from Project p
                        left join Tender t on t.id = p.tenderId
                        left join User manager on manager.id = p.managerId
                        left join User tmMember on tmMember.id member of p.teamMembers
                        where (:startDate is null or coalesce(p.startDate, p.createdAt) >= :startDate)
                          and (:endDate is null or coalesce(p.startDate, p.createdAt) <= :endDate)
                        order by coalesce(p.startDate, p.createdAt) desc, p.id desc
                        """, DashboardAnalyticsRepository.ProjectSnapshotRowWithUsers.class)
                .setParameter("startDate", startDate == null ? null : startDate.atStartOfDay())
                .setParameter("endDate", endDate == null ? null : endDate.atTime(23, 59, 59))
                .getResultList();
    }

    public List<DashboardAnalyticsRepository.ProjectSnapshotRowWithUsers> fetchProjectSnapshotRowsWithUsersByProjectIdsAndDateRange(
            Set<Long> projectIds,
            LocalDate startDate,
            LocalDate endDate
    ) {
        if (projectIds == null || projectIds.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery("""
                        select new com.xiyu.bid.analytics.repository.DashboardAnalyticsRepository$ProjectSnapshotRowWithUsers(
                            p.id,
                            p.tenderId,
                            p.name,
                            p.status,
                            p.managerId,
                            manager.fullName,
                            t.source,
                            t.budget,
                            coalesce(p.startDate, p.createdAt),
                            p.endDate,
                            tmMember.id,
                            tmMember.id,
                            tmMember.fullName,
                            tmMember.username,
                            tmMember.roleProfile.code
                        )
                        from Project p
                        left join Tender t on t.id = p.tenderId
                        left join User manager on manager.id = p.managerId
                        left join User tmMember on tmMember.id member of p.teamMembers
                        where p.id in :projectIds
                          and (:startDate is null or coalesce(p.startDate, p.createdAt) >= :startDate)
                          and (:endDate is null or coalesce(p.startDate, p.createdAt) <= :endDate)
                        order by coalesce(p.startDate, p.createdAt) desc, p.id desc
                        """, DashboardAnalyticsRepository.ProjectSnapshotRowWithUsers.class)
                .setParameter("projectIds", projectIds)
                .setParameter("startDate", startDate == null ? null : startDate.atStartOfDay())
                .setParameter("endDate", endDate == null ? null : endDate.atTime(23, 59, 59))
                .getResultList();
    }
}
