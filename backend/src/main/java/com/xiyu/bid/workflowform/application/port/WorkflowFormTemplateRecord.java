package com.xiyu.bid.workflowform.application.port;

import com.xiyu.bid.workflowform.domain.FormBusinessType;

import java.util.Map;

public record WorkflowFormTemplateRecord(
        String templateCode,
        FormBusinessType businessType,
        Integer version,
        Map<String, Object> schema
) {
}
