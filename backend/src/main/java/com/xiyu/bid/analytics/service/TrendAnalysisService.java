package com.xiyu.bid.analytics.service;

import com.xiyu.bid.analytics.dto.EnhancedOverviewResponse;
import com.xiyu.bid.analytics.dto.TrendAnalysisResponse;
import com.xiyu.bid.analytics.service.TrendAnalysisComputationService.TrendComputationResult;
import com.xiyu.bid.analytics.service.TrendAnalysisQueryService.OverviewRow;
import com.xiyu.bid.analytics.service.TrendAnalysisQueryService.TimeDimensionRow;
import com.xiyu.bid.entity.Project;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TrendAnalysisService {

    private final TrendAnalysisQueryService queryService;
    private final TrendAnalysisComputationService computationService;

    public TrendAnalysisResponse getEnhancedTrends(
            LocalDate startDate,
            LocalDate endDate,
            String xAxis,
            List<Long> departmentIds,
            List<Long> userIds,
            List<Long> regionIds,
            List<String> customerTypes,
            List<String> projectTypes,
            List<String> statuses,
            List<String> tenderEntities,
            List<String> competitorNames
    ) {
        // 将字符串状态转换为枚举
        List<Project.Status> statusEnums = statuses != null
                ? statuses.stream()
                        .map(s -> {
                            try {
                                return Project.Status.valueOf(s);
                            } catch (IllegalArgumentException e) {
                                return null;
                            }
                        })
                        .filter(java.util.Objects::nonNull)
                        .toList()
                : null;

        // 默认按时间维度查询
        List<TimeDimensionRow> rows = queryService.fetchTimeTrendRows(
                startDate, endDate,
                departmentIds, userIds, regionIds,
                customerTypes, projectTypes, statusEnums
        );

        TrendComputationResult result = computationService.computeTimeTrend(rows);

        return TrendAnalysisResponse.builder()
                .categories(result.categories())
                .bidSeries(result.bidSeries())
                .winSeries(result.winSeries())
                .winRateSeries(result.winRateSeries())
                .build();
    }

    public EnhancedOverviewResponse getEnhancedOverview(LocalDate startDate, LocalDate endDate) {
        OverviewRow row = queryService.fetchOverviewRow(startDate, endDate);

        long totalCount = row.totalCount() != null ? row.totalCount() : 0L;
        long biddingCount = row.biddingCount() != null ? row.biddingCount() : 0L;
        long wonCount = row.wonCount() != null ? row.wonCount() : 0L;
        double winRate = totalCount == 0 ? 0.0
                : Math.round(wonCount * 1000.0 / totalCount) / 10.0;

        return EnhancedOverviewResponse.builder()
                .totalCount(totalCount)
                .biddingCount(biddingCount)
                .wonCount(wonCount)
                .winRate(winRate)
                .build();
    }
}