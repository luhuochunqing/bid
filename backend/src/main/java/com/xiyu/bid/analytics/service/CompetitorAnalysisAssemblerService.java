package com.xiyu.bid.analytics.service;

import com.xiyu.bid.analytics.dto.CompetitorAnalysisRequest;
import com.xiyu.bid.analytics.dto.CompetitorAnalysisResponse;
import com.xiyu.bid.analytics.dto.CompetitorAnalysisSeriesDTO;
import com.xiyu.bid.analytics.model.CompetitorAnalysisRow;
import com.xiyu.bid.analytics.service.CompetitorAnalysisComputationService.DiscountStats;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CompetitorAnalysisAssemblerService {

    private final CompetitorAnalysisQueryService queryService;
    private final CompetitorAnalysisComputationService computationService;

    public CompetitorAnalysisResponse analyze(CompetitorAnalysisRequest request) {
        List<CompetitorAnalysisRow> rows = queryService.fetchCompetitorRows(
                request.getCompetitorNames(),
                request.getStartDate(),
                request.getEndDate()
        );

        boolean isGrouped = request.getTenderEntity() != null && !request.getTenderEntity().isBlank();

        if (isGrouped) {
            return buildGroupedResponse(rows, request.getTenderEntity());
        }
        return buildDefaultResponse(rows);
    }

    public List<String> getTenderEntities() {
        return queryService.fetchDistinctTenderEntities();
    }

    private CompetitorAnalysisResponse buildDefaultResponse(List<CompetitorAnalysisRow> rows) {
        Map<String, DiscountStats> statsMap = computationService.computeDiscountByCompetitor(rows);

        List<String> categories = new ArrayList<>(statsMap.keySet());
        List<Integer> minData = new ArrayList<>();
        List<Integer> avgData = new ArrayList<>();
        List<Integer> maxData = new ArrayList<>();

        for (DiscountStats stats : statsMap.values()) {
            minData.add(stats.min());
            avgData.add((int) Math.round(stats.average()));
            maxData.add(stats.max());
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

    private CompetitorAnalysisResponse buildGroupedResponse(List<CompetitorAnalysisRow> rows, String tenderEntity) {
        // 按招标主体过滤
        List<CompetitorAnalysisRow> filteredRows = rows.stream()
                .filter(row -> row.tenderEntity() != null && row.tenderEntity().contains(tenderEntity))
                .toList();

        // 收集所有竞品公司名
        Set<String> competitorNames = new LinkedHashSet<>();
        for (CompetitorAnalysisRow row : filteredRows) {
            if (row.competitorName() != null && !row.competitorName().isBlank()) {
                competitorNames.add(row.competitorName());
            }
        }

        // 按招标主体分组
        Map<String, Map<String, DiscountStats>> grouped =
                computationService.computeDiscountByTenderEntity(filteredRows);

        // 收集所有招标主体名（X轴类别）
        List<String> categories = new ArrayList<>(grouped.keySet());
        if (categories.isEmpty()) {
            return CompetitorAnalysisResponse.builder()
                    .mode("grouped")
                    .categories(List.of())
                    .series(List.of())
                    .build();
        }

        // 计算整体平均折扣
        double overallAverage = computationService.computeOverallAverageDiscount(
                grouped.values().stream()
                        .flatMap(m -> m.entrySet().stream())
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue,
                                (a, b) -> a
                        ))
        );

        // 构建系列：每个竞品公司一个折线
        List<CompetitorAnalysisSeriesDTO> series = new ArrayList<>();
        for (String competitor : competitorNames) {
            List<Integer> data = new ArrayList<>();
            for (String entity : categories) {
                Map<String, DiscountStats> entityStats = grouped.get(entity);
                if (entityStats != null && entityStats.containsKey(competitor)) {
                    data.add((int) Math.round(entityStats.get(competitor).average()));
                } else {
                    data.add(0);
                }
            }
            series.add(CompetitorAnalysisSeriesDTO.builder()
                    .name(competitor)
                    .type("bar")
                    .data(data)
                    .build());
        }

        // 整体平均折扣线（每个招标主体一个值）
        List<Double> overallAverageLine = new ArrayList<>();
        for (String entity : categories) {
            Map<String, DiscountStats> entityStats = grouped.get(entity);
            if (entityStats != null && !entityStats.isEmpty()) {
                double avg = entityStats.values().stream()
                        .mapToDouble(s -> s.average())
                        .average()
                        .orElse(0.0);
                overallAverageLine.add(Math.round(avg * 10.0) / 10.0);
            } else {
                overallAverageLine.add(0.0);
            }
        }

        return CompetitorAnalysisResponse.builder()
                .mode("grouped")
                .categories(categories)
                .series(series)
                .overallAverageDiscount(overallAverage)
                .overallAverageLine(overallAverageLine)
                .build();
    }
}