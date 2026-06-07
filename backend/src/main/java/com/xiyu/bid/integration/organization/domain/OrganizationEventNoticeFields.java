package com.xiyu.bid.integration.organization.domain;

public record OrganizationEventNoticeFields(
        String traceId,
        String spanId,
        String parentId,
        String eventSource,
        String eventTopic,
        String time,
        String key,
        String deptId,
        String userId
) {
}
