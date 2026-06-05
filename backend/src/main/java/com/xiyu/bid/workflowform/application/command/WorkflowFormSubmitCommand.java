package com.xiyu.bid.workflowform.application.command;

import com.xiyu.bid.workflowform.domain.FormBusinessType;

import java.util.Map;

public record WorkflowFormSubmitCommand(
        String templateCode,
        FormBusinessType businessType,
        Long projectId,
        String applicantName,
        Map<String, Object> formData
) {
}
