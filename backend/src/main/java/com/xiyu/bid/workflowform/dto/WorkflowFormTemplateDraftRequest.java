package com.xiyu.bid.workflowform.dto;

import com.xiyu.bid.workflowform.domain.FormBusinessType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record WorkflowFormTemplateDraftRequest(
        @NotBlank String templateCode,
        @NotBlank String name,
        @NotNull FormBusinessType businessType,
        boolean enabled,
        @NotNull Map<String, Object> schema
) {
}
