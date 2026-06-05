package com.xiyu.bid.analytics.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Repository
@Transactional(readOnly = true)
public class DashboardDrillDownAnalyticsRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public List<DashboardAnalyticsRepository.RevenueDrillDownRow> fetchRevenueDrillDownRows(
            LocalDate startDate,
            LocalDate endDate
    ) {
        return entityManager.createQuery("""
                        select new com.xiyu.bid.analytics.repository.DashboardAnalyticsRepository$RevenueDrillDownRow(
                            t.id,
                            p.id,
                            t.title,
                            t.source,
                            t.status,
                            p.name,
                            p.status,
                            p.managerId,
                            u.fullName,
                            t.budget,
                            t.aiScore,
                            t.createdAt,
                            t.deadline
                        )
                        from Tender t
                        left join Project p on p.tenderId = t.id
                        left join User u on u.id = p.managerId
                        where (:startDate is null or t.createdAt >= :startDate)
                          and (:endDate is null or t.createdAt <= :endDate)
                        order by t.createdAt desc, t.id desc
                        """, DashboardAnalyticsRepository.RevenueDrillDownRow.class)
                .setParameter("startDate", startDate == null ? null : startDate.atStartOfDay())
                .setParameter("endDate", endDate == null ? null : endDate.atTime(23, 59, 59))
                .getResultList();
    }

    public List<DashboardAnalyticsRepository.RevenueDrillDownRow> fetchRevenueDrillDownRowsByProjectIds(
            Set<Long> projectIds,
            LocalDate startDate,
            LocalDate endDate
    ) {
        if (projectIds == null || projectIds.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery("""
                        select new com.xiyu.bid.analytics.repository.DashboardAnalyticsRepository$RevenueDrillDownRow(
                            t.id,
                            p.id,
                            t.title,
                            t.source,
                            t.status,
                            p.name,
                            p.status,
                            p.managerId,
                            u.fullName,
                            t.budget,
                            t.aiScore,
                            t.createdAt,
                            t.deadline
                        )
                        from Project p
                        left join Tender t on t.id = p.tenderId
                        left join User u on u.id = p.managerId
                        where p.id in :projectIds
                          and (:startDate is null or t.createdAt >= :startDate)
                          and (:endDate is null or t.createdAt <= :endDate)
                        order by t.createdAt desc, t.id desc
                        """, DashboardAnalyticsRepository.RevenueDrillDownRow.class)
                .setParameter("projectIds", projectIds)
                .setParameter("startDate", startDate == null ? null : startDate.atStartOfDay())
                .setParameter("endDate", endDate == null ? null : endDate.atTime(23, 59, 59))
                .getResultList();
    }

    public List<DashboardAnalyticsRepository.ProjectDrillDownRow> fetchProjectDrillDownRows(
            LocalDate startDate,
            LocalDate endDate
    ) {
        return entityManager.createQuery("""
                        select new com.xiyu.bid.analytics.repository.DashboardAnalyticsRepository$ProjectDrillDownRow(
                            p.id,
                            p.tenderId,
                            p.name,
                            t.title,
                            p.status,
                            p.managerId,
                            u.fullName,
                            t.budget,
                            coalesce(p.startDate, p.createdAt),
                            p.endDate,
                            size(p.teamMembers)
                        )
                        from Project p
                        left join Tender t on t.id = p.tenderId
                        left join User u on u.id = p.managerId
                        where (:startDate is null or coalesce(p.startDate, p.createdAt) >= :startDate)
                          and (:endDate is null or coalesce(p.startDate, p.createdAt) <= :endDate)
                        order by coalesce(p.startDate, p.createdAt) desc, p.id desc
                        """, DashboardAnalyticsRepository.ProjectDrillDownRow.class)
                .setParameter("startDate", startDate == null ? null : startDate.atStartOfDay())
                .setParameter("endDate", endDate == null ? null : endDate.atTime(23, 59, 59))
                .getResultList();
    }

    public List<DashboardAnalyticsRepository.ProjectDrillDownRow> fetchProjectDrillDownRowsByProjectIds(
            Set<Long> projectIds,
            LocalDate startDate,
            LocalDate endDate
    ) {
        if (projectIds == null || projectIds.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery("""
                        select new com.xiyu.bid.analytics.repository.DashboardAnalyticsRepository$ProjectDrillDownRow(
                            p.id,
                            p.tenderId,
                            p.name,
                            t.title,
                            p.status,
                            p.managerId,
                            u.fullName,
                            t.budget,
                            coalesce(p.startDate, p.createdAt),
                            p.endDate,
                            size(p.teamMembers)
                        )
                        from Project p
                        left join Tender t on t.id = p.tenderId
                        left join User u on u.id = p.managerId
                        where p.id in :projectIds
                          and (:startDate is null or coalesce(p.startDate, p.createdAt) >= :startDate)
                          and (:endDate is null or coalesce(p.startDate, p.createdAt) <= :endDate)
                        order by coalesce(p.startDate, p.createdAt) desc, p.id desc
                        """, DashboardAnalyticsRepository.ProjectDrillDownRow.class)
                .setParameter("projectIds", projectIds)
                .setParameter("startDate", startDate == null ? null : startDate.atStartOfDay())
                .setParameter("endDate", endDate == null ? null : endDate.atTime(23, 59, 59))
                .getResultList();
    }
}
