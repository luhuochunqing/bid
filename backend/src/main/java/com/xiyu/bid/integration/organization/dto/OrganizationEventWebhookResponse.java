package com.xiyu.bid.integration.organization.dto;

public record OrganizationEventWebhookResponse(
        String code,
        String msg,
        long timestamp,
        OrganizationEventWebhookData data
) {
}
