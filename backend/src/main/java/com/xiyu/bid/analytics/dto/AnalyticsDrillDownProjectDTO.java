package com.xiyu.bid.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsDrillDownProjectDTO {
    private Long id;
    private String name;
    private String customer;
    private BigDecimal budget;
    private String status;
    private String manager;
    private String result;
}
