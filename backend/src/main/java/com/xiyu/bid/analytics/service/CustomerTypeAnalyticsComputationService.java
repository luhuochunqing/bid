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

@Component
class CustomerTypeAnalyticsComputationService {

    static final String UNCATEGORIZED_CUSTOMER_TYPE = "未分类";
    private static final String ALL_FILTER = "ALL";

    List<CustomerTypeAggregate> summarize(List<CustomerTypeProjectRow> rows) {
        long totalProjects = rows.size();
        Map<String, MutableCustomerTypeAggregate> aggregates = new LinkedHashMap<>();
        for (CustomerTypeProjectRow row : rows) {
            String cType = normalizeCustomerType(row.customerType());
            MutableCustomerTypeAggregate aggregate = aggregates.computeIfAbsent(
                    cType,
                    MutableCustomerTypeAggregate::new
            );
            aggregate.projectCount++;
            if (!row.projectStatus().isTerminal()) {
                aggregate.activeProjectCount++;
            }
            if (isWon(row)) {
                aggregate.wonCount++;
            }
            aggregate.totalAmount = aggregate.totalAmount.add(defaultAmount(row.amount()));
        }

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
                .filter(row -> normalizeCustomerType(row.customerType()).equals(normalizedFilter))
                .toList();
    }

    String normalizeCustomerType(String customerType) {
        if (customerType == null || customerType.isBlank()) {
            return UNCATEGORIZED_CUSTOMER_TYPE;
        }
        return customerType.trim();
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
