package com.xiyu.bid.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompetitorAnalysisResponse {
    private String mode;
    private List<String> categories;
    // 默认模式
    private List<CompetitorAnalysisSeriesDTO> series;
    // 分组模式
    private List<CompetitorGroupDTO> groups;
    private List<Double> overallAvgLine;
    // 项目模式
    private String projectLabel;
    private List<Double> discounts;
    private List<CompetitorTableRowDTO> tableRows;
}
