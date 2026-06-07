package com.xiyu.bid.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Summary statistics for the dashboard
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SummaryStats implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Total number of tenders
     */
    private Long totalTenders;

    /**
     * Number of active projects
     */
    private Long activeProjects;

    /**
     * Number of pending tasks
     */
    private Long pendingTasks;

    /**
     * Total budget across all tenders
     */
    private BigDecimal totalBudget;

    /**
     * Success rate (percentage)
     */
    private Double successRate;
}
