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
public class AnalyticsDrillDownResponseDTO {
    private String metricKey;
    private String metricLabel;
    private AnalyticsDrillDownFiltersDTO filters;
    private AnalyticsPaginationDTO pagination;
    private AnalyticsDrillDownSummaryDTO summary;
    private List<AnalyticsDrillDownRowDTO> items;
}
