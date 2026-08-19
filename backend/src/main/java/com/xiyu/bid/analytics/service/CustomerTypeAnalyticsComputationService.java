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

    // 5 种标准客户类型分类（与 Excel 导入模板一致）
    private static final String CATEGORY_GOVERNMENT = "政府机关/事业单位/高校";
    private static final String CATEGORY_CENTRAL_SOE = "央企";
    private static final String CATEGORY_LOCAL_SOE = "地方国企";
    private static final String CATEGORY_PRIVATE = "民企";
    private static final String CATEGORY_FOREIGN = "港澳台及外企";
    private static final Set<String> ALLOWED_CATEGORIES = Set.of(
            CATEGORY_GOVERNMENT, CATEGORY_CENTRAL_SOE, CATEGORY_LOCAL_SOE,
            CATEGORY_PRIVATE, CATEGORY_FOREIGN
    );

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
     * 将项目的 customerType 字段值归一到 5 种标准分类之一。
     * 支持数据库中存储的英文枚举（GOVERNMENT/CENTRAL_SOE/LOCAL_SOE/PRIVATE/FOREIGN 等）及中文变体。
     * 不匹配任何分类时返回 null（跳过不统计）。
     */
    String normalizeCustomerType(String customerType) {
        if (customerType == null || customerType.isBlank()) {
            return null;
        }
        String trimmed = customerType.trim();
        // 1) 精确匹配中文标准分类
        if (ALLOWED_CATEGORIES.contains(trimmed)) {
            return trimmed;
        }
        String upper = trimmed.toUpperCase();

        // 2) 英文枚举精确与前缀匹配
        if (upper.equals("GOVERNMENT") || upper.equals("GOVERNMENT_INSTITUTION")) {
            return CATEGORY_GOVERNMENT;
        }
        if (upper.equals("CENTRAL_SOE") || upper.equals("STATE_OWNED")) {
            return CATEGORY_CENTRAL_SOE;
        }
        if (upper.equals("LOCAL_SOE")) {
            return CATEGORY_LOCAL_SOE;
        }
        if (upper.equals("PRIVATE") || upper.equals("PRIVATE_ENTERPRISE")) {
            return CATEGORY_PRIVATE;
        }
        if (upper.equals("FOREIGN") || upper.equals("FOREIGN_HK_MACAO_TW") || upper.startsWith("FOREIGN_")) {
            return CATEGORY_FOREIGN;
        }

        // 3) 中文模糊匹配：覆盖常见变体写法
        if (trimmed.contains("政府") || trimmed.contains("事业单位") || trimmed.contains("高校")
                || trimmed.contains("机关")) {
            return CATEGORY_GOVERNMENT;
        }
        if (trimmed.contains("央企") || trimmed.contains("中央企业")) {
            return CATEGORY_CENTRAL_SOE;
        }
        if (trimmed.contains("地方国企") || ((trimmed.contains("国企") || trimmed.contains("国有")) && !trimmed.contains("央"))) {
            return CATEGORY_LOCAL_SOE;
        }
        if (trimmed.contains("民企") || trimmed.contains("民营") || trimmed.contains("私营") || trimmed.contains("企业客户")) {
            return CATEGORY_PRIVATE;
        }
        if (trimmed.contains("港澳台") || trimmed.contains("港") || trimmed.contains("澳") || trimmed.contains("台")
                || trimmed.contains("外企") || trimmed.contains("外资") || trimmed.contains("合资")) {
            return CATEGORY_FOREIGN;
        }
        return null;
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
