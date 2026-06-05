package com.xiyu.bid.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Regional distribution data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegionalData implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Region name or code
     */
    private String region;

    /**
     * Number of tenders in this region
     */
    private Long tenderCount;

    /**
     * Total budget for this region
     */
    private BigDecimal totalBudget;

    /**
     * Percentage of total tenders
     */
    private Double percentage;
}
