package com.xiyu.bid.analytics.service;

import com.xiyu.bid.analytics.dto.AnalyticsDrillDownFileDTO;
import com.xiyu.bid.analytics.dto.AnalyticsDrillDownProjectDTO;
import com.xiyu.bid.analytics.dto.AnalyticsDrillDownResponse;
import com.xiyu.bid.analytics.dto.AnalyticsDrillDownResponseDTO;
import com.xiyu.bid.analytics.dto.AnalyticsDrillDownRowDTO;
import com.xiyu.bid.analytics.dto.AnalyticsDrillDownSummaryDTO;
import com.xiyu.bid.analytics.dto.AnalyticsDrillDownTeamDTO;
import com.xiyu.bid.analytics.dto.AnalyticsFilterDimensionDTO;
import com.xiyu.bid.analytics.dto.CompetitorData;
import com.xiyu.bid.analytics.dto.DashboardOverviewDTO;
import com.xiyu.bid.analytics.dto.RegionalData;
import com.xiyu.bid.analytics.dto.SummaryStats;
import com.xiyu.bid.analytics.dto.TrendData;
import com.xiyu.bid.analytics.model.ProjectSnapshotAggregate;
import com.xiyu.bid.analytics.model.TeamAggregate;
import com.xiyu.bid.analytics.model.TeamTaskAggregate;
import com.xiyu.bid.analytics.repository.DashboardAnalyticsRepository;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.User;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Component
class DashboardAnalyticsAssemblerService {

    private final DashboardAnalyticsOverviewAssemblerService overviewAssembler;
    private final DashboardAnalyticsDrillDownContentAssemblerService contentAssembler;
    private final DashboardAnalyticsDrillDownMetricAssemblerService metricAssembler;

    DashboardAnalyticsAssemblerService(
            DashboardAnalyticsOverviewAssemblerService pOverviewAssembler,
            DashboardAnalyticsDrillDownContentAssemblerService pContentAssembler,
            DashboardAnalyticsDrillDownMetricAssemblerService pMetricAssembler
    ) {
        this.overviewAssembler = pOverviewAssembler;
        this.contentAssembler = pContentAssembler;
        this.metricAssembler = pMetricAssembler;
    }

    DashboardOverviewDTO buildOverview(
            SummaryStats summaryStats,
            List<TrendData> tenderTrends,
            List<TrendData> projectTrends,
            Map<String, Long> statusDistribution,
            List<CompetitorData> topCompetitors,
            List<RegionalData> regionalDistribution
    ) {
        return overviewAssembler.buildOverview(summaryStats, tenderTrends, projectTrends, statusDistribution, topCompetitors, regionalDistribution);
    }

    SummaryStats buildSummaryStats(DashboardAnalyticsRepository.OverviewSnapshot snapshot, double successRate) {
        return overviewAssembler.buildSummaryStats(snapshot, successRate);
    }

    Map<String, Long> buildStatusDistribution(Map<Project.Status, Long> countsByStatus) {
        return overviewAssembler.buildStatusDistribution(countsByStatus);
    }

    Map<String, Long> buildStatusDistribution(com.xiyu.bid.entity.Tender.Status[] statuses, Map<com.xiyu.bid.entity.Tender.Status, Long> countsByStatus) {
        return overviewAssembler.buildStatusDistribution(statuses, countsByStatus);
    }

    List<AnalyticsDrillDownProjectDTO> buildProjectItems(
            List<ProjectSnapshotAggregate> projects,
            Map<Long, User> userById
    ) {
        return contentAssembler.buildProjectItems(projects, userById);
    }

    List<AnalyticsDrillDownTeamDTO> buildDrillDownTeamItems(
            List<ProjectSnapshotAggregate> projects,
            List<DashboardAnalyticsRepository.TaskSnapshotRow> tasks,
            Map<Long, User> userById
    ) {
        return contentAssembler.buildDrillDownTeamItems(projects, tasks, userById);
    }

    List<AnalyticsDrillDownFileDTO> buildFileItems(
            List<ProjectSnapshotAggregate> projects,
            List<DashboardAnalyticsRepository.ProjectDocumentRow> projectDocuments,
            List<DashboardAnalyticsRepository.DocumentExportRow> documentExports
    ) {
        return contentAssembler.buildFileItems(projects, projectDocuments, documentExports);
    }

    AnalyticsDrillDownResponse assembleBasicDrillDownResponse(
            List<AnalyticsDrillDownProjectDTO> projects,
            List<AnalyticsDrillDownTeamDTO> teams,
            List<AnalyticsDrillDownFileDTO> files,
            long totalParticipation,
            long wonCount,
            double teamWinRate,
            BigDecimal totalAmount
    ) {
        return metricAssembler.assembleBasicDrillDownResponse(projects, teams, files, totalParticipation, wonCount, teamWinRate, totalAmount);
    }

    AnalyticsDrillDownResponseDTO buildMetricDrillDownResponse(
            String metricKey,
            String metricLabel,
            LocalDate startDate,
            LocalDate endDate,
            List<AnalyticsFilterDimensionDTO> dimensions,
            List<AnalyticsDrillDownRowDTO> filteredRows,
            Integer requestedPage,
            Integer requestedSize,
            AnalyticsDrillDownSummaryDTO summary
    ) {
        return metricAssembler.buildMetricDrillDownResponse(
                metricKey,
                metricLabel,
                startDate,
                endDate,
                dimensions,
                filteredRows,
                requestedPage,
                requestedSize,
                summary
        );
    }

    AnalyticsFilterDimensionDTO buildDimension(
            String key,
            String label,
            String selectedValue,
            List<AnalyticsDrillDownRowDTO> rows,
            Function<AnalyticsDrillDownRowDTO, String> extractor,
            Function<String, String> labelTranslator
    ) {
        return metricAssembler.buildDimension(key, label, selectedValue, rows, extractor, labelTranslator);
    }

    AnalyticsFilterDimensionDTO buildProjectStatusDimension(String selectedValue, List<AnalyticsDrillDownRowDTO> rows) {
        return metricAssembler.buildProjectStatusDimension(selectedValue, rows);
    }

    String translateTenderStatus(String status) {
        return metricAssembler.translateTenderStatus(status);
    }

    String translateProjectStatus(String status) {
        return metricAssembler.translateProjectStatus(status);
    }

    String translateOutcome(String outcome) {
        return metricAssembler.translateOutcome(outcome);
    }

    String translateUserRole(String role) {
        return metricAssembler.translateUserRole(role);
    }

    int normalizePage(Integer page) {
        return metricAssembler.normalizePage(page);
    }

    int normalizeSize(Integer size) {
        return metricAssembler.normalizeSize(size);
    }

    BigDecimal sumAmounts(List<AnalyticsDrillDownRowDTO> rows) {
        return metricAssembler.sumAmounts(rows);
    }

    AnalyticsDrillDownSummaryDTO buildTeamSummary(
            List<AnalyticsDrillDownRowDTO> filteredRows,
            List<ProjectSnapshotAggregate> filteredProjects,
            Map<Long, DashboardAnalyticsRepository.TenderSummaryRow> tenderById
    ) {
        return contentAssembler.buildTeamSummary(filteredRows, filteredProjects, tenderById);
    }

    List<AnalyticsDrillDownRowDTO> toRevenueDrillDownRows(List<DashboardAnalyticsRepository.RevenueDrillDownRow> rows) {
        return contentAssembler.toRevenueDrillDownRows(rows);
    }

    List<AnalyticsDrillDownRowDTO> toProjectDrillDownRows(List<DashboardAnalyticsRepository.ProjectDrillDownRow> rows) {
        return contentAssembler.toProjectDrillDownRows(rows);
    }

    List<AnalyticsDrillDownRowDTO> toTeamDrillDownRows(
            Map<Long, TeamAggregate> aggregates,
            Map<Long, User> userById,
            Map<Long, TeamTaskAggregate> taskByAssignee
    ) {
        return contentAssembler.toTeamDrillDownRows(aggregates, userById, taskByAssignee);
    }

    boolean matchesFilter(String value, String selectedValue) {
        return metricAssembler.matchesFilter(value, selectedValue);
    }

    boolean matchesProjectStatusFilter(String status, String filter) {
        return metricAssembler.matchesProjectStatusFilter(status, filter);
    }

    String resolveDisplayName(User user, String fallbackName, Long userId) {
        return contentAssembler.resolveDisplayName(user, fallbackName, userId);
    }

    String resolveProjectResult(Project.Status status) {
        return contentAssembler.resolveProjectResult(status);
    }
}
