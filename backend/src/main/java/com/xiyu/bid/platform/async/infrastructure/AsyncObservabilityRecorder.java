package com.xiyu.bid.platform.async.infrastructure;

import com.xiyu.bid.platform.async.domain.AsyncHandlingDecision;

public interface AsyncObservabilityRecorder {
    void recordSuccess(String asyncType, String eventType, String businessKey);

    void recordRetry(String asyncType, String eventType, String businessKey, int attempt, AsyncHandlingDecision decision);

    void recordDeadLetter(String asyncType, String eventType, String businessKey, String reasonCode);

    void recordDrop(String asyncType, String eventType, String businessKey, String reasonCode, boolean alertRequired);
}
