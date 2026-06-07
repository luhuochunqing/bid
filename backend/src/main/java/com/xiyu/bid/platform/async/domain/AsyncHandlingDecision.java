package com.xiyu.bid.platform.async.domain;

public record AsyncHandlingDecision(
        AsyncAction action,
        boolean alertRequired,
        boolean rollbackRequired,
        String reasonCode,
        int nextRetryDelaySeconds
) {
    public static AsyncHandlingDecision drop(String reasonCode, boolean alertRequired) {
        return new AsyncHandlingDecision(AsyncAction.DROP, alertRequired, false, reasonCode, 0);
    }

    public static AsyncHandlingDecision succeedWithLog(String reasonCode) {
        return new AsyncHandlingDecision(AsyncAction.SUCCEED_WITH_LOG, false, false, reasonCode, 0);
    }

    public static AsyncHandlingDecision retry(String reasonCode, int nextRetryDelaySeconds, boolean alertRequired) {
        return new AsyncHandlingDecision(AsyncAction.RETRY, alertRequired, false, reasonCode, nextRetryDelaySeconds);
    }

    public static AsyncHandlingDecision deadLetter(String reasonCode, boolean alertRequired) {
        return new AsyncHandlingDecision(AsyncAction.DEAD_LETTER, alertRequired, false, reasonCode, 0);
    }

    public static AsyncHandlingDecision failMainTransaction(String reasonCode) {
        return new AsyncHandlingDecision(AsyncAction.FAIL_MAIN_TRANSACTION, true, true, reasonCode, 0);
    }
}
