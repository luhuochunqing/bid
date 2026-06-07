package com.xiyu.bid.workflowform.dto;

import com.xiyu.bid.workflowform.domain.FormBusinessType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record WorkflowFormSubmitRequest(
        @NotBlank String templateCode,
        @NotNull FormBusinessType businessType,
        Long projectId,
        @NotBlank String applicantName,
        @NotNull Map<String, Object> formData
) {
}
