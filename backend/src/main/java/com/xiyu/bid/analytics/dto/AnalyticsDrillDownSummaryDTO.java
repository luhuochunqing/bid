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
public class AnalyticsDrillDownSummaryDTO {
    private Long totalCount;
    private BigDecimal totalAmount;
    private Long wonCount;
    private Double winRate;
    private Long activeCount;
    private Long totalTeamMembers;
    private Long totalCompletedTasks;
    private Long totalOverdueTasks;
    private Double averageTaskCompletionRate;
}
