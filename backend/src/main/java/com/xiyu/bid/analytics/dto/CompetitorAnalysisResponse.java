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
    private List<CompetitorAnalysisSeriesDTO> series;
    private Double overallAverageDiscount;
    private List<Double> overallAverageLine;
}