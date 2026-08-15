package com.xiyu.bid.analytics.service;

import com.xiyu.bid.analytics.service.DimensionRow;
import com.xiyu.bid.analytics.service.TrendAnalysisQueryService.TimeDimensionRow;
import com.xiyu.bid.entity.Project;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
class TrendAnalysisComputationService {

    /**
     * 按维度标签分组，计算投标数、中标数、中标率。
     * 投标数 = 已中标(WON) + 未中标(LOST) 的项目数。
     * 中标数 = 已中标(WON) 的项目数。
     * 按 bidCount 降序排列（PRD 6.5）。
     */
    TrendComputationResult computeDimensionTrend(List<DimensionRow> rows) {
        Map<String, MutableTrendBucket> bucketMap = new LinkedHashMap<>();
        for (DimensionRow row : rows) {
            String key = row.category() != null ? row.category() : "未知";
            MutableTrendBucket bucket = bucketMap.computeIfAbsent(key, MutableTrendBucket::new);
            // 投标数口径：只统计已中标 + 未中标的项目
            if (row.status() == Project.Status.WON
                    || row.status() == Project.Status.LOST) {
                bucket.bidCount++;
            }
            if (row.status() == Project.Status.WON) {
                bucket.winCount++;
            }
        }

        // 按 bidCount 降序排列
        List<Map.Entry<String, MutableTrendBucket>> sorted = new ArrayList<>(bucketMap.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue().bidCount, a.getValue().bidCount));

        List<String> categories = new ArrayList<>();
        List<Long> bidSeries = new ArrayList<>();
        List<Long> winSeries = new ArrayList<>();
        List<Double> winRateSeries = new ArrayList<>();

        for (Map.Entry<String, MutableTrendBucket> entry : sorted) {
            categories.add(entry.getKey());
            long bid = entry.getValue().bidCount;
            long win = entry.getValue().winCount;
            bidSeries.add(bid);
            winSeries.add(win);
            double winRate = bid == 0 ? 0.0
                    : Math.round(win * 1000.0 / bid) / 10.0;
            winRateSeries.add(winRate);
        }

        return new TrendComputationResult(categories, bidSeries, winSeries, winRateSeries);
    }

    /**
     * 按年月分组，计算投标数、中标数、中标率。
     * 投标数 = 已中标(WON) + 未中标(LOST) 的项目数。
     * 中标数 = 已中标(WON) 的项目数。
     * 当 startDate/endDate 均存在时，补全区间内所有月份（缺失月份显示 0），
     * 确保 X 轴从区间起始月开始，连续到结束月。
     */
    TrendComputationResult computeTimeTrend(List<TimeDimensionRow> rows,
                                            LocalDate startDate, LocalDate endDate) {
        // 按年月分组
        Map<String, MutableTrendBucket> bucketMap = new LinkedHashMap<>();
        for (TimeDimensionRow row : rows) {
            String key = buildPeriodKey(row.year(), row.month());
            MutableTrendBucket bucket = bucketMap.computeIfAbsent(key, MutableTrendBucket::new);
            // 投标数口径：只统计已中标 + 未中标的项目
            if (row.status() == com.xiyu.bid.entity.Project.Status.WON
                    || row.status() == com.xiyu.bid.entity.Project.Status.LOST) {
                bucket.bidCount++;
            }
            if (row.status() == com.xiyu.bid.entity.Project.Status.WON) {
                bucket.winCount++;
            }
        }

        // 生成完整月份序列：startDate ~ endDate 之间所有月份（正序），
        // 缺失月份补 0，确保 X 轴从区间起始月开始连续显示。
        List<String> sortedKeys = buildContinuousMonthKeys(startDate, endDate, bucketMap.keySet());

        List<String> categories = new ArrayList<>();
        List<Long> bidSeries = new ArrayList<>();
        List<Long> winSeries = new ArrayList<>();
        List<Double> winRateSeries = new ArrayList<>();

        for (String key : sortedKeys) {
            categories.add(key);
            MutableTrendBucket bucket = bucketMap.get(key);
            long bid = bucket == null ? 0L : bucket.bidCount;
            long win = bucket == null ? 0L : bucket.winCount;
            bidSeries.add(bid);
            winSeries.add(win);
            double winRate = bid == 0 ? 0.0
                    : Math.round(win * 1000.0 / bid) / 10.0;
            winRateSeries.add(winRate);
        }

        return new TrendComputationResult(categories, bidSeries, winSeries, winRateSeries);
    }

    /**
     * 生成 startDate~endDate 之间所有月份键（"YYYY-MM"），正序排列。
     * 区间外但实际有数据的月份也追加到末尾（保持正序），避免遗漏数据。
     * 当 startDate/endDate 任一为空时，退化为仅按已有数据月份正序排序。
     */
    private List<String> buildContinuousMonthKeys(LocalDate startDate, LocalDate endDate,
                                                  java.util.Set<String> dataKeys) {
        if (startDate == null || endDate == null) {
            List<String> fallback = new ArrayList<>(dataKeys);
            fallback.sort(String::compareTo);
            return fallback;
        }
        List<String> keys = new ArrayList<>();
        YearMonth cur = YearMonth.from(startDate);
        YearMonth end = YearMonth.from(endDate);
        while (!cur.isAfter(end)) {
            keys.add(String.format("%d-%02d", cur.getYear(), cur.getMonthValue()));
            cur = cur.plusMonths(1);
        }
        // 追加区间外但实际有数据的月份（防御性，正常不会触发）
        for (String k : dataKeys) {
            if (!keys.contains(k)) {
                keys.add(k);
            }
        }
        keys.sort(String::compareTo);
        return keys;
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