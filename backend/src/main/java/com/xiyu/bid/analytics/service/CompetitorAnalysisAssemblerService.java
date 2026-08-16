package com.xiyu.bid.analytics.service;

import com.xiyu.bid.analytics.dto.CompetitorAnalysisRequest;
import com.xiyu.bid.analytics.dto.CompetitorAnalysisResponse;
import com.xiyu.bid.analytics.dto.CompetitorAnalysisSeriesDTO;
import com.xiyu.bid.analytics.dto.CompetitorGroupDTO;
import com.xiyu.bid.analytics.dto.CompetitorTableRowDTO;
import com.xiyu.bid.analytics.model.CompetitorAnalysisRow;
import com.xiyu.bid.analytics.service.CompetitorAnalysisComputationService.DiscountStats;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * M4 竞品分析装配服务：编排查询与计算，输出响应 DTO。
 * <p>项目级数据权限由上游 {@link CompetitorAnalysisQueryService} 经
 * {@code ProjectAccessScopeService} 统一过滤，本类只消费已授权范围内的行，
 * 不独立做项目访问决策。
 */
@Service
@RequiredArgsConstructor
public class CompetitorAnalysisAssemblerService {

    private final CompetitorAnalysisQueryService queryService;
    private final CompetitorAnalysisComputationService computationService;

    public CompetitorAnalysisResponse analyze(CompetitorAnalysisRequest request) {
        List<String> competitors = request.getCompetitorNames();
        if (competitors == null || competitors.isEmpty()) {
            return CompetitorAnalysisResponse.builder().mode("default").categories(List.of()).series(List.of()).build();
        }

        // 项目名称模式（PRD §9.8.5）
        if (request.getProjectName() != null && !request.getProjectName().isBlank()) {
            return buildProjectResponse(request);
        }

        List<CompetitorAnalysisRow> rows = queryService.fetchCompetitorRows(
                competitors,
                request.getTenderEntities(),
                request.getStartDate(),
                request.getEndDate()
        );

        // 分组模式（PRD §9.8 — tenderEntities 非空）
        if (request.getTenderEntities() != null && !request.getTenderEntities().isEmpty()) {
            return buildGroupedResponse(rows, competitors, request.getTenderEntities());
        }

        // 默认模式（PRD §9.7）
        return buildDefaultResponse(rows, competitors);
    }

    public List<String> getTenderEntities() {
        return queryService.fetchDistinctTenderEntities();
    }

    public List<String> getProjectNames(String query) {
        return queryService.fetchProjectNames(query);
    }

    /**
     * 默认模式（PRD §9.7）— 竞品公司为 X 轴，min/avg/max 三段堆叠。
     */
    private CompetitorAnalysisResponse buildDefaultResponse(List<CompetitorAnalysisRow> rows, List<String> competitors) {
        Map<String, DiscountStats> statsMap = computationService.computeDiscountByCompetitor(rows);

        List<String> categories = new ArrayList<>();
        List<Double> minData = new ArrayList<>();
        List<Double> avgData = new ArrayList<>();
        List<Double> maxData = new ArrayList<>();

        for (String comp : competitors) {
            categories.add(comp);
            DiscountStats stats = statsMap.get(comp);
            if (stats != null) {
                minData.add(stats.min());
                avgData.add(stats.average());
                maxData.add(stats.max());
            } else {
                minData.add(0.0);
                avgData.add(0.0);
                maxData.add(0.0);
            }
        }

        List<CompetitorAnalysisSeriesDTO> series = List.of(
                CompetitorAnalysisSeriesDTO.builder().name("最低折扣").type("bar").data(minData).build(),
                CompetitorAnalysisSeriesDTO.builder().name("平均折扣").type("bar").data(avgData).build(),
                CompetitorAnalysisSeriesDTO.builder().name("最高折扣").type("bar").data(maxData).build()
        );

        return CompetitorAnalysisResponse.builder()
                .mode("default")
                .categories(categories)
                .series(series)
                .build();
    }

    /**
     * 分组模式（PRD §9.8）— 招标主体为 X 轴，每个竞品公司一组堆叠（min/avg/max）+ 整体平均折扣折线。
     */
    private CompetitorAnalysisResponse buildGroupedResponse(
            List<CompetitorAnalysisRow> rows,
            List<String> competitors,
            List<String> tenderEntities
    ) {
        // 按招标主体分组
        Map<String, Map<String, DiscountStats>> grouped =
                computationService.computeDiscountByTenderEntity(rows);

        // categories = 用户选中的招标主体（仅保留有数据的）
        List<String> categories = new ArrayList<>();
        for (String entity : tenderEntities) {
            if (grouped.containsKey(entity)) {
                categories.add(entity);
            }
        }

        if (categories.isEmpty()) {
            return CompetitorAnalysisResponse.builder()
                    .mode("grouped")
                    .categories(List.of())
                    .groups(List.of())
                    .overallAvgLine(List.of())
                    .build();
        }

        // 构建每个竞品公司的 group（minData/avgData/maxData）
        List<CompetitorGroupDTO> groups = new ArrayList<>();
        for (String comp : competitors) {
            List<Double> minData = new ArrayList<>();
            List<Double> avgData = new ArrayList<>();
            List<Double> maxData = new ArrayList<>();
            for (String entity : categories) {
                Map<String, DiscountStats> entityStats = grouped.get(entity);
                if (entityStats != null && entityStats.containsKey(comp)) {
                    DiscountStats stats = entityStats.get(comp);
                    minData.add(stats.min());
                    avgData.add(stats.average());
                    maxData.add(stats.max());
                } else {
                    minData.add(0.0);
                    avgData.add(0.0);
                    maxData.add(0.0);
                }
            }
            groups.add(CompetitorGroupDTO.builder()
                    .competitor(comp)
                    .minData(minData)
                    .avgData(avgData)
                    .maxData(maxData)
                    .build());
        }

        // 整体平均折扣折线（PRD §9.8.2 — 每个招标主体下所有竞品公司平均折扣的再平均）
        List<Double> overallAvgLine = new ArrayList<>();
        for (String entity : categories) {
            Map<String, DiscountStats> entityStats = grouped.get(entity);
            if (entityStats != null && !entityStats.isEmpty()) {
                double avg = entityStats.values().stream()
                        .mapToDouble(DiscountStats::average)
                        .average()
                        .orElse(0.0);
                overallAvgLine.add(computationService.round1(avg));
            } else {
                overallAvgLine.add(0.0);
            }
        }

        return CompetitorAnalysisResponse.builder()
                .mode("grouped")
                .categories(categories)
                .groups(groups)
                .overallAvgLine(overallAvgLine)
                .build();
    }

    /**
     * 项目名称模式（PRD §9.8.5 + §9.15）— 竞品公司为 X 轴，单条柱展示折扣 + 明细表格。
     */
    private CompetitorAnalysisResponse buildProjectResponse(CompetitorAnalysisRequest request) {
        List<CompetitorAnalysisRow> rows = queryService.fetchProjectCompetitorRows(
                request.getProjectName(),
                request.getCompetitorNames(),
                request.getStartDate(),
                request.getEndDate()
        );

        // 构建竞品唯一列表（同一项目中每个竞品公司只出现一条记录，PRD §9.15）
        Map<String, CompetitorAnalysisRow> uniqueByCompetitor = new LinkedHashMap<>();
        for (CompetitorAnalysisRow row : rows) {
            if (row.competitorName() != null && !row.competitorName().isBlank()) {
                uniqueByCompetitor.putIfAbsent(row.competitorName(), row);
            }
        }

        List<String> categories = new ArrayList<>(uniqueByCompetitor.keySet());
        List<Double> discounts = new ArrayList<>();
        List<CompetitorTableRowDTO> tableRows = new ArrayList<>();

        for (String comp : categories) {
            CompetitorAnalysisRow row = uniqueByCompetitor.get(comp);
            Double discountVal = computationService.parseDiscount(row.discount());
            discounts.add(discountVal != null ? discountVal : 0.0);

            // 是否中标：project_result_competitor 表无 is_won 字段，通过 resultType 判断项目是否中标
            boolean isWon = "WON".equalsIgnoreCase(row.resultType());
            tableRows.add(CompetitorTableRowDTO.builder()
                    .competitor(comp)
                    .discount(row.discount())
                    .paymentDays(row.paymentTerm())
                    .isWon(isWon)
                    .build());
        }

        return CompetitorAnalysisResponse.builder()
                .mode("project")
                .categories(categories)
                .projectLabel(request.getProjectName())
                .discounts(discounts)
                .tableRows(tableRows)
                .build();
    }
}
