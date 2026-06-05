package com.xiyu.bid.analytics.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardAnalyticsReadRepository {

    private final DashboardSummaryAnalyticsRepository summaryRepository;
    private final DashboardOutcomeAnalyticsRepository outcomeRepository;
    private final DashboardDrillDownAnalyticsRepository drillDownRepository;
    private final DashboardProjectSnapshotRepository projectSnapshotRepository;
    private final ProjectSnapshotWithTeamRepository projectSnapshotWithTeamRepository;
    private final SnapshotSupportingQueryRepository supportingQueryRepository;

    public DashboardAnalyticsRepository.OverviewSnapshot fetchOverviewSnapshot(Set<Long> projectIds) {
        return projectIds == null
                ? summaryRepository.fetchOverviewSnapshot()
                : summaryRepository.fetchOverviewSnapshotByProjectIds(projectIds);
    }

    public List<DashboardAnalyticsRepository.MonthlyTrendRow> fetchTenderTrendRows(Set<Long> projectIds) {
        return projectIds == null
                ? summaryRepository.fetchTenderTrends()
                : summaryRepository.fetchTenderTrendsByProjectIds(projectIds);
    }

    public List<DashboardAnalyticsRepository.MonthlyTrendRow> fetchProjectTrendRows(Set<Long> projectIds) {
        return projectIds == null
                ? summaryRepository.fetchProjectTrends()
                : summaryRepository.fetchProjectTrendsByProjectIds(projectIds);
    }

    public List<DashboardAnalyticsRepository.StatusCountRow> fetchStatusCounts(Set<Long> projectIds) {
        return projectIds == null
                ? summaryRepository.fetchStatusDistribution()
                : summaryRepository.fetchStatusDistributionByProjectIds(projectIds);
    }

    public List<DashboardAnalyticsRepository.SourceAggregateRow> fetchSourceAggregateRows(
            Set<Long> projectIds,
            int limit
    ) {
        return projectIds == null
                ? summaryRepository.fetchSourceAggregates(limit)
                : summaryRepository.fetchSourceAggregatesByProjectIds(projectIds, limit);
    }

    public List<DashboardAnalyticsRepository.ProductLineCandidateRow> fetchProductLineCandidateRows(
            Set<Long> projectIds
    ) {
        return projectIds == null
                ? outcomeRepository.fetchProductLineCandidateRows()
                : outcomeRepository.fetchProductLineCandidateRowsByProjectIds(projectIds);
    }

    public List<DashboardAnalyticsRepository.TenderSummaryRow> fetchTenderSummaryRows(Set<Long> projectIds) {
        return projectIds == null
                ? outcomeRepository.fetchTenderSummaryRows()
                : outcomeRepository.fetchTenderSummaryRowsByProjectIds(projectIds);
    }

    public List<DashboardAnalyticsRepository.ProjectSnapshotRow> fetchProjectSnapshotRowsByTenderIds(
            Set<Long> projectIds,
            Collection<Long> tenderIds
    ) {
        return projectIds == null
                ? projectSnapshotRepository.fetchProjectSnapshotRowsByTenderIds(tenderIds)
                : projectSnapshotRepository.fetchProjectSnapshotRowsByTenderIdsAndProjectIds(tenderIds, projectIds);
    }

    public List<DashboardAnalyticsRepository.ProjectSnapshotRow> fetchProjectSnapshotRowsByDateRange(
            Set<Long> projectIds,
            LocalDate startDate,
            LocalDate endDate
    ) {
        return projectIds == null
                ? projectSnapshotRepository.fetchProjectSnapshotRowsByDateRange(startDate, endDate)
                : projectSnapshotRepository.fetchProjectSnapshotRowsByProjectIdsAndDateRange(
                        projectIds,
                        startDate,
                        endDate
                );
    }

    public List<DashboardAnalyticsRepository.TaskSnapshotRow> fetchTaskSnapshotRows(Set<Long> projectIds) {
        return supportingQueryRepository.fetchTaskSnapshotRows(projectIds);
    }

    public List<DashboardAnalyticsRepository.ProjectDocumentRow> fetchProjectDocumentRows(Set<Long> projectIds) {
        return supportingQueryRepository.fetchProjectDocumentRows(projectIds);
    }

    public List<DashboardAnalyticsRepository.DocumentExportRow> fetchDocumentExportRows(Set<Long> projectIds) {
        return supportingQueryRepository.fetchDocumentExportRows(projectIds);
    }

    public List<DashboardAnalyticsRepository.RevenueDrillDownRow> fetchRevenueDrillDownRows(
            Set<Long> projectIds,
            LocalDate startDate,
            LocalDate endDate
    ) {
        return projectIds == null
                ? drillDownRepository.fetchRevenueDrillDownRows(startDate, endDate)
                : drillDownRepository.fetchRevenueDrillDownRowsByProjectIds(projectIds, startDate, endDate);
    }

    public List<DashboardAnalyticsRepository.ProjectDrillDownRow> fetchProjectDrillDownRows(
            Set<Long> projectIds,
            LocalDate startDate,
            LocalDate endDate
    ) {
        return projectIds == null
                ? drillDownRepository.fetchProjectDrillDownRows(startDate, endDate)
                : drillDownRepository.fetchProjectDrillDownRowsByProjectIds(projectIds, startDate, endDate);
    }

    /**
     * Optimized method that fetches project snapshots with full team member User data.
     * Eliminates the need for a separate fetchUsersByIds call.
     */
    public List<DashboardAnalyticsRepository.ProjectSnapshotRowWithUsers> fetchProjectSnapshotRowsWithUsersByTenderIds(
            Set<Long> projectIds,
            Collection<Long> tenderIds
    ) {
        return projectIds == null
                ? projectSnapshotWithTeamRepository.fetchProjectSnapshotRowsWithUsersByTenderIds(tenderIds)
                : projectSnapshotWithTeamRepository.fetchProjectSnapshotRowsWithUsersByTenderIdsAndProjectIds(tenderIds, projectIds);
    }

    /**
     * Optimized method that fetches project snapshots with full team member User data.
     * Eliminates the need for a separate fetchUsersByIds call.
     */
    public List<DashboardAnalyticsRepository.ProjectSnapshotRowWithUsers> fetchProjectSnapshotRowsWithUsersByDateRange(
            Set<Long> projectIds,
            LocalDate startDate,
            LocalDate endDate
    ) {
        return projectIds == null
                ? projectSnapshotWithTeamRepository.fetchProjectSnapshotRowsWithUsersByDateRange(startDate, endDate)
                : projectSnapshotWithTeamRepository.fetchProjectSnapshotRowsWithUsersByProjectIdsAndDateRange(
                        projectIds,
                        startDate,
                        endDate
                );
    }
}
