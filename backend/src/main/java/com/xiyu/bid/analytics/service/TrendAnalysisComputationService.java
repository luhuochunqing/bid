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

    /**
     * 按维度标签分组，计算投标数、中标数、中标率。
     * 投标数 = 已中标(WON) + 未中标(LOST) 的项目数。
     * 中标数 = 已中标(WON) 的项目数。
     * 按 bidCount 降序排列（PRD 6.5）。
     * @param expectedCategories 可选，期望在 X 轴上出现的分类列表（数量为 0 的也会展示），
     *                           仅当 xAxis 为 customerType/projectType 时传入
     */
    TrendComputationResult computeDimensionTrend(List<DimensionRow> rows, List<String> expectedCategories) {
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

        // 补充期望分类中缺失的项（数量为 0 也在 X 轴上展示）
        if (expectedCategories != null) {
            for (String expected : expectedCategories) {
                bucketMap.computeIfAbsent(expected, MutableTrendBucket::new);
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
     * 项目状态维度专用计算：统计每个状态的所有项目数量（不限于 WON+LOST）。
     * 因为 X 轴本身就是项目状态，每个状态的所有项目都应计入数量。
     * 按 count 降序排列（PRD 6.5）。
     */
    TrendComputationResult computeProjectStatusTrend(List<DimensionRow> rows) {
        Map<String, MutableTrendBucket> bucketMap = new LinkedHashMap<>();
        for (DimensionRow row : rows) {
            String key = row.category() != null ? row.category() : "未知";
            MutableTrendBucket bucket = bucketMap.computeIfAbsent(key, MutableTrendBucket::new);
            // 项目状态维度：统计所有项目（每个状态的项目都计入 bidCount）
            bucket.bidCount++;
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
     * 按指定时间粒度（day/week/month/year）分组，计算投标数、中标数、中标率。
     * 投标数 = 已中标(WON) + 未中标(LOST) 的项目数。
     * 中标数 = 已中标(WON) 的项目数。
     * 当 startDate/endDate 均存在时，补全区间内所有时间槽（缺失显示 0），
     * 确保 X 轴从区间起始连续到结束。
     */
    TrendComputationResult computeTimeTrend(List<TimeDimensionRow> rows,
                                            LocalDate startDate, LocalDate endDate,
                                            String timeDimension) {
        String td = timeDimension != null ? timeDimension : "month";

        // 按时间粒度分组
        Map<String, MutableTrendBucket> bucketMap = new LinkedHashMap<>();
        for (TimeDimensionRow row : rows) {
            String key = buildPeriodKey(row, td);
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

        // 生成完整时间序列：startDate ~ endDate 之间所有时间槽（正序），
        // 缺失补 0，确保 X 轴从区间起始连续显示。
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

    /**
     * 根据时间粒度生成 period key。
     *   - month: "YYYY-MM"
     *   - week:  "YYYY-WXX"
     *   - day:   "YYYY-MM-DD"
     *   - year:  "YYYY"
     */
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

    /**
     * 生成 startDate~endDate 之间所有时间槽 key，按正序排列。
     * 根据 timeDimension 选择生成的粒度：
     *   - month: 逐月
     *   - week:  逐周（ISO 周基准）
     *   - day:   逐日
     *   - year:  逐年
     * 区间外但实际有数据的 key 也追加到末尾。
     * 当 startDate/endDate 任一为空时，退化为仅按已有数据 key 排序。
     */
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
                // month
                YearMonth cur = YearMonth.from(startDate);
                YearMonth end = YearMonth.from(endDate);
                while (!cur.isAfter(end)) {
                    keys.add(String.format("%d-%02d", cur.getYear(), cur.getMonthValue()));
                    cur = cur.plusMonths(1);
                }
            }
        }

        // 追加区间外但实际有数据的 key（防御性，正常不会触发）
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
        private final String period;
        private long bidCount;
        private long winCount;

        MutableTrendBucket(String period) {
            this.period = period;
        }
    }
}