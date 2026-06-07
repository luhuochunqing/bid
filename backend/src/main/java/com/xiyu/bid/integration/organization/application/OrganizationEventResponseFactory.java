package com.xiyu.bid.integration.organization.application;

import com.xiyu.bid.integration.organization.domain.OrganizationEventStatus;
import com.xiyu.bid.integration.organization.dto.OrganizationEventWebhookData;
import com.xiyu.bid.integration.organization.dto.OrganizationEventWebhookResponse;

final class OrganizationEventResponseFactory {
    private OrganizationEventResponseFactory() {
    }

    static OrganizationEventWebhookResponse response(
            String code,
            String msg,
            String eventKey,
            boolean accepted,
            boolean duplicate,
            OrganizationEventStatus status
    ) {
        return new OrganizationEventWebhookResponse(
                code,
                msg,
                System.currentTimeMillis(),
                new OrganizationEventWebhookData(eventKey, accepted, duplicate, status.name())
        );
    }
}
