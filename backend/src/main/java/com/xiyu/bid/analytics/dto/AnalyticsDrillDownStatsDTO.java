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
public class AnalyticsDrillDownStatsDTO {
    private long totalParticipation;
    private long wonCount;
    private double teamWinRate;
    private BigDecimal totalAmount;
}
