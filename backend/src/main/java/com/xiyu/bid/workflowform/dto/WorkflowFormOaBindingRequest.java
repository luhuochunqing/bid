package com.xiyu.bid.workflowform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record WorkflowFormOaBindingRequest(
        @NotBlank String provider,
        @NotBlank String workflowCode,
        @NotNull Map<String, Object> fieldMapping,
        boolean enabled
) {
}
