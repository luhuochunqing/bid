package com.xiyu.bid.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Complete dashboard overview data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardOverviewDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Summary statistics
     */
    private SummaryStats summaryStats;

    /**
     * Tender trends over time
     */
    private List<TrendData> tenderTrends;

    /**
     * Project trends over time
     */
    private List<TrendData> projectTrends;

    /**
     * Status distribution (tenders by status)
     */
    private Map<String, Long> statusDistribution;

    /**
     * Top competitors
     */
    private List<CompetitorData> topCompetitors;

    /**
     * Regional distribution
     */
    private List<RegionalData> regionalDistribution;
}
