package com.xiyu.bid.integration.organization.dto;

public record OrganizationEventWebhookRequest(
        String eventTopic,
        String eventMessage
) {
}
