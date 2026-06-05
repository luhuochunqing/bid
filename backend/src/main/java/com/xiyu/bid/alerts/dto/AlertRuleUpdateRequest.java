package com.xiyu.bid.alerts.dto;

import com.xiyu.bid.alerts.entity.AlertRule;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AlertRuleUpdateRequest {

    private String name;

    private AlertRule.AlertType type;

    private AlertRule.ConditionType condition;

    @Positive(message = "Threshold must be positive")
    private BigDecimal threshold;

    private Boolean enabled;
}
