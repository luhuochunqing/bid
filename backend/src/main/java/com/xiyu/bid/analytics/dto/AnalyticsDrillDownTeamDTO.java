package com.xiyu.bid.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsDrillDownTeamDTO {
    private String name;
    private String role;
    private String dept;
    private long participation;
    private double winRate;
}
