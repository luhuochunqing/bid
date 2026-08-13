package com.xiyu.bid.analytics.service;

import com.xiyu.bid.analytics.model.CompetitorAnalysisRow;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
class CompetitorAnalysisComputationService {

    /**
     * 按竞品公司名称分组计算折扣统计（默认模式）。
     * 返回 Map<competitorName, DiscountStats>。
     */
    Map<String, DiscountStats> computeDiscountByCompetitor(List<CompetitorAnalysisRow> rows) {
        Map<String, MutableDiscountStats> statsMap = new LinkedHashMap<>();
        for (CompetitorAnalysisRow row : rows) {
            Integer discount = parseDiscount(row.discount());
            if (discount == null) {
                continue;
            }
            String name = row.competitorName();
            if (name == null || name.isBlank()) {
                continue;
            }
            MutableDiscountStats stats = statsMap.computeIfAbsent(name, MutableDiscountStats::new);
            stats.min = stats.min == null ? discount : Math.min(stats.min, discount);
            stats.max = stats.max == null ? discount : Math.max(stats.max, discount);
            stats.sum += discount;
            stats.count++;
        }
        return statsMap.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().toImmutable(),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }

    /**
     * 按招标主体分组计算折扣统计（分组模式）。
     * 返回 Map<tenderEntity, Map<competitorName, DiscountStats>>。
     */
    Map<String, Map<String, DiscountStats>> computeDiscountByTenderEntity(List<CompetitorAnalysisRow> rows) {
        Map<String, Map<String, MutableDiscountStats>> grouped = new LinkedHashMap<>();
        for (CompetitorAnalysisRow row : rows) {
            Integer discount = parseDiscount(row.discount());
            if (discount == null) {
                continue;
            }
            String name = row.competitorName();
            if (name == null || name.isBlank()) {
                continue;
            }
            String entity = row.tenderEntity();
            if (entity == null || entity.isBlank()) {
                continue;
            }
            Map<String, MutableDiscountStats> competitorMap = grouped.computeIfAbsent(entity, k -> new LinkedHashMap<>());
            MutableDiscountStats stats = competitorMap.computeIfAbsent(name, MutableDiscountStats::new);
            stats.min = stats.min == null ? discount : Math.min(stats.min, discount);
            stats.max = stats.max == null ? discount : Math.max(stats.max, discount);
            stats.sum += discount;
            stats.count++;
        }
        return grouped.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().entrySet().stream()
                                .collect(Collectors.toMap(
                                        Map.Entry::getKey,
                                        ce -> ce.getValue().toImmutable(),
                                        (a, b) -> a,
                                        LinkedHashMap::new
                                )),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }

    /**
     * 计算整体平均折扣（分组模式使用）。
     * 各竞品公司平均折扣之和 ÷ 有有效数据的竞品公司数量。
     */
    double computeOverallAverageDiscount(Map<String, DiscountStats> competitorStats) {
        if (competitorStats.isEmpty()) {
            return 0.0;
        }
        double sum = competitorStats.values().stream()
                .mapToDouble(s -> s.average)
                .sum();
        return Math.round(sum / competitorStats.size() * 10.0) / 10.0;
    }

    Integer parseDiscount(String discount) {
        if (discount == null || discount.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(discount.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public record DiscountStats(
            int min,
            int max,
            double average
    ) {
    }

    private static final class MutableDiscountStats {
        private String name;
        private Integer min;
        private Integer max;
        private int sum;
        private int count;

        MutableDiscountStats() {
        }

        MutableDiscountStats(String name) {
            this.name = name;
        }

        DiscountStats toImmutable() {
            double avg = count == 0 ? 0.0 : Math.round((double) sum / count * 10.0) / 10.0;
            return new DiscountStats(
                    min == null ? 0 : min,
                    max == null ? 0 : max,
                    avg
            );
        }
    }
}