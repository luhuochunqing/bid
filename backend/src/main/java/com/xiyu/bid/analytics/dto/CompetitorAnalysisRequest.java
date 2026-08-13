package com.xiyu.bid.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompetitorAnalysisRequest {
    private LocalDate startDate;
    private LocalDate endDate;
    private List<String> competitorNames;
    private String tenderEntity;
}