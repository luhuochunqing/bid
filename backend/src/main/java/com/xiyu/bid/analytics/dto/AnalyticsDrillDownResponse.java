package com.xiyu.bid.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsDrillDownResponse {
    private List<AnalyticsDrillDownProjectDTO> projects;
    private List<AnalyticsDrillDownTeamDTO> team;
    private List<AnalyticsDrillDownFileDTO> files;
    private AnalyticsDrillDownStatsDTO stats;
}
