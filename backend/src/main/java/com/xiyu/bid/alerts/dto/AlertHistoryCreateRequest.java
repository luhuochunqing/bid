package com.xiyu.bid.alerts.dto;

import com.xiyu.bid.alerts.entity.AlertHistory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AlertHistoryCreateRequest {

    @NotNull(message = "Rule ID is required")
    private Long ruleId;

    @NotNull(message = "Level is required")
    private AlertHistory.AlertLevel level;

    @NotBlank(message = "Message is required")
    private String message;

    private String relatedId;
}
