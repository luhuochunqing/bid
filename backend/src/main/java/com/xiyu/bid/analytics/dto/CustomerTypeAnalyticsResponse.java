package com.xiyu.bid.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerTypeAnalyticsResponse {
    private Long totalProjectCount;
    private Long classifiedProjectCount;
    private Long uncategorizedProjectCount;
    private BigDecimal totalAmount;
    private List<CustomerTypeDimensionDTO> dimensions;
}
