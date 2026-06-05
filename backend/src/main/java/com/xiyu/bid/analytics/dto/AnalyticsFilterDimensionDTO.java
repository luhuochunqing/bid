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
public class AnalyticsFilterDimensionDTO {
    private String key;
    private String label;
    private String selectedValue;
    private List<AnalyticsFilterOptionDTO> options;
}
