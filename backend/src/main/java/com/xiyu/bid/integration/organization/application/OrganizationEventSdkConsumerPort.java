package com.xiyu.bid.integration.organization.application;

import com.xiyu.bid.integration.organization.dto.OrganizationEventWebhookResponse;

public interface OrganizationEventSdkConsumerPort {
    OrganizationEventWebhookResponse acceptEvent(String eventMessage);
}
