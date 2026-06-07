package com.xiyu.bid.marketinsight.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure core policy for trend analysis and forecast generation.
 * No state, no dependencies, no side effects.
 */
public final class TrendAnalysisPolicy {

    private TrendAnalysisPolicy() {
    }

    /**
     * Compute trend indicators for an industry.
     *
     * @param industry           industry name
     * @param currentPeriodCount count in current period
     * @param previousPeriodCount count in previous period
     * @param totalAmount        total amount in yuan
     * @return TrendResult with growth, trend, and hot level
     */
    public static TrendResult computeTrend(
            final String industry,
            final int currentPeriodCount,
            final int previousPeriodCount,
            final long totalAmount) {

        long growth = computeGrowth(currentPeriodCount, previousPeriodCount);
        String trend = classifyTrend(growth);
        int hotLevel = computeHotLevel(currentPeriodCount, growth);

        return new TrendResult(industry, currentPeriodCount,
                totalAmount, growth, trend, hotLevel);
    }

    /**
     * Compute purchaser purchasing pattern from tender records.
     *
     * @param tenders list of tender records for one purchaser
     * @return PurchaserPatternResult with frequency, period, and opportunity
     */
    public static PurchaserPatternResult computePurchaserPattern(
            final List<PurchaserTenderRecord> tenders) {
        if (tenders == null || tenders.isEmpty()) {
            return new PurchaserPatternResult("", "", 0, "", 0L, 1);
        }

        String name = extractPurchaserName(tenders.get(0).title());
        String industry = IndustryClassificationPolicy.classifyIndustry(
                tenders.get(0).title());
        int frequency = tenders.size();
        String period = extractTopMonths(tenders);
        long avgBudget = computeAvgBudget(tenders);
        int opportunity = scoreOpportunity(frequency);

        return new PurchaserPatternResult(
                name, industry, frequency, period, avgBudget, opportunity);
    }

    /**
     * Generate forecast tips from trend results.
     * Only includes trends with hotLevel &gt;= 3 or trend = "up".
     * Maximum 4 tips returned.
     *
     * @param trends list of trend results
     * @return list of forecast tips
     */
    public static List<ForecastTip> generateForecastTips(
            final List<TrendResult> trends) {
        if (trends == null) {
            return List.of();
        }

        var tips = new ArrayList<ForecastTip>();
        for (var t : trends) {
            if (tips.size() >= 4) {
                break;
            }
            if (t.hotLevel() >= 3 || "up".equals(t.trend())) {
                String description = trendDescription(t.trend());
                String text = t.industry() + "类产品近期需求"
                        + description + "，建议重点关注";
                String color = trendColor(t.trend());
                tips.add(new ForecastTip(text, color));
            }
        }
        return List.copyOf(tips);
    }

    private static long computeGrowth(final int current,
                                      final int previous) {
        if (previous == 0 && current > 0) {
            return 100;
        }
        if (previous == 0) {
            return 0;
        }
        return (long) ((current - previous) * 100 / previous);
    }

    private static String classifyTrend(final long growth) {
        if (growth > 10) {
            return "up";
        }
        if (growth < -10) {
            return "down";
        }
        return "stable";
    }

    private static int computeHotLevel(final int count,
                                       final long growth) {
        int level = 1;
        if (count > 50) level++;
        if (count > 100) level++;
        if (count > 200) level++;
        if (growth > 15) level++;
        if (growth > 30) level++;
        return Math.min(level, 5);
    }

    private static String extractPurchaserName(final String title) {
        var result = PurchaserExtractionPolicy.extractPurchaser(title);
        return result.found() ? result.purchaserName() : "";
    }

    private static String extractTopMonths(
            final List<PurchaserTenderRecord> tenders) {
        var monthCounts = new LinkedHashMap<Integer, Integer>();
        for (var t : tenders) {
            monthCounts.merge(t.publishMonth(), 1, Integer::sum);
        }

        var sorted = monthCounts.entrySet().stream()
                .sorted(Map.Entry.<Integer, Integer>comparingByValue()
                        .reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(3)
                .map(e -> e.getKey() + "月")
                .toList();

        return String.join("、", sorted);
    }

    private static long computeAvgBudget(
            final List<PurchaserTenderRecord> tenders) {
        long total = 0;
        for (var t : tenders) {
            total += t.budgetInWan();
        }
        return tenders.isEmpty() ? 0L : total / tenders.size();
    }

    private static int scoreOpportunity(final int frequency) {
        if (frequency >= 12) return 5;
        if (frequency >= 6) return 4;
        if (frequency >= 3) return 3;
        if (frequency >= 2) return 2;
        return 1;
    }

    private static String trendDescription(final String trend) {
        return switch (trend) {
            case "up" -> "上升";
            case "down" -> "下降";
            default -> "平稳";
        };
    }

    private static String trendColor(final String trend) {
        return switch (trend) {
            case "up" -> "#67c23a";
            case "down" -> "#f56c6c";
            default -> "#e6a23c";
        };
    }

    /** Trend analysis result. */
    public record TrendResult(String industry, int count, long amount,
                              long growth, String trend, int hotLevel) {
    }

    /** Purchaser purchasing pattern result. */
    public record PurchaserPatternResult(String name, String industry,
                                         int frequency, String period,
                                         long avgBudget, int opportunity) {
    }

    /** Forecast tip with text and color. */
    public record ForecastTip(String text, String color) {
    }

    /** Input record for purchaser tender data. */
    public record PurchaserTenderRecord(String title, long budgetInWan,
                                        int publishYear,
                                        int publishMonth) {
    }
}
