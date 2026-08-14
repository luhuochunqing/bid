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
        // PRD §3.1 本期数据
        OverviewRow row = queryService.fetchOverviewRow(startDate, endDate);

        long totalCount = row.totalCount() != null ? row.totalCount() : 0L;
        long biddingCount = row.biddingCount() != null ? row.biddingCount() : 0L;
        long wonCount = row.wonCount() != null ? row.wonCount() : 0L;
        double winRate = totalCount == 0 ? 0.0
                : Math.round(wonCount * 1000.0 / totalCount) / 10.0;

        // PRD §3.1 去年同期数据（去年同期 = 上一年度同一日期范围）
        LocalDate yoyStart = startDate != null ? startDate.minusYears(1) : null;
        LocalDate yoyEnd = endDate != null ? endDate.minusYears(1) : null;
        OverviewRow yoyRow = queryService.fetchOverviewRow(yoyStart, yoyEnd);

        long yoyTotalCount = yoyRow.totalCount() != null ? yoyRow.totalCount() : 0L;
        long yoyBiddingCount = yoyRow.biddingCount() != null ? yoyRow.biddingCount() : 0L;
        long yoyWonCount = yoyRow.wonCount() != null ? yoyRow.wonCount() : 0L;
        double yoyWinRate = yoyTotalCount == 0 ? 0.0
                : Math.round(yoyWonCount * 1000.0 / yoyTotalCount) / 10.0;

        // PRD §3.1 同比计算（去年同期无数据时返回 null，前端显示「—」）
        Double totalCountYoy = yoyTotalCount > 0
                ? round1((totalCount - yoyTotalCount) * 100.0 / yoyTotalCount)
                : null;
        Double biddingCountYoy = yoyBiddingCount > 0
                ? round1((biddingCount - yoyBiddingCount) * 100.0 / yoyBiddingCount)
                : null;
        Double wonCountYoy = yoyWonCount > 0
                ? round1((wonCount - yoyWonCount) * 100.0 / yoyWonCount)
                : null;
        // 中标率同比 = 百分点差值（非百分比变化）
        Double winRateYoy = yoyTotalCount > 0
                ? round1(winRate - yoyWinRate)
                : null;

        // PRD §3.1 今日新增（投标总数卡片底部显示）
        long todayNewCount = queryService.fetchTodayNewCount();

        return EnhancedOverviewResponse.builder()
                .totalCount(totalCount)
                .biddingCount(biddingCount)
                .wonCount(wonCount)
                .winRate(winRate)
                .totalCountYoy(totalCountYoy)
                .biddingCountYoy(biddingCountYoy)
                .wonCountYoy(wonCountYoy)
                .winRateYoy(winRateYoy)
                .todayNewCount(todayNewCount)
                .build();
    }

    /** 保留 1 位小数 */
    private static double round1(double val) {
        return Math.round(val * 10.0) / 10.0;
    }
}