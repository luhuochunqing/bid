package com.xiyu.bid.alerts.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertStatisticsResponse {

    private Long totalAlerts;
    private Long unresolvedAlerts;
    private Long highAlerts;
    private Long mediumAlerts;
    private Long lowAlerts;
    private Long criticalAlerts;
}
