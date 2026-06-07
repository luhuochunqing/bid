package com.xiyu.bid.workflowform.application.port;

import com.xiyu.bid.workflowform.domain.FormBusinessType;

import java.time.LocalDateTime;
import java.util.Map;

public record WorkflowFormTemplateVersionRecord(
        String templateCode,
        Integer version,
        String name,
        FormBusinessType businessType,
        boolean enabled,
        String publishedBy,
        LocalDateTime publishedAt,
        Map<String, Object> schema
) {
}
