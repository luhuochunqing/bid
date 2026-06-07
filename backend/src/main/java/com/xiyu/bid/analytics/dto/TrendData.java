package com.xiyu.bid.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Trend data for analytics visualization
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrendData implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Time period (e.g., "2024-01", "Q1 2024")
     */
    private String period;

    /**
     * Count of items in this period
     */
    private Long count;

    /**
     * Total value for this period
     */
    private BigDecimal value;

    /**
     * Percentage change from previous period
     */
    private Double changePercentage;
}
