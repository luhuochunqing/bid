package com.xiyu.bid.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsDrillDownRowDTO {
    private Long id;
    private Long relatedId;
    private String title;
    private String subtitle;
    private String status;
    private String ownerName;
    private BigDecimal amount;
    private Integer score;
    private LocalDateTime createdAt;
    private LocalDateTime deadline;
    private String outcome;
    private String role;
    private Long count;
    private Long wonCount;
    private Long activeProjectCount;
    private Long managedProjectCount;
    private Long totalTaskCount;
    private Long completedTaskCount;
    private Long overdueTaskCount;
    private Double taskCompletionRate;
    private Double rate;
    private Integer teamSize;
}
