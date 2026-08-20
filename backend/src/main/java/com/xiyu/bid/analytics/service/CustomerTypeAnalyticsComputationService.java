// Input: CustomerTypeProjectRow lists
// Output: CustomerTypeAggregate lists
// Pos: Computation Service/纯计算层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.analytics.service;

import com.xiyu.bid.analytics.model.CustomerTypeAggregate;
import com.xiyu.bid.analytics.model.CustomerTypeProjectRow;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
class CustomerTypeAnalyticsComputationService {

    private static final String ALL_FILTER = "ALL";

    // 5 种标准客户类型分类常量与归一化逻辑统一收敛到 CustomerTypeNormalizer（M1 趋势链路复用）
    private static final Set<String> ALLOWED_CATEGORIES = CustomerTypeNormalizer.ALLOWED_CATEGORIES;

    List<CustomerTypeAggregate> summarize(List<CustomerTypeProjectRow> rows) {
        // 初始化所有 5 种标准分类（即使 count=0 也包含在结果中）
        Map<String, MutableCustomerTypeAggregate> aggregates = new LinkedHashMap<>();
        for (String category : ALLOWED_CATEGORIES) {
            aggregates.put(category, new MutableCustomerTypeAggregate(category));
        }

        // 只统计精确匹配 5 种分类的项目，不匹配的跳过
        for (CustomerTypeProjectRow row : rows) {
            String cType = normalizeCustomerType(row.customerType());
            if (cType == null) continue;
            MutableCustomerTypeAggregate aggregate = aggregates.get(cType);
            if (aggregate == null) continue;
            aggregate.projectCount++;
            if (!row.projectStatus().isTerminal()) {
                aggregate.activeProjectCount++;
            }
            if (isWon(row)) {
                aggregate.wonCount++;
            }
            aggregate.totalAmount = aggregate.totalAmount.add(defaultAmount(row.amount()));
        }

        long totalProjects = aggregates.values().stream()
                .mapToLong(a -> a.projectCount)
                .sum();

        return aggregates.values().stream()
                .map(aggregate -> aggregate.toImmutable(totalProjects))
                .sorted((left, right) -> {
                    int countCompare = Long.compare(right.projectCount(), left.projectCount());
                    if (countCompare != 0) {
                        return countCompare;
                    }
                    int amountCompare = right.totalAmount().compareTo(left.totalAmount());
                    if (amountCompare != 0) {
                        return amountCompare;
                    }
                    return left.customerType().compareTo(right.customerType());
                })
                .toList();
    }

    List<CustomerTypeProjectRow> filterByCustomerType(
            List<CustomerTypeProjectRow> rows,
            String selectedCustomerType
    ) {
        String normalizedFilter = normalizeFilterValue(selectedCustomerType);
        if (ALL_FILTER.equals(normalizedFilter)) {
            return rows;
        }
        return rows.stream()
                .filter(row -> {
                    String normalized = normalizeCustomerType(row.customerType());
                    return normalized != null && normalized.equals(normalizedFilter);
                })
                .toList();
    }

    /**
     * 归一化入口：委托纯函数工具 {@link CustomerTypeNormalizer#normalize}，
     * 与 M1 趋势维度分组/筛选/下钻保持同一口径。
     */
    String normalizeCustomerType(String customerType) {
        return CustomerTypeNormalizer.normalize(customerType);
    }

    String deriveOutcome(CustomerTypeProjectRow row) {
        if (row.tenderStatus() == Tender.Status.WON) {
            return "WON";
        }
        if (row.tenderStatus() == Tender.Status.ABANDONED || row.projectStatus().isTerminal()) {
            return "LOST";
        }
        return "IN_PROGRESS";
    }

    boolean isWon(CustomerTypeProjectRow row) {
        return row.tenderStatus() == Tender.Status.WON;
    }

    private String normalizeFilterValue(String value) {
        if (value == null || value.isBlank()) {
            return ALL_FILTER;
        }
        String trimmed = value.trim();
        if (ALL_FILTER.equalsIgnoreCase(trimmed)) {
            return ALL_FILTER;
        }
        return trimmed;
    }

    private BigDecimal defaultAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private static final class MutableCustomerTypeAggregate {
        private final String customerType;
        private long projectCount;
        private long activeProjectCount;
        private long wonCount;
        private BigDecimal totalAmount = BigDecimal.ZERO;

        private MutableCustomerTypeAggregate(String pCustomerType) {
            this.customerType = pCustomerType;
        }

        private CustomerTypeAggregate toImmutable(long totalProjects) {
            double percentage = totalProjects == 0
                    ? 0.0
                    : BigDecimal.valueOf(projectCount * 100.0)
                            .divide(BigDecimal.valueOf(totalProjects), 2, RoundingMode.HALF_UP)
                            .doubleValue();
            double winRate = projectCount == 0
                    ? 0.0
                    : BigDecimal.valueOf(wonCount * 100.0)
                            .divide(BigDecimal.valueOf(projectCount), 2, RoundingMode.HALF_UP)
                            .doubleValue();
            return new CustomerTypeAggregate(
                    customerType,
                    projectCount,
                    activeProjectCount,
                    wonCount,
                    totalAmount,
                    percentage,
                    winRate
            );
        }
    }
}
