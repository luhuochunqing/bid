package com.xiyu.bid.marketinsight.core;

import java.util.List;
import java.util.Map;

/**
 * Pure core policy for computing opportunity scores and predictions.
 * No state, no dependencies, no side effects.
 */
public final class OpportunityScoringPolicy {

    private static final double FREQ_WEIGHT = 0.30;
    private static final double RECENCY_WEIGHT = 0.25;
    private static final double BUDGET_WEIGHT = 0.25;
    private static final double CYCLE_WEIGHT = 0.20;

    private OpportunityScoringPolicy() {
    }

    /**
     * Compute opportunity score from purchasing signals.
     *
     * @param frequency      historical purchase frequency
     * @param monthsSinceLast months since last purchase
     * @param avgBudgetInWan  average budget in wan-yuan
     * @param hasCycle        whether the purchaser has a cyclical pattern
     * @return OpportunityScore with total and component breakdown
     */
    public static OpportunityScore computeScore(
            final int frequency,
            final int monthsSinceLast,
            final long avgBudgetInWan,
            final boolean hasCycle) {

        double freqScore = Math.min(100, frequency * 10);
        double recencyScore = Math.max(0, 100 - monthsSinceLast * 8);
        double budgetScore = Math.min(100, avgBudgetInWan / 50.0);
        double cycleScore = hasCycle ? 80 : 30;

        double freqComponent = freqScore * FREQ_WEIGHT;
        double recencyComponent = recencyScore * RECENCY_WEIGHT;
        double budgetComponent = budgetScore * BUDGET_WEIGHT;
        double cycleComponent = cycleScore * CYCLE_WEIGHT;

        int total = (int) Math.round(
                freqComponent + recencyComponent
                        + budgetComponent + cycleComponent);
        total = Math.max(0, Math.min(100, total));

        return new OpportunityScore(total, freqComponent,
                recencyComponent, budgetComponent, cycleComponent);
    }

    /**
     * Predict the next purchase window based on historical month counts.
     *
     * @param monthCounts   map of month to occurrence count
     * @param currentMonth  current month (1-12)
     * @return PredictedWindow with label and confidence
     */
    public static PredictedWindow predictNextWindow(
            final Map<Integer, Integer> monthCounts,
            final int currentMonth,
            final int currentYear) {
        if (monthCounts == null || monthCounts.isEmpty()) {
            return new PredictedWindow(
                    currentYear + "-01",
                    1, 0.0);
        }

        int totalCount = monthCounts.values().stream()
                .mapToInt(Integer::intValue).sum();

        // Find the soonest month after currentMonth with activity
        var futureMonths = monthCounts.entrySet().stream()
                .filter(e -> e.getKey() > currentMonth)
                .sorted(Map.Entry.comparingByKey())
                .toList();

        if (!futureMonths.isEmpty()) {
            var best = futureMonths.get(0);
            double confidence = (double) best.getValue() / totalCount;
            confidence = Math.max(0.0, Math.min(1.0, confidence));
            return new PredictedWindow(
                    currentYear + "-" + String.format("%02d", best.getKey()),
                    best.getKey(), confidence);
        }

        // Wrap to next year: find the earliest month overall
        var allSorted = monthCounts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();

        var best = allSorted.get(0);
        int year = currentYear + 1;
        double confidence = (double) best.getValue() / totalCount;
        confidence = Math.max(0.0, Math.min(1.0, confidence));
        return new PredictedWindow(
                year + "-" + String.format("%02d", best.getKey()),
                best.getKey(), confidence);
    }

    /**
     * Classify the cycle type based on monthly distribution.
     *
     * @param monthCounts map of month to occurrence count
     * @return cycle type description
     */
    public static String classifyCycleType(
            final Map<Integer, Integer> monthCounts) {
        if (monthCounts == null || monthCounts.isEmpty()) {
            return "不定期采购";
        }

        double average = monthCounts.values().stream()
                .mapToInt(Integer::intValue).average().orElse(0);

        List<Integer> peakMonths = monthCounts.entrySet().stream()
                .filter(e -> e.getValue() > average * 1.5)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();

        // Single or double dominant peak = annual concentrated
        if (peakMonths.size() >= 1 && peakMonths.size() <= 2) {
            return "年度集中采购";
        }

        // Quarterly: 4+ months at ~3-month intervals with even distribution
        List<Integer> sortedMonths = monthCounts.keySet().stream()
                .sorted().toList();
        if (sortedMonths.size() >= 4 && hasQuarterlyInterval(sortedMonths)
                && isEvenlyDistributed(monthCounts, average)) {
            return "季度规律采购";
        }

        return "不定期采购";
    }

    private static boolean isEvenlyDistributed(
            final Map<Integer, Integer> monthCounts, final double average) {
        if (average <= 0) return false;
        double sumSqDiff = 0;
        for (int count : monthCounts.values()) {
            double diff = count - average;
            sumSqDiff += diff * diff;
        }
        double stdDev = Math.sqrt(sumSqDiff / monthCounts.size());
        // Coefficient of variation < 0.3 means relatively even
        return stdDev / average < 0.3;
    }

    /**
     * Generate a human-readable reasoning summary.
     *
     * @param customerName      customer name
     * @param industry          industry category
     * @param frequency         purchase frequency
     * @param predictedCategory predicted project category
     * @param confidence        prediction confidence (0-1)
     * @return formatted summary string
     */
    public static String generateReasoningSummary(
            final String customerName,
            final String industry,
            final int frequency,
            final String predictedCategory,
            final double confidence) {

        String freqDesc = frequencyDescription(frequency);
        String cycleDesc = frequency >= 6 ? "持续性" : "间歇性";
        int pct = (int) Math.round(confidence * 100);

        return customerName + "历史采购规律显示" + freqDesc
                + "，在" + industry + "领域有" + cycleDesc
                + "采购需求，预测" + predictedCategory
                + "类项目置信度" + pct + "%";
    }

    private static boolean hasQuarterlyInterval(
            final List<Integer> peakMonths) {
        if (peakMonths.size() < 4) {
            return false;
        }
        // Check if peaks are roughly at ~3-month intervals
        int intervalsWithinRange = 0;
        for (int i = 1; i < peakMonths.size(); i++) {
            int gap = peakMonths.get(i) - peakMonths.get(i - 1);
            if (gap >= 2 && gap <= 4) {
                intervalsWithinRange++;
            }
        }
        return intervalsWithinRange >= 3;
    }

    private static String frequencyDescription(final int frequency) {
        if (frequency >= 12) return "高频采购（年均12次及以上）";
        if (frequency >= 6) return "中高频采购（年均6-11次）";
        if (frequency >= 3) return "中频采购（年均3-5次）";
        if (frequency >= 2) return "低频采购（年均2次）";
        return "极低频采购（年均1次）";
    }

    /** Opportunity score with breakdown. */
    public record OpportunityScore(int total, double frequencyComponent,
                                   double recencyComponent,
                                   double budgetComponent,
                                   double cycleComponent) {
    }

    /** Predicted purchase window. */
    public record PredictedWindow(String windowLabel, int predictedMonth,
                                  double confidence) {
    }
}
