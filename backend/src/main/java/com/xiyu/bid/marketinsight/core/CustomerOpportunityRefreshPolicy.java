package com.xiyu.bid.marketinsight.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Pure core policy for recomputing customer opportunity signals from tender snapshots.
 */
public final class CustomerOpportunityRefreshPolicy {

    private static final BigDecimal PREDICTED_MIN_MULTIPLIER = BigDecimal.valueOf(0.8);
    private static final BigDecimal PREDICTED_MAX_MULTIPLIER = BigDecimal.valueOf(1.2);
    private static final BigDecimal WAN_DIVISOR = BigDecimal.valueOf(10_000L);

    private CustomerOpportunityRefreshPolicy() {
    }

    public static Optional<RefreshEvaluation> evaluate(
            final String purchaserHash,
            final List<CustomerOpportunityTenderSnapshot> group,
            final LocalDateTime referenceTime) {
        if (purchaserHash == null || purchaserHash.isBlank()
                || group == null || group.isEmpty()
                || referenceTime == null) {
            return Optional.empty();
        }

        CustomerOpportunityTenderSnapshot first = group.get(0);
        int frequency = group.size();
        BigDecimal avgBudgetYuan = computeAvgBudgetYuan(group);
        long avgBudgetWan = avgBudgetYuan.divide(WAN_DIVISOR, 0, RoundingMode.HALF_UP).longValue();
        int monthsSinceLast = computeMonthsSinceLast(group, referenceTime);
        Map<Integer, Integer> monthCounts = collectMonthCounts(group);

        String cycleType = OpportunityScoringPolicy.classifyCycleType(monthCounts);
        boolean hasCycle = "年度集中采购".equals(cycleType) || "季度规律采购".equals(cycleType);
        OpportunityScoringPolicy.OpportunityScore score =
                OpportunityScoringPolicy.computeScore(frequency, monthsSinceLast, avgBudgetWan, hasCycle);
        OpportunityScoringPolicy.PredictedWindow predictedWindow =
                OpportunityScoringPolicy.predictNextWindow(
                        monthCounts,
                        referenceTime.getMonthValue(),
                        referenceTime.getYear());

        return Optional.of(new RefreshEvaluation(
                purchaserHash,
                first.purchaserName(),
                first.industry(),
                score.total(),
                first.industry(),
                avgBudgetYuan.multiply(PREDICTED_MIN_MULTIPLIER),
                avgBudgetYuan.multiply(PREDICTED_MAX_MULTIPLIER),
                predictedWindow.windowLabel(),
                BigDecimal.valueOf(predictedWindow.confidence()),
                OpportunityScoringPolicy.generateReasoningSummary(
                        first.purchaserName(),
                        first.industry(),
                        frequency,
                        first.industry(),
                        predictedWindow.confidence()),
                group.stream()
                        .map(CustomerOpportunityTenderSnapshot::tenderId)
                        .filter(Objects::nonNull)
                        .map(String::valueOf)
                        .collect(Collectors.joining(",")),
                group.stream()
                        .map(CustomerOpportunityTenderSnapshot::industry)
                        .filter(Objects::nonNull)
                        .filter(industry -> !industry.isBlank())
                        .distinct()
                        .collect(Collectors.joining(",")),
                avgBudgetYuan,
                cycleType,
                frequency,
                monthCounts.keySet().stream()
                        .sorted()
                        .map(String::valueOf)
                        .collect(Collectors.joining(","))));
    }

    private static BigDecimal computeAvgBudgetYuan(
            final List<CustomerOpportunityTenderSnapshot> group) {
        BigDecimal total = BigDecimal.ZERO;
        for (CustomerOpportunityTenderSnapshot snapshot : group) {
            if (snapshot.budget() != null) {
                total = total.add(snapshot.budget());
            }
        }
        return total.divide(BigDecimal.valueOf(group.size()), 2, RoundingMode.HALF_UP);
    }

    private static int computeMonthsSinceLast(
            final List<CustomerOpportunityTenderSnapshot> group,
            final LocalDateTime referenceTime) {
        LocalDateTime latest = null;
        for (CustomerOpportunityTenderSnapshot snapshot : group) {
            LocalDateTime createdAt = snapshot.createdAt();
            if (createdAt != null && (latest == null || createdAt.isAfter(latest))) {
                latest = createdAt;
            }
        }
        if (latest == null) {
            return 999;
        }
        long days = Duration.between(latest, referenceTime).toDays();
        return (int) Math.max(0L, days) / 30;
    }

    private static Map<Integer, Integer> collectMonthCounts(
            final List<CustomerOpportunityTenderSnapshot> group) {
        Map<Integer, Integer> monthCounts = new LinkedHashMap<>();
        for (CustomerOpportunityTenderSnapshot snapshot : group) {
            if (snapshot.createdAt() != null) {
                monthCounts.merge(snapshot.createdAt().getMonthValue(), 1, Integer::sum);
            }
        }
        return monthCounts;
    }

    public record RefreshEvaluation(
            String purchaserHash,
            String purchaserName,
            String industry,
            int opportunityScore,
            String predictedCategory,
            BigDecimal predictedBudgetMin,
            BigDecimal predictedBudgetMax,
            String predictedWindow,
            BigDecimal confidence,
            String reasoningSummary,
            String evidenceRecordIds,
            String mainCategories,
            BigDecimal avgBudget,
            String cycleType,
            int frequency,
            String periodMonths) {
    }
}
