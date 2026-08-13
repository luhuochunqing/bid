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
public class TrendAnalysisResponse {
    private List<String> categories;
    private List<Long> bidSeries;
    private List<Long> winSeries;
    private List<Double> winRateSeries;
}