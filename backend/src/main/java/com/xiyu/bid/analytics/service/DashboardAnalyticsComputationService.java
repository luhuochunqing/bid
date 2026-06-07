package com.xiyu.bid.analytics.service;

import com.xiyu.bid.analytics.dto.CompetitorData;
import com.xiyu.bid.analytics.dto.ProductLineData;
import com.xiyu.bid.analytics.dto.RegionalData;
import com.xiyu.bid.analytics.dto.TrendData;
import com.xiyu.bid.analytics.model.ProjectSnapshotAggregate;
import com.xiyu.bid.analytics.model.TeamAggregate;
import com.xiyu.bid.analytics.model.TeamTaskAggregate;
import com.xiyu.bid.analytics.repository.DashboardAnalyticsRepository;
import com.xiyu.bid.entity.Tender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
class DashboardAnalyticsComputationService {

    private final DashboardAnalyticsTrendAndDistributionService trendAndDistributionService;
    private final DashboardAnalyticsOutcomeComputationService outcomeComputationService;
    private final DashboardAnalyticsTeamComputationService teamComputationService;

    List<TrendData> buildTenderTrends(List<DashboardAnalyticsRepository.MonthlyTrendRow> rows) {
        return trendAndDistributionService.buildTenderTrends(rows);
    }

    List<TrendData> buildProjectTrends(List<DashboardAnalyticsRepository.MonthlyTrendRow> rows) {
        return trendAndDistributionService.buildProjectTrends(rows);
    }

    double calculateSuccessRate(DashboardAnalyticsRepository.OverviewSnapshot snapshot) {
        return trendAndDistributionService.calculateSuccessRate(snapshot);
    }

    List<RegionalData> buildRegionalDistribution(List<DashboardAnalyticsRepository.SourceAggregateRow> rows) {
        return trendAndDistributionService.buildRegionalDistribution(rows);
    }

    List<CompetitorData> buildTopCompetitors(List<DashboardAnalyticsRepository.SourceAggregateRow> rows) {
        return trendAndDistributionService.buildTopCompetitors(rows);
    }

    Map<Tender.Status, Long> buildStatusDistributionCounts(List<DashboardAnalyticsRepository.StatusCountRow> rows) {
        return trendAndDistributionService.buildStatusDistributionCounts(rows);
    }

    List<ProductLineData> buildProductLinePerformance(List<DashboardAnalyticsRepository.ProductLineCandidateRow> rows) {
        return trendAndDistributionService.buildProductLinePerformance(rows);
    }

    Map<String, Double> filterMatchCounts(List<String> values, String selected, Map<String, String> translator) {
        return trendAndDistributionService.filterMatchCounts(values, selected, translator);
    }

    List<DashboardAnalyticsRepository.TenderSummaryRow> filterTenderSummaryRowsByTypeAndKey(
            List<DashboardAnalyticsRepository.TenderSummaryRow> rows,
            String type,
            String key
    ) {
        return outcomeComputationService.filterTenderSummaryRowsByTypeAndKey(rows, type, key);
    }

    String deriveOutcome(Tender.Status status, ProjectSnapshotAggregate project) {
        return outcomeComputationService.deriveOutcome(status, project);
    }

    List<DashboardAnalyticsRepository.TenderSummaryRow> filterTenderRowsByDateRange(
            List<DashboardAnalyticsRepository.TenderSummaryRow> rows,
            LocalDate startDate,
            LocalDate endDate
    ) {
        return outcomeComputationService.filterTenderRowsByDateRange(rows, startDate, endDate);
    }

    Map<Long, TeamTaskAggregate> summarizeTaskRows(
            List<DashboardAnalyticsRepository.TaskSnapshotRow> rows,
            java.time.LocalDateTime now
    ) {
        return teamComputationService.summarizeTaskRows(rows, now);
    }

    Map<Long, TeamAggregate> buildTeamProjectAggregates(
            List<ProjectSnapshotAggregate> projects,
            Map<Long, DashboardAnalyticsRepository.TenderSummaryRow> tenderById
    ) {
        return teamComputationService.buildTeamProjectAggregates(projects, tenderById);
    }

    int calculatePerformanceScore(TeamAggregate aggregate) {
        return teamComputationService.calculatePerformanceScore(aggregate);
    }
}
