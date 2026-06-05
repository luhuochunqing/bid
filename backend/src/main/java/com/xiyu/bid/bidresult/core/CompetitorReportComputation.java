package com.xiyu.bid.bidresult.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * FP-Java Profile：声明式 Stream 风格，消除所有可变集合操作与 setter。
 */
public final class CompetitorReportComputation {

    private CompetitorReportComputation() {
    }

    public static List<CompetitorReportRow> aggregate(List<CompetitorWinRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        return rows.stream()
                .collect(Collectors.groupingBy(CompetitorReportComputation::resolveKey))
                .entrySet().stream()
                .map(entry -> toReportRow(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(CompetitorReportRow::projectCount).reversed())
                .toList();
    }

    private static String resolveKey(CompetitorWinRow row) {
        return row.competitorName() != null && !row.competitorName().isBlank()
                ? row.competitorName()
                : row.competitorId() != null ? "竞争对手#" + row.competitorId() : "未知竞争对手";
    }

    private static CompetitorReportRow toReportRow(String company, List<CompetitorWinRow> rows) {
        long skuTotal = rows.stream()
                .map(CompetitorWinRow::skuCount)
                .filter(Objects::nonNull)
                .mapToLong(Integer::longValue)
                .sum();

        String category = modeOf(rows.stream().map(CompetitorWinRow::category).toList());
        String discount = averageDiscount(rows.stream().map(CompetitorWinRow::discount).toList());
        String payment = modeOf(rows.stream().map(CompetitorWinRow::paymentTerms).toList());

        double avgWin = rows.stream()
                .map(CompetitorWinRow::winProbability)
                .filter(Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue)
                .average()
                .orElse(0.0);

        return new CompetitorReportRow(
                company,
                skuTotal,
                blankAsPlaceholder(category),
                blankAsPlaceholder(discount),
                blankAsPlaceholder(payment),
                formatPercent(avgWin),
                rows.size(),
                trendOf(avgWin)
        );
    }

    private static String modeOf(List<String> values) {
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("");
    }

    private static String averageDiscount(List<String> discounts) {
        List<BigDecimal> numeric = discounts.stream()
                .filter(Objects::nonNull)
                .map(raw -> raw.replace("%", "").replace("折", "").trim())
                .filter(s -> !s.isEmpty())
                .map(s -> {
                    try {
                        return new BigDecimal(s);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();

        if (numeric.isEmpty()) {
            return modeOf(discounts);
        }

        BigDecimal avg = numeric.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(numeric.size()), 1, RoundingMode.HALF_UP);
        
        return avg + "%";
    }

    private static String formatPercent(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP) + "%";
    }

    private static String trendOf(double avgWin) {
        if (avgWin >= 60.0) return "up";
        if (avgWin >= 40.0) return "flat";
        return "down";
    }

    private static String blankAsPlaceholder(String value) {
        return (value == null || value.isBlank()) ? "未采集" : value;
    }
}
