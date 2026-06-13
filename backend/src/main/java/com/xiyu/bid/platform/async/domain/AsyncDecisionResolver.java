package com.xiyu.bid.platform.async.domain;

import com.xiyu.bid.platform.async.domain.AsyncFailureKind;
import com.xiyu.bid.platform.async.domain.AsyncHandlingDecision;
import com.xiyu.bid.platform.async.domain.AsyncRetrySchedule;

import java.util.Objects;

public class AsyncDecisionResolver {
    public AsyncHandlingDecision resolve(
            AsyncFailureKind failureKind,
            int currentAttempt,
            int maxAttempts,
            AsyncRetrySchedule retrySchedule,
            boolean deadLetterSupported
    ) {
        Objects.requireNonNull(failureKind, "failureKind");
        return switch (failureKind) {
            case BUSINESS_REJECT -> AsyncHandlingDecision.drop("BUSINESS_REJECT", false);
            case IDEMPOTENT_DUPLICATE -> AsyncHandlingDecision.succeedWithLog("IDEMPOTENT_DUPLICATE");
            case SIDE_EFFECT_OPTIONAL -> AsyncHandlingDecision.drop("SIDE_EFFECT_OPTIONAL", false);
            case CONTRACT_INVALID -> deadLetterOrDrop(deadLetterSupported, "CONTRACT_INVALID", true);
            case DATA_CORRUPTION -> deadLetterOrDrop(deadLetterSupported, "DATA_CORRUPTION", true);
            case BUG -> deadLetterOrDrop(deadLetterSupported, "BUG", true);
            case MAIN_TRANSACTION_REQUIRED -> AsyncHandlingDecision.failMainTransaction("MAIN_TRANSACTION_REQUIRED");
            case PERSISTENT_DEPENDENCY -> deadLetterOrDrop(deadLetterSupported, "PERSISTENT_DEPENDENCY", true);
            case TRANSIENT_DEPENDENCY -> resolveTransientDecision(currentAttempt, maxAttempts, retrySchedule, deadLetterSupported);
        };
    }

    private AsyncHandlingDecision resolveTransientDecision(
            int currentAttempt,
            int maxAttempts,
            AsyncRetrySchedule retrySchedule,
            boolean deadLetterSupported
    ) {
        if (currentAttempt >= maxAttempts) {
            return deadLetterOrDrop(deadLetterSupported, "TRANSIENT_DEPENDENCY_EXHAUSTED", true);
        }
        int nextDelaySeconds = retrySchedule.nextDelaySeconds(currentAttempt);
        return AsyncHandlingDecision.retry("TRANSIENT_DEPENDENCY", nextDelaySeconds, false);
    }

    private AsyncHandlingDecision deadLetterOrDrop(boolean deadLetterSupported, String reasonCode, boolean alertRequired) {
        return deadLetterSupported
                ? AsyncHandlingDecision.deadLetter(reasonCode, alertRequired)
                : AsyncHandlingDecision.drop(reasonCode, alertRequired);
    }
}
