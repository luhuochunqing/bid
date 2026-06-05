package com.xiyu.bid.integration.organization.domain;

import java.time.LocalDateTime;

public record OrganizationRetryDecision(
        boolean retryable,
        OrganizationEventStatus status,
        LocalDateTime nextRetryAt
) {
    public static OrganizationRetryDecision retryAt(LocalDateTime nextRetryAt) {
        return new OrganizationRetryDecision(true, OrganizationEventStatus.PENDING_RETRY, nextRetryAt);
    }

    public static OrganizationRetryDecision deadLetter() {
        return new OrganizationRetryDecision(false, OrganizationEventStatus.DEAD_LETTER, null);
    }
}
