package com.xiyu.bid.integration.organization.domain;

import java.time.LocalDateTime;

public final class OrganizationDirectoryRetryPolicy {
    private static final int BASE_DELAY_MINUTES = 5;
    private static final int MAX_DELAY_MINUTES = 60;
    private static final int MAX_EXPONENT = 4;

    private OrganizationDirectoryRetryPolicy() {
    }

    public static OrganizationRetryDecision decide(int retryCount, int maxAttempts, LocalDateTime now) {
        if (retryCount >= maxAttempts) {
            return OrganizationRetryDecision.deadLetter();
        }
        int multiplier = 1 << Math.min(Math.max(retryCount, 0), MAX_EXPONENT);
        int delayMinutes = Math.min(BASE_DELAY_MINUTES * multiplier, MAX_DELAY_MINUTES);
        return OrganizationRetryDecision.retryAt(now.plusMinutes(delayMinutes));
    }
}
