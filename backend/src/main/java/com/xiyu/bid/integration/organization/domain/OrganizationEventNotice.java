package com.xiyu.bid.integration.organization.domain;

public record OrganizationEventNotice(
        String traceId,
        String spanId,
        String parentId,
        String eventSource,
        OrganizationEventType topic,
        String time,
        String key,
        String subjectId
) {
}
