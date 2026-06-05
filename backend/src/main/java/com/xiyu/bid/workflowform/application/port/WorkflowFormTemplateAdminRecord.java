package com.xiyu.bid.workflowform.application.port;

import com.xiyu.bid.workflowform.domain.FormBusinessType;

import java.util.Map;

public record WorkflowFormTemplateAdminRecord(
        String templateCode,
        String name,
        FormBusinessType businessType,
        Integer version,
        boolean enabled,
        String status,
        Map<String, Object> schema,
        OaProcessBindingRecord oaBinding
) {
}
