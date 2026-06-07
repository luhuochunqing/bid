package com.xiyu.bid.integration.organization.infrastructure.sdk;

import com.xiyu.bid.integration.organization.dto.OrganizationEventWebhookResponse;
import com.xiyu.bid.integration.organization.dto.OrganizationEventWebhookData;

/**
 * Maps SDK event response types to the internal {@link OrganizationEventWebhookResponse}.
 * <p>
 * The SDK types ({@code EventInfoRespDto}, {@code EventResult}) are from
 * {@code com.ehsy.eventlibrary:ClientSDK}. The concrete class names and package
 * paths must be verified when the SDK jar ({@code release_0.0.2}) is available.
 * <p>
 * Response classification:
 * <ul>
 *   <li>{@code code = 200} → status {@code PROCESSED} (no retry)</li>
 *   <li>{@code code = 5xx} → status {@code PENDING_RETRY} (retry)</li>
 *   <li>{@code code = 4xx} → status {@code REJECTED} (no retry)</li>
 *   <li>SDK exception → status {@code PENDING_RETRY}</li>
 * </ul>
 */
public class OrganizationEventSdkResponseMapper {

    /**
     * Maps a successful SDK response to an internal response.
     *
     * @param eventId SDK's event key / idempotency token
     * @param code    SDK response code
     * @param msg     SDK response message
     * @return internal webhook response
     */
    public OrganizationEventWebhookResponse toResponse(String eventId, int code, String msg) {
        if (code == 200) {
            return new OrganizationEventWebhookResponse(
                    "200",
                    msg != null ? msg : "ok",
                    System.currentTimeMillis(),
                    new OrganizationEventWebhookData(eventId, true, false, "PROCESSED")
            );
        }
        return new OrganizationEventWebhookResponse(
                String.valueOf(code),
                msg != null ? msg : "sdk error",
                System.currentTimeMillis(),
                new OrganizationEventWebhookData(eventId, false, false, code >= 500 ? "PENDING_RETRY" : "REJECTED")
        );
    }

    /**
     * Maps an SDK exception to a retryable internal response.
     *
     * @param eventId SDK's event key
     * @param ex      the exception thrown by the SDK
     * @return internal webhook response marked as retryable
     */
    public OrganizationEventWebhookResponse fromException(String eventId, Exception ex) {
        return new OrganizationEventWebhookResponse(
                "500",
                "sdk exception: " + (ex != null ? ex.getMessage() : "unknown"),
                System.currentTimeMillis(),
                new OrganizationEventWebhookData(eventId, false, false, "PENDING_RETRY")
        );
    }
}
