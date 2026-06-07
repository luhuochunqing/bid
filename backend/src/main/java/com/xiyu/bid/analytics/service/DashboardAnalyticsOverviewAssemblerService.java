package com.xiyu.bid.analytics.service;

import com.xiyu.bid.analytics.dto.DashboardOverviewDTO;
import com.xiyu.bid.analytics.dto.RegionalData;
import com.xiyu.bid.analytics.dto.SummaryStats;
import com.xiyu.bid.analytics.dto.TrendData;
import com.xiyu.bid.analytics.repository.DashboardAnalyticsRepository;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
class DashboardAnalyticsOverviewAssemblerService {

    DashboardOverviewDTO buildOverview(
            SummaryStats summaryStats,
            List<TrendData> tenderTrends,
            List<TrendData> projectTrends,
            Map<String, Long> statusDistribution,
            List<com.xiyu.bid.analytics.dto.CompetitorData> topCompetitors,
            List<RegionalData> regionalDistribution
    ) {
        return DashboardOverviewDTO.builder()
                .summaryStats(summaryStats)
                .tenderTrends(tenderTrends)
                .projectTrends(projectTrends)
                .statusDistribution(statusDistribution)
                .topCompetitors(topCompetitors)
                .regionalDistribution(regionalDistribution)
                .build();
    }

    SummaryStats buildSummaryStats(DashboardAnalyticsRepository.OverviewSnapshot snapshot, double successRate) {
        return SummaryStats.builder()
                .totalTenders(snapshot.totalTenders())
                .activeProjects(snapshot.activeProjects())
                .pendingTasks(snapshot.pendingTasks())
                .totalBudget(snapshot.totalBudget())
                .successRate(successRate)
                .build();
    }

    Map<String, Long> buildStatusDistribution(Map<Project.Status, Long> countsByStatus) {
        Map<String, Long> distribution = new LinkedHashMap<>();
        for (var status : com.xiyu.bid.entity.Tender.Status.values()) {
            distribution.put(status.name(), countsByStatus.getOrDefault(status, 0L));
        }
        return distribution;
    }

    Map<String, Long> buildStatusDistribution(Tender.Status[] statuses, Map<Tender.Status, Long> countsByStatus) {
        Map<String, Long> distribution = new LinkedHashMap<>();
        for (Tender.Status status : statuses) {
            distribution.put(status.name(), countsByStatus.getOrDefault(status, 0L));
        }
        return distribution;
    }
}
