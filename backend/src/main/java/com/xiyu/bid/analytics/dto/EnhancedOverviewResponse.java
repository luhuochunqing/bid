package com.xiyu.bid.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnhancedOverviewResponse {
    private Long totalCount;
    private Long biddingCount;
    private Long wonCount;
    private Double winRate;
}