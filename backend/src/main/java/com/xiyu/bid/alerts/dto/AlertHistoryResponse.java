package com.xiyu.bid.alerts.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertHistoryResponse {
    private Long id;
    private Long ruleId;
    private String ruleName;
    private String alertType;
    private String severity;
    private String message;
    private String projectName;
    private String status;
    private String relatedId;
    private LocalDateTime createdAt;
    private LocalDateTime acknowledgedAt;
    private LocalDateTime resolvedAt;
}
