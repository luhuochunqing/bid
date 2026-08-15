package com.xiyu.bid.analytics.service;

import com.xiyu.bid.analytics.service.DimensionRow;
import com.xiyu.bid.analytics.service.TrendAnalysisQueryService.TimeDimensionRow;
import com.xiyu.bid.entity.Project;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
class TrendAnalysisComputationService {

    TrendComputationResult computeDimensionTrend(List<DimensionRow> rows) {
        Map<String, MutableTrendBucket> bucketMap = new LinkedHashMap<>();
        for (DimensionRow row : rows) {
            String key = row.category() != null ? row.category() : "未知";
            MutableTrendBucket bucket = bucketMap.computeIfAbsent(key, MutableTrendBucket::new);
            if (row.status() == Project.Status.WON
                    || row.status() == Project.Status.LOST) {
                bucket.bidCount++;
            }
            if (row.status() == Project.Status.WON) {
                bucket.winCount++;
            }
        }

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

    TrendComputationResult computeTimeTrend(List<TimeDimensionRow> rows,
                                            LocalDate startDate, LocalDate endDate,
                                            String timeDimension) {
        String td = timeDimension != null ? timeDimension : "month";

        Map<String, MutableTrendBucket> bucketMap = new LinkedHashMap<>();
        for (TimeDimensionRow row : rows) {
            String key = buildPeriodKey(row, td);
            MutableTrendBucket bucket = bucketMap.computeIfAbsent(key, MutableTrendBucket::new);
            if (row.status() == com.xiyu.bid.entity.Project.Status.WON
                    || row.status() == com.xiyu.bid.entity.Project.Status.LOST) {
                bucket.bidCount++;
            }
            if (row.status() == com.xiyu.bid.entity.Project.Status.WON) {
                bucket.winCount++;
            }
        }

        List<String> sortedKeys = buildContinuousKeys(startDate, endDate, bucketMap.keySet(), td);

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

    private String buildPeriodKey(TimeDimensionRow row, String timeDimension) {
        return switch (timeDimension != null ? timeDimension : "month") {
            case "year" -> row.year() != null ? String.valueOf(row.year()) : "未知";
            case "week" -> row.year() != null && row.week() != null
                    ? String.format("%d-W%02d", row.year(), row.week()) : "未知";
            case "day" -> row.year() != null && row.month() != null && row.day() != null
                    ? String.format("%d-%02d-%02d", row.year(), row.month(), row.day()) : "未知";
            default -> {
                if (row.year() == null) yield "未知";
                if (row.month() == null) yield String.valueOf(row.year());
                yield String.format("%d-%02d", row.year(), row.month());
            }
        };
    }

    private List<String> buildContinuousKeys(LocalDate startDate, LocalDate endDate,
                                              java.util.Set<String> dataKeys, String timeDimension) {
        if (startDate == null || endDate == null) {
            List<String> fallback = new ArrayList<>(dataKeys);
            fallback.sort(String::compareTo);
            return fallback;
        }

        String td = timeDimension != null ? timeDimension : "month";
        List<String> keys = new ArrayList<>();

        switch (td) {
            case "year" -> {
                int curYear = startDate.getYear();
                int endYear = endDate.getYear();
                while (curYear <= endYear) {
                    keys.add(String.valueOf(curYear));
                    curYear++;
                }
            }
            case "week" -> {
                LocalDate cur = startDate;
                while (!cur.isAfter(endDate)) {
                    int year = cur.get(IsoFields.WEEK_BASED_YEAR);
                    int week = cur.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
                    keys.add(String.format("%d-W%02d", year, week));
                    cur = cur.plusWeeks(1);
                }
            }
            case "day" -> {
                LocalDate cur = startDate;
                while (!cur.isAfter(endDate)) {
                    keys.add(cur.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
                    cur = cur.plusDays(1);
                }
            }
            default -> {
                YearMonth cur = YearMonth.from(startDate);
                YearMonth end = YearMonth.from(endDate);
                while (!cur.isAfter(end)) {
                    keys.add(String.format("%d-%02d", cur.getYear(), cur.getMonthValue()));
                    cur = cur.plusMonths(1);
                }
            }
        }

        for (String k : dataKeys) {
            if (!keys.contains(k)) {
                keys.add(k);
            }
        }
        keys.sort(String::compareTo);
        return keys;
    }

    public record TrendComputationResult(
            List<String> categories,
            List<Long> bidSeries,
            List<Long> winSeries,
            List<Double> winRateSeries
    ) {
    }

    private static final class MutableTrendBucket {
        private long bidCount;
        private long winCount;

        MutableTrendBucket(@SuppressWarnings("unused") String period) {
        }
    }
}
