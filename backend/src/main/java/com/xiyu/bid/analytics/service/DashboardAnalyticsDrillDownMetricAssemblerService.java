package com.xiyu.bid.analytics.service;

import com.xiyu.bid.analytics.dto.AnalyticsDrillDownFiltersDTO;
import com.xiyu.bid.analytics.dto.AnalyticsDrillDownResponse;
import com.xiyu.bid.analytics.dto.AnalyticsDrillDownResponseDTO;
import com.xiyu.bid.analytics.dto.AnalyticsDrillDownRowDTO;
import com.xiyu.bid.analytics.dto.AnalyticsDrillDownStatsDTO;
import com.xiyu.bid.analytics.dto.AnalyticsDrillDownSummaryDTO;
import com.xiyu.bid.analytics.dto.AnalyticsDrillDownProjectDTO;
import com.xiyu.bid.analytics.dto.AnalyticsDrillDownTeamDTO;
import com.xiyu.bid.analytics.dto.AnalyticsDrillDownFileDTO;
import com.xiyu.bid.analytics.dto.AnalyticsFilterDimensionDTO;
import com.xiyu.bid.analytics.dto.AnalyticsFilterOptionDTO;
import com.xiyu.bid.analytics.dto.AnalyticsPaginationDTO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
class DashboardAnalyticsDrillDownMetricAssemblerService {

    AnalyticsDrillDownResponse assembleBasicDrillDownResponse(
            List<AnalyticsDrillDownProjectDTO> projects,
            List<AnalyticsDrillDownTeamDTO> teams,
            List<AnalyticsDrillDownFileDTO> files,
            long totalParticipation,
            long wonCount,
            double teamWinRate,
            BigDecimal totalAmount
    ) {
        return AnalyticsDrillDownResponse.builder()
                .projects(projects)
                .team(teams)
                .files(files)
                .stats(AnalyticsDrillDownStatsDTO.builder()
                        .totalParticipation(totalParticipation)
                        .wonCount(wonCount)
                        .teamWinRate(teamWinRate)
                        .totalAmount(totalAmount)
                        .build())
                .build();
    }

    AnalyticsDrillDownResponseDTO buildMetricDrillDownResponse(
            String metricKey,
            String metricLabel,
            LocalDate startDate,
            LocalDate endDate,
            List<AnalyticsFilterDimensionDTO> dimensions,
            List<AnalyticsDrillDownRowDTO> filteredRows,
            Integer requestedPage,
            Integer requestedSize,
            AnalyticsDrillDownSummaryDTO summary
    ) {
        int page = normalizePage(requestedPage);
        int size = normalizeSize(requestedSize);
        int fromIndex = Math.min((page - 1) * size, filteredRows.size());
        int toIndex = Math.min(fromIndex + size, filteredRows.size());
        List<AnalyticsDrillDownRowDTO> pageItems = filteredRows.subList(fromIndex, toIndex);
        int totalPages = filteredRows.isEmpty() ? 0 : (int) Math.ceil((double) filteredRows.size() / size);
        return AnalyticsDrillDownResponseDTO.builder()
                .metricKey(metricKey)
                .metricLabel(metricLabel)
                .filters(AnalyticsDrillDownFiltersDTO.builder()
                        .startDate(startDate)
                        .endDate(endDate)
                        .dimensions(dimensions)
                        .build())
                .pagination(AnalyticsPaginationDTO.builder()
                        .page(page)
                        .size(size)
                        .total((long) filteredRows.size())
                        .totalPages(totalPages)
                        .hasNext(page < totalPages)
                        .build())
                .summary(summary)
                .items(pageItems)
                .build();
    }

    AnalyticsFilterDimensionDTO buildDimension(
            String key,
            String label,
            String selectedValue,
            List<AnalyticsDrillDownRowDTO> rows,
            Function<AnalyticsDrillDownRowDTO, String> extractor,
            Function<String, String> labelTranslator
    ) {
        Map<String, Long> counts = rows.stream()
                .map(extractor)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));

        List<AnalyticsFilterOptionDTO> options = new ArrayList<>();
        options.add(AnalyticsFilterOptionDTO.builder()
                .label("全部")
                .value("ALL")
                .count((long) rows.size())
                .build());
        counts.forEach((value, count) -> options.add(AnalyticsFilterOptionDTO.builder()
                .label(labelTranslator.apply(value))
                .value(value)
                .count(count)
                .build()));
        return AnalyticsFilterDimensionDTO.builder()
                .key(key)
                .label(label)
                .selectedValue(normalizeFilterValue(selectedValue))
                .options(options)
                .build();
    }

    AnalyticsFilterDimensionDTO buildProjectStatusDimension(String selectedValue, List<AnalyticsDrillDownRowDTO> rows) {
        long inProgressCount = rows.stream().filter(row -> !"WON".equals(row.getStatus()) && !"LOST".equals(row.getStatus()) && !"FAILED".equals(row.getStatus()) && !"ABANDONED".equals(row.getStatus())).count();
        Map<String, Long> counts = rows.stream()
                .map(AnalyticsDrillDownRowDTO::getStatus)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));

        List<AnalyticsFilterOptionDTO> options = new ArrayList<>();
        options.add(AnalyticsFilterOptionDTO.builder().label("全部").value("ALL").count((long) rows.size()).build());
        options.add(AnalyticsFilterOptionDTO.builder().label("进行中").value("IN_PROGRESS").count(inProgressCount).build());
        counts.forEach((value, count) -> options.add(AnalyticsFilterOptionDTO.builder()
                .label(translateProjectStatus(value))
                .value(value)
                .count(count)
                .build()));
        return AnalyticsFilterDimensionDTO.builder()
                .key("status")
                .label("项目状态")
                .selectedValue(normalizeProjectStatusFilter(selectedValue))
                .options(options)
                .build();
    }

    String translateTenderStatus(String status) {
        return switch (normalizeFilterValue(status)) {
            case "PENDING_ASSIGNMENT" -> "待分配";
            case "TRACKING" -> "跟踪中";
            case "EVALUATED" -> "已评估";
            case "BIDDING" -> "投标中";
            case "WON" -> "已中标";
            case "LOST" -> "未中标";
            case "ABANDONED" -> "已放弃";
            default -> status;
        };
    }

    String translateProjectStatus(String status) {
        return switch (normalizeFilterValue(status)) {
            case "PENDING_INITIATION" -> "待立项";
            case "INITIATED" -> "已立项";
            case "BIDDING" -> "投标中";
            case "EVALUATING" -> "评标中";
            case "WON" -> "已中标";
            case "LOST" -> "未中标";
            case "FAILED" -> "已流标";
            case "ABANDONED" -> "已放弃";
            default -> status;
        };
    }

    String translateOutcome(String outcome) {
        return switch (normalizeFilterValue(outcome)) {
            case "WON" -> "已中标";
            case "LOST" -> "未中标";
            case "IN_PROGRESS" -> "进行中";
            default -> outcome;
        };
    }

    String translateUserRole(String role) {
        return switch (normalizeFilterValue(role)) {
            case "ADMIN" -> "管理员";
            case "MANAGER" -> "经理";
            case "STAFF" -> "员工";
            default -> role;
        };
    }

    boolean matchesFilter(String value, String selectedValue) {
        String normalized = normalizeFilterValue(selectedValue);
        return "ALL".equals(normalized) || Objects.equals(value, normalized);
    }

    boolean matchesProjectStatusFilter(String status, String filter) {
        String normalized = normalizeProjectStatusFilter(filter);
        if ("ALL".equals(normalized)) {
            return true;
        }
        if ("IN_PROGRESS".equals(normalized)) {
            return !"WON".equals(status) && !"LOST".equals(status) && !"FAILED".equals(status) && !"ABANDONED".equals(status);
        }
        return Objects.equals(status, normalized);
    }

    BigDecimal sumAmounts(List<AnalyticsDrillDownRowDTO> rows) {
        return rows.stream()
                .map(AnalyticsDrillDownRowDTO::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    int normalizeSize(Integer size) {
        return size == null || size < 1 ? 10 : Math.min(size, 100);
    }

    private String normalizeFilterValue(String value) {
        if (value == null || value.isBlank()) {
            return "ALL";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeProjectStatusFilter(String value) {
        String normalized = normalizeFilterValue(value);
        if ("IN_PROGRESS".equals(normalized) || "INPROGRESS".equals(normalized)) {
            return "IN_PROGRESS";
        }
        return normalized;
    }
}
