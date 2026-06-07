package com.xiyu.bid.analytics.service;

import com.xiyu.bid.analytics.dto.CompetitorData;
import com.xiyu.bid.analytics.dto.ProductLineData;
import com.xiyu.bid.analytics.dto.RegionalData;
import com.xiyu.bid.analytics.dto.SummaryStats;
import com.xiyu.bid.analytics.dto.TrendData;
import com.xiyu.bid.demo.service.DemoDataProvider;
import com.xiyu.bid.demo.service.DemoFusionService;
import com.xiyu.bid.demo.service.DemoModeService;
import com.xiyu.bid.entity.Tender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
class DashboardDemoFusionService {

    private final DemoModeService demoModeService;
    private final DemoDataProvider demoDataProvider;
    private final DemoFusionService demoFusionService;

    SummaryStats mergeSummary(SummaryStats real) {
        if (!demoModeService.isEnabled()) {
            return real;
        }
        SummaryStats demo = demoDataProvider.getDemoSummaryStats();
        return SummaryStats.builder()
                .totalTenders(valueOrZero(real.getTotalTenders()) + valueOrZero(demo.getTotalTenders()))
                .activeProjects(valueOrZero(real.getActiveProjects()) + valueOrZero(demo.getActiveProjects()))
                .pendingTasks(valueOrZero(real.getPendingTasks()) + valueOrZero(demo.getPendingTasks()))
                .totalBudget(valueOrZero(real.getTotalBudget()).add(valueOrZero(demo.getTotalBudget())))
                .successRate(real.getSuccessRate() == null || real.getSuccessRate() <= 0
                        ? demo.getSuccessRate()
                        : real.getSuccessRate())
                .build();
    }

    List<TrendData> mergeTenderTrends(List<TrendData> real) {
        if (!demoModeService.isEnabled()) {
            return real;
        }
        return demoFusionService.mergeByKey(real, demoDataProvider.getDemoTenderTrends(), TrendData::getPeriod);
    }

    List<TrendData> mergeProjectTrends(List<TrendData> real) {
        if (!demoModeService.isEnabled()) {
            return real;
        }
        return demoFusionService.mergeByKey(real, demoDataProvider.getDemoProjectTrends(), TrendData::getPeriod);
    }

    Map<String, Long> mergeStatusDistribution(Map<String, Long> real) {
        if (!demoModeService.isEnabled()) {
            return real;
        }
        Map<String, Long> merged = new java.util.LinkedHashMap<>(real);
        demoDataProvider.getDemoTenders().forEach(tender -> {
            String key = tender.getStatus() == null
                    ? Tender.Status.TRACKING.name()
                    : tender.getStatus().name();
            merged.put(key, merged.getOrDefault(key, 0L) + 1L);
        });
        return merged;
    }

    List<CompetitorData> mergeCompetitors(List<CompetitorData> real) {
        if (!demoModeService.isEnabled()) {
            return real;
        }
        return demoFusionService.mergeByKey(real, demoDataProvider.getDemoCompetitors(), CompetitorData::getName);
    }

    List<RegionalData> mergeRegions(List<RegionalData> real) {
        if (!demoModeService.isEnabled()) {
            return real;
        }
        return demoFusionService.mergeByKey(real, demoDataProvider.getDemoRegions(), RegionalData::getRegion);
    }

    List<ProductLineData> mergeProductLines(List<ProductLineData> real) {
        if (!demoModeService.isEnabled()) {
            return real;
        }
        return demoFusionService.mergeByKey(real, demoDataProvider.getDemoProductLines(), ProductLineData::getName);
    }

    private Long valueOrZero(Long value) {
        return value == null ? Long.valueOf(0L) : value;
    }

    private BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
