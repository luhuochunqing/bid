package com.xiyu.bid.integration.organization.dto;

public record OrganizationEventWebhookData(
        String eventId,
        boolean accepted,
        boolean duplicate,
        String status
) {
}
