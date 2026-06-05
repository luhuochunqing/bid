package com.xiyu.bid.analytics.service;

import com.xiyu.bid.analytics.dto.CompetitorData;
import com.xiyu.bid.analytics.dto.ProductLineData;
import com.xiyu.bid.analytics.dto.RegionalData;
import com.xiyu.bid.analytics.dto.TrendData;
import com.xiyu.bid.analytics.repository.DashboardAnalyticsRepository;
import com.xiyu.bid.entity.Tender;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

@Component
class DashboardAnalyticsTrendAndDistributionService {

    List<TrendData> buildTenderTrends(List<DashboardAnalyticsRepository.MonthlyTrendRow> rows) {
        return buildTrendData(rows, true);
    }

    List<TrendData> buildProjectTrends(List<DashboardAnalyticsRepository.MonthlyTrendRow> rows) {
        return buildTrendData(rows, false);
    }

    double calculateSuccessRate(DashboardAnalyticsRepository.OverviewSnapshot snapshot) {
        if (snapshot == null || normalizeCount(snapshot.biddedTenders()) == 0L) {
            return 0.0;
        }
        long biddedTenders = normalizeCount(snapshot.biddedTenders());
        long winningProjects = normalizeCount(snapshot.winningProjects());
        long normalizedWins = Math.min(winningProjects, biddedTenders);
        return (normalizedWins * 100.0) / biddedTenders;
    }

    List<RegionalData> buildRegionalDistribution(List<DashboardAnalyticsRepository.SourceAggregateRow> rows) {
        long totalBidCount = rows.stream()
                .mapToLong(row -> normalizeCount(row.bidCount()))
                .sum();
        return rows.stream()
                .map(row -> {
                    long count = normalizeCount(row.bidCount());
                    double percentage = totalBidCount == 0L ? 0.0 : (count * 100.0) / totalBidCount;
                    return RegionalData.builder()
                            .region(row.source())
                            .tenderCount(count)
                            .totalBudget(normalizeAmount(row.totalBidAmount()))
                            .percentage(percentage)
                            .build();
                })
                .toList();
    }

    List<CompetitorData> buildTopCompetitors(List<DashboardAnalyticsRepository.SourceAggregateRow> rows) {
        return rows.stream()
                .map(row -> {
                    long bidCount = normalizeCount(row.bidCount());
                    long winCount = normalizeCount(row.winCount());
                    double winRate = bidCount == 0 ? 0.0 : (winCount * 100.0) / bidCount;
                    return CompetitorData.builder()
                            .name(row.source())
                            .bidCount(bidCount)
                            .winCount(winCount)
                            .winRate(winRate)
                            .totalBidAmount(normalizeAmount(row.totalBidAmount()))
                            .build();
                })
                .toList();
    }

    Map<Tender.Status, Long> buildStatusDistributionCounts(List<DashboardAnalyticsRepository.StatusCountRow> rows) {
        Map<Tender.Status, Long> result = new java.util.EnumMap<>(Tender.Status.class);
        for (DashboardAnalyticsRepository.StatusCountRow row : rows) {
            if (row.status() == null) {
                continue;
            }
            result.put(row.status(), normalizeCount(row.count()));
        }
        return result;
    }

    List<ProductLineData> buildProductLinePerformance(List<DashboardAnalyticsRepository.ProductLineCandidateRow> rows) {
        Map<String, List<DashboardAnalyticsRepository.ProductLineCandidateRow>> grouped = rows.stream()
                .collect(Collectors.groupingBy(row -> classifyProductLine(row.title()), LinkedHashMap::new, Collectors.toList()));

        return grouped.entrySet().stream()
                .map(entry -> {
                    List<DashboardAnalyticsRepository.ProductLineCandidateRow> tenders = entry.getValue();
                    long bidCount = tenders.size();
                    long wonCount = tenders.stream()
                            .filter(tender -> tender.status() == Tender.Status.WON)
                            .count();
                    BigDecimal revenue = tenders.stream()
                            .map(DashboardAnalyticsRepository.ProductLineCandidateRow::budget)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal cost = revenue.multiply(new BigDecimal("0.72"));
                    double rate = bidCount > 0 ? (wonCount * 100.0) / bidCount : 0.0;
                    return ProductLineData.builder()
                            .name(entry.getKey())
                            .revenue(revenue)
                            .cost(cost)
                            .bids(bidCount)
                            .rate(rate)
                            .build();
                })
                .sorted(Comparator.comparing(ProductLineData::getRevenue, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
    }

    String classifyProductLine(String sourceText) {
        String text = sourceText == null ? "" : sourceText.toLowerCase(Locale.ROOT);
        if (text.contains("办公") || text.contains("oa") || text.contains("协同")) {
            return "智慧办公";
        }
        if (text.contains("云") || text.contains("cloud")) {
            return "云服务";
        }
        if (text.contains("工业") || text.contains("mes") || text.contains("制造")) {
            return "工业软件";
        }
        if (text.contains("数据中心") || text.contains("机房") || text.contains("idc")) {
            return "数据中心";
        }
        return "综合解决方案";
    }

    Map<String, Double> filterMatchCounts(List<String> values, String selected, Map<String, String> translator) {
        String normalizedSelected = normalizeFilterValue(selected);
        var candidateValues = values == null
                ? new java.util.LinkedHashSet<String>()
                : values.stream().filter(Objects::nonNull).filter(s -> !s.isBlank())
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

        return candidateValues.stream()
                .collect(Collectors.toMap(
                        value -> value,
                        value -> {
                            if (Objects.equals(normalizeFilterValue(value), normalizedSelected)) {
                                return 100.0;
                            }
                            return 0.0;
                        },
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    List<TrendData> buildTrendData(List<DashboardAnalyticsRepository.MonthlyTrendRow> rows, boolean includeValue) {
        requireNonNull(rows);
        List<TrendData> trends = new java.util.ArrayList<>();
        Long previousCount = null;

        for (DashboardAnalyticsRepository.MonthlyTrendRow row : rows) {
            long count = row.count() == null ? 0L : row.count();
            BigDecimal value = includeValue ? normalizeAmount(row.totalValue()) : null;
            Double changePercentage = null;
            if (previousCount != null && previousCount > 0) {
                changePercentage = ((double) (count - previousCount) * 100.0) / previousCount;
            }
            trends.add(TrendData.builder()
                    .period(String.format(Locale.ROOT, "%04d-%02d", row.year(), row.month()))
                    .count(count)
                    .value(value)
                    .changePercentage(changePercentage)
                    .build());
            previousCount = count;
        }
        return trends;
    }

    Double calculatePerformancePercentage(long numerator, long denominator) {
        return denominator == 0 ? 0.0 : (numerator * 100.0) / denominator;
    }

    int roundScore(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private long normalizeCount(Long value) {
        return value == null ? 0L : value;
    }

    private BigDecimal normalizeAmount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String normalizeFilterValue(String value) {
        if (value == null || value.isBlank()) {
            return "ALL";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
