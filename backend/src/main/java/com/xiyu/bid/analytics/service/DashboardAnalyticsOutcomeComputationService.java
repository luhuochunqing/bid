package com.xiyu.bid.analytics.service;

import com.xiyu.bid.analytics.model.ProjectSnapshotAggregate;
import com.xiyu.bid.analytics.repository.DashboardAnalyticsRepository;
import com.xiyu.bid.entity.Tender;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Component
class DashboardAnalyticsOutcomeComputationService {

    List<DashboardAnalyticsRepository.TenderSummaryRow> filterTenderSummaryRowsByTypeAndKey(
            List<DashboardAnalyticsRepository.TenderSummaryRow> rows,
            String type,
            String key
    ) {
        String normalizedType = type == null ? "" : type.toLowerCase(java.util.Locale.ROOT);
        return switch (normalizedType) {
            case "trend" -> rows.stream()
                    .filter(tender -> tender.createdAt() != null)
                    .filter(tender -> tender.createdAt().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM")).equals(key))
                    .toList();
            case "competitor", "region" -> rows.stream()
                    .filter(tender -> key != null && key.equals(tender.source()))
                    .toList();
            case "product" -> rows.stream()
                    .filter(tender -> classifyProductLine(tender.title()).equals(key))
                    .toList();
            default -> List.of();
        };
    }

    String deriveOutcome(Tender.Status status, ProjectSnapshotAggregate project) {
        if (status == Tender.Status.WON) {
            return "WON";
        }
        if (status == Tender.Status.ABANDONED) {
            return "LOST";
        }
        return "IN_PROGRESS";
    }

    List<DashboardAnalyticsRepository.TenderSummaryRow> filterTenderRowsByDateRange(
            List<DashboardAnalyticsRepository.TenderSummaryRow> rows,
            LocalDate startDate,
            LocalDate endDate
    ) {
        return rows.stream()
                .filter(tender -> isWithinDateRange(tender.createdAt(), startDate, endDate))
                .toList();
    }

    private boolean isWithinDateRange(LocalDateTime value, LocalDate startDate, LocalDate endDate) {
        if (value == null) {
            return false;
        }
        LocalDate date = value.toLocalDate();
        if (startDate != null && date.isBefore(startDate)) {
            return false;
        }
        return endDate == null || !date.isAfter(endDate);
    }

    private String classifyProductLine(String sourceText) {
        String text = sourceText == null ? "" : sourceText.toLowerCase(java.util.Locale.ROOT);
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
}
