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
import java.util.Set;

/**
 * M3 项目类型分析纯计算服务：分类聚合与占比计算。
 * <p>输入行已由上游 {@link ProjectTypeAnalyticsQueryService} 经
 * {@code ProjectAccessScopeService} 完成项目级数据权限过滤，
 * 本类为无副作用纯计算，不访问数据源也不做权限决策。
 */
@Component
class ProjectTypeAnalyticsComputationService {

    // 5 种标准项目类型分类（PRD §8.4，与前端 m3-project-type COLOR_MAP 一致）。
    // P1-4 口径：仅统计这 5 个分类，未分类（空值）与不在白名单内的值不计数。
    static final Set<String> ALLOWED_CATEGORIES = Set.of("工业品", "办公", "综合", "集采", "其他");

    List<ProjectTypeAggregate> summarize(List<ProjectTypeProjectRow> rows) {
        // 初始化全部 5 种标准分类（count=0 也保留，前端图例需展示）
        Map<String, MutableProjectTypeAggregate> aggregates = new LinkedHashMap<>();
        for (String category : ALLOWED_CATEGORIES) {
            aggregates.put(category, new MutableProjectTypeAggregate(category));
        }

        // 只统计精确匹配 5 种分类的项目，未分类/不匹配的跳过不计数
        for (ProjectTypeProjectRow row : rows) {
            String pType = normalizeProjectType(row.projectType());
            if (pType == null) {
                continue;
            }
            MutableProjectTypeAggregate aggregate = aggregates.get(pType);
            if (aggregate == null) {
                continue;
            }
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
                    return left.projectType().compareTo(right.projectType());
                })
                .toList();
    }

    /**
     * 只返回精确匹配 5 种标准分类的值，不匹配的返回 null（跳过不统计）。
     */
    String normalizeProjectType(String projectType) {
        if (projectType == null || projectType.isBlank()) {
            return null;
        }
        String trimmed = projectType.trim();
        if (ALLOWED_CATEGORIES.contains(trimmed)) {
            return trimmed;
        }
        return null;
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