package com.xiyu.bid.analytics.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Repository
@Transactional(readOnly = true)
public class DashboardOutcomeAnalyticsRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public List<DashboardAnalyticsRepository.ProductLineCandidateRow> fetchProductLineCandidateRows() {
        return entityManager.createQuery("""
                        select new com.xiyu.bid.analytics.repository.DashboardAnalyticsRepository$ProductLineCandidateRow(
                            t.title,
                            t.status,
                            t.budget
                        )
                        from Tender t
                        """, DashboardAnalyticsRepository.ProductLineCandidateRow.class)
                .getResultList();
    }

    public List<DashboardAnalyticsRepository.ProductLineCandidateRow> fetchProductLineCandidateRowsByProjectIds(
            Set<Long> projectIds
    ) {
        if (projectIds == null || projectIds.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery("""
                        select new com.xiyu.bid.analytics.repository.DashboardAnalyticsRepository$ProductLineCandidateRow(
                            t.title,
                            t.status,
                            t.budget
                        )
                        from Project p
                        join Tender t on t.id = p.tenderId
                        where p.id in :projectIds
                        """, DashboardAnalyticsRepository.ProductLineCandidateRow.class)
                .setParameter("projectIds", projectIds)
                .getResultList();
    }

    public List<DashboardAnalyticsRepository.TenderSummaryRow> fetchTenderSummaryRows() {
        return entityManager.createQuery("""
                        select new com.xiyu.bid.analytics.repository.DashboardAnalyticsRepository$TenderSummaryRow(
                            t.id,
                            t.title,
                            t.source,
                            t.status,
                            t.budget,
                            t.createdAt,
                            t.deadline
                        )
                        from Tender t
                        order by t.createdAt desc, t.id desc
                        """, DashboardAnalyticsRepository.TenderSummaryRow.class)
                .getResultList();
    }

    public List<DashboardAnalyticsRepository.TenderSummaryRow> fetchTenderSummaryRowsByProjectIds(
            Set<Long> projectIds
    ) {
        if (projectIds == null || projectIds.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery("""
                        select new com.xiyu.bid.analytics.repository.DashboardAnalyticsRepository$TenderSummaryRow(
                            t.id,
                            t.title,
                            t.source,
                            t.status,
                            t.budget,
                            t.createdAt,
                            t.deadline
                        )
                        from Project p
                        join Tender t on t.id = p.tenderId
                        where p.id in :projectIds
                        order by t.createdAt desc, t.id desc
                        """, DashboardAnalyticsRepository.TenderSummaryRow.class)
                .setParameter("projectIds", projectIds)
                .getResultList();
    }
}
