package com.xiyu.bid.alerts.dto;

import com.xiyu.bid.alerts.entity.AlertRule;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AlertRuleCreateRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotNull(message = "Type is required")
    private AlertRule.AlertType type;

    @NotNull(message = "Condition is required")
    private AlertRule.ConditionType condition;

    @NotNull(message = "Threshold is required")
    @Positive(message = "Threshold must be positive")
    private BigDecimal threshold;

    private Boolean enabled = true;

    @NotBlank(message = "Created by is required")
    private String createdBy;
}
