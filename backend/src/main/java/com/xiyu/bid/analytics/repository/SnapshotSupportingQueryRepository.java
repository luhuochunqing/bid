package com.xiyu.bid.analytics.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Repository for supporting snapshot queries (Task, Document, Export).
 * These queries provide supplementary data for dashboard analytics.
 */
@Repository
@Transactional(readOnly = true)
public class SnapshotSupportingQueryRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public List<DashboardAnalyticsRepository.TaskSnapshotRow> fetchTaskSnapshotRows(Set<Long> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery("""
                        select new com.xiyu.bid.analytics.repository.DashboardAnalyticsRepository$TaskSnapshotRow(
                            t.projectId,
                            t.assigneeId,
                            t.status,
                            t.dueDate
                        )
                        from Task t
                        where t.projectId in :projectIds
                        order by t.createdAt desc, t.id desc
                        """, DashboardAnalyticsRepository.TaskSnapshotRow.class)
                .setParameter("projectIds", projectIds)
                .getResultList();
    }

    public List<DashboardAnalyticsRepository.ProjectDocumentRow> fetchProjectDocumentRows(Set<Long> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery("""
                        select new com.xiyu.bid.analytics.repository.DashboardAnalyticsRepository$ProjectDocumentRow(
                            d.projectId,
                            d.id,
                            d.name,
                            d.uploaderName,
                            d.createdAt,
                            d.size
                        )
                        from ProjectDocument d
                        where d.projectId in :projectIds
                        order by d.createdAt desc, d.id desc
                        """, DashboardAnalyticsRepository.ProjectDocumentRow.class)
                .setParameter("projectIds", projectIds)
                .getResultList();
    }

    public List<DashboardAnalyticsRepository.DocumentExportRow> fetchDocumentExportRows(Set<Long> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery("""
                        select new com.xiyu.bid.analytics.repository.DashboardAnalyticsRepository$DocumentExportRow(
                            e.projectId,
                            e.id,
                            e.fileName,
                            e.exportedByName,
                            e.exportedAt,
                            e.fileSize,
                            e.format
                        )
                        from DocumentExport e
                        where e.projectId in :projectIds
                        order by e.exportedAt desc, e.id desc
                        """, DashboardAnalyticsRepository.DocumentExportRow.class)
                .setParameter("projectIds", projectIds)
                .getResultList();
    }
}
