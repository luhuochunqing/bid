package com.xiyu.bid.analytics.service;

import com.xiyu.bid.analytics.service.TrendAnalysisQueryService.TimeDimensionRow;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
class TrendAnalysisComputationService {

    /**
     * 按年月分组，计算投标数、中标数、中标率。
     */
    TrendComputationResult computeTimeTrend(List<TimeDimensionRow> rows) {
        // 按年月分组
        Map<String, MutableTrendBucket> bucketMap = new LinkedHashMap<>();
        for (TimeDimensionRow row : rows) {
            String key = buildPeriodKey(row.year(), row.month());
            MutableTrendBucket bucket = bucketMap.computeIfAbsent(key, MutableTrendBucket::new);
            bucket.bidCount++;
            if (row.status() == com.xiyu.bid.entity.Project.Status.WON) {
                bucket.winCount++;
            }
        }

        List<String> categories = new ArrayList<>();
        List<Long> bidSeries = new ArrayList<>();
        List<Long> winSeries = new ArrayList<>();
        List<Double> winRateSeries = new ArrayList<>();

        for (Map.Entry<String, MutableTrendBucket> entry : bucketMap.entrySet()) {
            categories.add(entry.getKey());
            MutableTrendBucket bucket = entry.getValue();
            bidSeries.add(bucket.bidCount);
            winSeries.add(bucket.winCount);
            double winRate = bucket.bidCount == 0 ? 0.0
                    : Math.round(bucket.winCount * 1000.0 / bucket.bidCount) / 10.0;
            winRateSeries.add(winRate);
        }

        return new TrendComputationResult(categories, bidSeries, winSeries, winRateSeries);
    }

    private String buildPeriodKey(Integer year, Integer month) {
        if (year == null) return "未知";
        if (month == null) return String.valueOf(year);
        return String.format("%d-%02d", year, month);
    }

    public record TrendComputationResult(
            List<String> categories,
            List<Long> bidSeries,
            List<Long> winSeries,
            List<Double> winRateSeries
    ) {
    }

    private static final class MutableTrendBucket {
        private final String period;
        private long bidCount;
        private long winCount;

        MutableTrendBucket(String period) {
            this.period = period;
        }
    }
}