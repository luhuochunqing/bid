package com.xiyu.bid.workflowform.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record WorkflowFormTrialSubmitRequest(
        @NotNull Map<String, Object> formData,
        String applicantName
) {
}
