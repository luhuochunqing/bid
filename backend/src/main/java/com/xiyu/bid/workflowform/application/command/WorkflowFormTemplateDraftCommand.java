package com.xiyu.bid.workflowform.application.command;

import com.xiyu.bid.workflowform.domain.FormBusinessType;

import java.util.Map;

public record WorkflowFormTemplateDraftCommand(
        String templateCode,
        String name,
        FormBusinessType businessType,
        boolean enabled,
        Map<String, Object> schema
) {
}
