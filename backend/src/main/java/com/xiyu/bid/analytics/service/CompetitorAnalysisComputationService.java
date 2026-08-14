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
            Double discount = parseDiscount(row.discount());
            if (discount == null) {
                continue;
            }
            String name = row.competitorName();
            if (name == null || name.isBlank()) {
                continue;
            }
            MutableDiscountStats stats = statsMap.computeIfAbsent(name, k -> new MutableDiscountStats());
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
            Double discount = parseDiscount(row.discount());
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
            MutableDiscountStats stats = competitorMap.computeIfAbsent(name, k -> new MutableDiscountStats());
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
     * 解析折扣值（PRD §9.11：过滤 null/空/非数值，CAST 为数值）。
     * discount 字段为 varchar，如 "95" 或 "95折"，需提取数值部分。
     */
    Double parseDiscount(String discount) {
        if (discount == null || discount.isBlank()) {
            return null;
        }
        try {
            String cleaned = discount.trim().replaceAll("[^0-9.]", "");
            if (cleaned.isEmpty()) {
                return null;
            }
            double val = Double.parseDouble(cleaned);
            return val > 0 ? val : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 四舍五入保留 1 位小数。
     */
    double round1(double val) {
        return Math.round(val * 10.0) / 10.0;
    }

    public record DiscountStats(
            double min,
            double max,
            double average
    ) {
    }

    private static final class MutableDiscountStats {
        private Double min;
        private Double max;
        private double sum;
        private int count;

        MutableDiscountStats() {
        }

        DiscountStats toImmutable() {
            double avg = count == 0 ? 0.0 : Math.round(sum / count * 10.0) / 10.0;
            return new DiscountStats(
                    min == null ? 0.0 : min,
                    max == null ? 0.0 : max,
                    avg
            );
        }
    }
}
