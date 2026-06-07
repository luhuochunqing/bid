// Input: DashboardAnalyticsQueryService, dashboard computation/assembler services
// Output: Dashboard analytics API data
// Pos: Service facade/业务门面层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.analytics.service;

import com.xiyu.bid.analytics.dto.AnalyticsDrillDownResponse;
import com.xiyu.bid.analytics.dto.AnalyticsDrillDownResponseDTO;
import com.xiyu.bid.analytics.dto.CompetitorData;
import com.xiyu.bid.analytics.dto.DashboardOverviewDTO;
import com.xiyu.bid.analytics.dto.ProductLineData;
import com.xiyu.bid.analytics.dto.RegionalData;
import com.xiyu.bid.analytics.dto.SummaryStats;
import com.xiyu.bid.analytics.dto.TrendData;
import com.xiyu.bid.analytics.model.ProjectSnapshotAggregate;
import com.xiyu.bid.analytics.repository.DashboardAnalyticsRepository;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Stable facade for dashboard analytics endpoints.
 * Delegates query, computation, and DTO assembly to smaller package-local services.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DashboardAnalyticsService {

    private final DashboardAnalyticsQueryService queryService;
    private final DashboardAnalyticsComputationService computationService;
    private final DashboardAnalyticsAssemblerService assemblerService;
    private final DashboardAnalyticsMetricDrillDownService metricDrillDownService;
    private final DashboardDemoFusionService demoFusionService;

    @Cacheable(value = "dashboard:overview", key = "'overview'")
    public DashboardOverviewDTO getOverview() {
        log.debug("Fetching dashboard overview from database");
        return assemblerService.buildOverview(
                getSummaryStats(),
                getTenderTrends(),
                getProjectTrends(),
                getStatusDistribution(),
                getTopCompetitors(5),
                getRegionalDistribution()
        );
    }

    public SummaryStats getSummaryStats() {
        DashboardAnalyticsRepository.OverviewSnapshot snapshot = queryService.fetchOverviewSnapshot();
        SummaryStats real = assemblerService.buildSummaryStats(
                snapshot,
                computationService.calculateSuccessRate(snapshot)
        );
        return demoFusionService.mergeSummary(real);
    }

    public List<TrendData> getTenderTrends() {
        List<TrendData> real = computationService.buildTenderTrends(queryService.fetchTenderTrendRows());
        return demoFusionService.mergeTenderTrends(real);
    }

    public List<TrendData> getProjectTrends() {
        List<TrendData> real = computationService.buildProjectTrends(queryService.fetchProjectTrendRows());
        return demoFusionService.mergeProjectTrends(real);
    }

    public Map<String, Long> getStatusDistribution() {
        Map<String, Long> distribution = assemblerService.buildStatusDistribution(
                Tender.Status.values(),
                computationService.buildStatusDistributionCounts(queryService.fetchStatusCounts())
        );
        return demoFusionService.mergeStatusDistribution(distribution);
    }

    public List<CompetitorData> getTopCompetitors(Integer limit) {
        int requestedLimit = limit == null || limit < 1 ? 5 : limit;
        List<CompetitorData> real = computationService.buildTopCompetitors(queryService.fetchSourceAggregateRows(requestedLimit));
        return demoFusionService.mergeCompetitors(real);
    }

    public List<RegionalData> getRegionalDistribution() {
        List<RegionalData> real = computationService.buildRegionalDistribution(queryService.fetchSourceAggregateRows(Integer.MAX_VALUE));
        return demoFusionService.mergeRegions(real);
    }

    public List<ProductLineData> getProductLinePerformance() {
        List<ProductLineData> real = computationService.buildProductLinePerformance(queryService.fetchProductLineCandidateRows());
        return demoFusionService.mergeProductLines(real);
    }

    @Transactional(readOnly = true)
    public AnalyticsDrillDownResponse getDrillDown(String type, String key) {
        List<DashboardAnalyticsRepository.TenderSummaryRow> matchedTenders =
                computationService.filterTenderSummaryRowsByTypeAndKey(queryService.fetchTenderSummaryRows(), type, key);
        Set<Long> tenderIds = matchedTenders.stream()
                .map(DashboardAnalyticsRepository.TenderSummaryRow::tenderId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // Optimized: fetch project snapshots AND team member users in a single query
        // (eliminates N+1: previously collectProjectUserIds + fetchUsersByIds)
        DashboardAnalyticsQueryService.ProjectSnapshotWithUsers snapshotWithUsers =
                queryService.fetchProjectSnapshotsWithUsersByTenderIds(tenderIds);
        List<ProjectSnapshotAggregate> matchedProjects = snapshotWithUsers.projects();
        Map<Long, User> userById = snapshotWithUsers.userById();

        Set<Long> projectIds = collectProjectIds(matchedProjects);

        var tasks = queryService.fetchTaskSnapshots(projectIds);
        BigDecimal totalAmount = matchedTenders.stream()
                .map(DashboardAnalyticsRepository.TenderSummaryRow::budget)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long wonCount = matchedTenders.stream().filter(tender -> tender.status() == Tender.Status.WON).count();
        long totalParticipation = matchedTenders.size();

        return assemblerService.assembleBasicDrillDownResponse(
                assemblerService.buildProjectItems(matchedProjects, userById),
                assemblerService.buildDrillDownTeamItems(matchedProjects, tasks, userById),
                assemblerService.buildFileItems(
                        matchedProjects,
                        queryService.fetchProjectDocuments(projectIds),
                        queryService.fetchDocumentExports(projectIds)
                ),
                totalParticipation,
                wonCount,
                totalParticipation > 0 ? (wonCount * 100.0) / totalParticipation : 0.0,
                totalAmount
        );
    }

    @Transactional(readOnly = true)
    public AnalyticsDrillDownResponseDTO getRevenueDrillDown(
            String status,
            LocalDate startDate,
            LocalDate endDate,
            Integer page,
            Integer size
    ) {
        return metricDrillDownService.getRevenueDrillDown(status, startDate, endDate, page, size);
    }

    @Transactional(readOnly = true)
    public AnalyticsDrillDownResponseDTO getWinRateDrillDown(
            String outcome,
            LocalDate startDate,
            LocalDate endDate,
            Integer page,
            Integer size
    ) {
        return metricDrillDownService.getWinRateDrillDown(outcome, startDate, endDate, page, size);
    }

    @Transactional(readOnly = true)
    public AnalyticsDrillDownResponseDTO getTeamDrillDown(
            String role,
            LocalDate startDate,
            LocalDate endDate,
            Integer page,
            Integer size
    ) {
        return metricDrillDownService.getTeamDrillDown(role, startDate, endDate, page, size);
    }

    @Transactional(readOnly = true)
    public AnalyticsDrillDownResponseDTO getProjectDrillDown(
            String status,
            LocalDate startDate,
            LocalDate endDate,
            Integer page,
            Integer size
    ) {
        return metricDrillDownService.getProjectDrillDown(status, startDate, endDate, page, size);
    }

    @CacheEvict(value = "dashboard:overview", key = "'overview'")
    public void clearOverviewCache() {
        log.debug("Clearing dashboard overview cache");
    }

    private Set<Long> collectProjectIds(List<ProjectSnapshotAggregate> projects) {
        return projects.stream()
                .map(ProjectSnapshotAggregate::projectId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
