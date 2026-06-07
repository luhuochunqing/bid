package com.xiyu.bid.analytics.service;

import com.xiyu.bid.analytics.dto.AnalyticsDrillDownRowDTO;
import com.xiyu.bid.analytics.dto.AnalyticsDrillDownSummaryDTO;
import com.xiyu.bid.analytics.model.ProjectSnapshotAggregate;
import com.xiyu.bid.analytics.repository.DashboardAnalyticsRepository;
import com.xiyu.bid.entity.User;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
class DashboardAnalyticsDrillDownContentSupport {

    BigDecimal defaultAmount(BigDecimal amount) {
        return amount != null ? amount : BigDecimal.ZERO;
    }

    String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    String fallbackUserName(Long userId) {
        return userId == null ? "未分配" : "用户#" + userId;
    }

    String resolveDisplayName(User user, String fallbackName, Long userId) {
        if (user != null && user.getFullName() != null && !user.getFullName().isBlank()) {
            return user.getFullName();
        }
        if (fallbackName != null && !fallbackName.isBlank()) {
            return fallbackName;
        }
        return fallbackUserName(userId);
    }

    String resolveProjectResult(com.xiyu.bid.entity.Project.Status status) {
        if (status == null) {
            return null;
        }
        if (status == com.xiyu.bid.entity.Project.Status.WON
                || status == com.xiyu.bid.entity.Project.Status.BIDDING
                || status == com.xiyu.bid.entity.Project.Status.EVALUATING) {
            return "won";
        }
        return null;
    }

    AnalyticsDrillDownSummaryDTO buildTeamSummary(
            List<AnalyticsDrillDownRowDTO> filteredRows,
            List<ProjectSnapshotAggregate> filteredProjects,
            Map<Long, DashboardAnalyticsRepository.TenderSummaryRow> tenderById
    ) {
        return AnalyticsDrillDownSummaryDTO.builder()
                .totalCount((long) filteredRows.size())
                .totalAmount(sumAmounts(filteredRows))
                .totalTeamMembers((long) filteredRows.size())
                .wonCount(teamWonProjectCount(filteredProjects, tenderById))
                .winRate(filteredRows.isEmpty() ? 0.0 : filteredRows.stream()
                        .map(AnalyticsDrillDownRowDTO::getRate)
                        .filter(Objects::nonNull)
                        .mapToDouble(Double::doubleValue)
                        .average()
                        .orElse(0.0))
                .totalCompletedTasks(filteredRows.stream()
                        .map(AnalyticsDrillDownRowDTO::getCompletedTaskCount)
                        .filter(Objects::nonNull)
                        .reduce(0L, Long::sum))
                .totalOverdueTasks(filteredRows.stream()
                        .map(AnalyticsDrillDownRowDTO::getOverdueTaskCount)
                        .filter(Objects::nonNull)
                        .reduce(0L, Long::sum))
                .averageTaskCompletionRate(filteredRows.isEmpty() ? 0.0 : filteredRows.stream()
                        .map(AnalyticsDrillDownRowDTO::getTaskCompletionRate)
                        .filter(Objects::nonNull)
                        .mapToDouble(Double::doubleValue)
                        .average()
                        .orElse(0.0))
                .build();
    }

    BigDecimal sumAmounts(List<AnalyticsDrillDownRowDTO> rows) {
        return rows.stream()
                .map(AnalyticsDrillDownRowDTO::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    long teamWonProjectCount(
            List<ProjectSnapshotAggregate> filteredProjects,
            Map<Long, DashboardAnalyticsRepository.TenderSummaryRow> tenderById
    ) {
        return filteredProjects.stream()
                .map(project -> tenderById.get(project.tenderId()))
                .filter(Objects::nonNull)
                .filter(tender -> tender.status() == com.xiyu.bid.entity.Tender.Status.WON)
                .count();
    }
}
