package com.xiyu.bid.integration.organization.domain;

import com.xiyu.bid.platform.async.domain.AsyncDecisionResolver;
import com.xiyu.bid.platform.async.domain.AsyncFailureKind;
import com.xiyu.bid.platform.async.domain.ExponentialBackoffRetrySchedule;

import java.time.LocalDateTime;

public class OrganizationDirectoryRetryPolicy {
    private static final int BASE_DELAY_SECONDS = 5 * 60;
    private static final int MAX_DELAY_SECONDS = 60 * 60;
    private static final int MAX_EXPONENT = 4;

    private final AsyncDecisionResolver decisionResolver;
    private final ExponentialBackoffRetrySchedule retrySchedule;

    public OrganizationDirectoryRetryPolicy(AsyncDecisionResolver decisionResolver) {
        this.decisionResolver = decisionResolver;
        this.retrySchedule = new ExponentialBackoffRetrySchedule(BASE_DELAY_SECONDS, MAX_DELAY_SECONDS, MAX_EXPONENT);
    }

    public OrganizationRetryDecision decide(int retryCount, int maxAttempts, LocalDateTime now) {
        return OrganizationRetryDecision.fromAsyncDecision(
                decisionResolver.resolve(
                        AsyncFailureKind.TRANSIENT_DEPENDENCY,
                        retryCount,
                        maxAttempts,
                        retrySchedule,
                        true
                ),
                now
        );
    }
}
