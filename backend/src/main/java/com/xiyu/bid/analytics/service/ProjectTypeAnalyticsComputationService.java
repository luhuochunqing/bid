package com.xiyu.bid.analytics.service;

import com.xiyu.bid.analytics.model.ProjectTypeAggregate;
import com.xiyu.bid.analytics.model.ProjectTypeProjectRow;
import com.xiyu.bid.entity.Tender;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
class ProjectTypeAnalyticsComputationService {

    static final String UNCATEGORIZED_PROJECT_TYPE = "未分类";

    List<ProjectTypeAggregate> summarize(List<ProjectTypeProjectRow> rows) {
        long totalProjects = rows.size();
        Map<String, MutableProjectTypeAggregate> aggregates = new LinkedHashMap<>();
        for (ProjectTypeProjectRow row : rows) {
            String pType = normalizeProjectType(row.projectType());
            MutableProjectTypeAggregate aggregate = aggregates.computeIfAbsent(
                    pType,
                    MutableProjectTypeAggregate::new
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
                    return left.projectType().compareTo(right.projectType());
                })
                .toList();
    }

    String normalizeProjectType(String projectType) {
        if (projectType == null || projectType.isBlank()) {
            return UNCATEGORIZED_PROJECT_TYPE;
        }
        return projectType.trim();
    }

    boolean isWon(ProjectTypeProjectRow row) {
        return row.tenderStatus() == Tender.Status.WON;
    }

    private BigDecimal defaultAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private static final class MutableProjectTypeAggregate {
        private final String projectType;
        private long projectCount;
        private long activeProjectCount;
        private long wonCount;
        private BigDecimal totalAmount = BigDecimal.ZERO;

        private MutableProjectTypeAggregate(String pProjectType) {
            this.projectType = pProjectType;
        }

        private ProjectTypeAggregate toImmutable(long totalProjects) {
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
            return new ProjectTypeAggregate(
                    projectType,
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