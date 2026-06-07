package com.xiyu.bid.integration.organization.domain;

import com.xiyu.bid.platform.async.domain.AsyncAction;
import com.xiyu.bid.platform.async.domain.AsyncHandlingDecision;

import java.time.LocalDateTime;

public record OrganizationRetryDecision(
        boolean retryable,
        OrganizationEventStatus status,
        LocalDateTime nextRetryAt,
        String reasonCode,
        boolean alertRequired
) {
    public static OrganizationRetryDecision retryAt(LocalDateTime nextRetryAt, String reasonCode, boolean alertRequired) {
        return new OrganizationRetryDecision(true, OrganizationEventStatus.PENDING_RETRY, nextRetryAt, reasonCode, alertRequired);
    }

    public static OrganizationRetryDecision deadLetter(String reasonCode, boolean alertRequired) {
        return new OrganizationRetryDecision(false, OrganizationEventStatus.DEAD_LETTER, null, reasonCode, alertRequired);
    }

    public static OrganizationRetryDecision fromAsyncDecision(AsyncHandlingDecision decision, LocalDateTime now) {
        if (decision.action() == AsyncAction.RETRY) {
            return retryAt(now.plusSeconds(decision.nextRetryDelaySeconds()), decision.reasonCode(), decision.alertRequired());
        }
        return deadLetter(decision.reasonCode(), decision.alertRequired());
    }
}
