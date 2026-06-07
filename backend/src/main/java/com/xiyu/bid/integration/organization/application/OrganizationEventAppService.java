package com.xiyu.bid.integration.organization.application;

import com.xiyu.bid.integration.organization.dto.OrganizationEventWebhookRequest;
import com.xiyu.bid.integration.organization.dto.OrganizationEventWebhookResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrganizationEventAppService {

    private final OrganizationDirectorySyncAppService directorySyncAppService;

    /**
     * Receives an event via the SDK (@AcceptEvent) path.
     * The SDK adapter passes the raw JSON string directly; no deserialization into
     * {@link OrganizationEventWebhookRequest} is required here.
     *
     * @param topic        event topic, e.g. {@code "BaseOssDept"} or {@code "BaseOssUser"}
     * @param eventMessage raw JSON event payload from the EHSY SDK
     * @return internal webhook response
     */
    public OrganizationEventWebhookResponse receiveViaSdk(String topic, String eventMessage) {
        return directorySyncAppService.receiveWebhook(
                new OrganizationEventWebhookRequest(topic, eventMessage)
        );
    }

    public OrganizationEventWebhookResponse receiveWebhook(
            OrganizationEventWebhookRequest request,
            String traceId,
            String sourceApp
    ) {
        return directorySyncAppService.receiveWebhook(request);
    }
}
