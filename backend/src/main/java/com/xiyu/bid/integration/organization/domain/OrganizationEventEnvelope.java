package com.xiyu.bid.integration.organization.domain;

public record OrganizationEventEnvelope(
        String topic,
        String sourceApp,
        String traceId,
        String message
) {
}
