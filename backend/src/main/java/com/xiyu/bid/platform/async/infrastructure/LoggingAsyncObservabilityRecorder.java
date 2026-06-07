package com.xiyu.bid.platform.async.infrastructure;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class LoggingAsyncObservabilityRecorder implements AsyncObservabilityRecorder {
    private static final String PREFIX = "xiyu_async";
    private static final Set<String> ALERT_REASON_CODES = Set.of(
            "BUG",
            "DATA_CORRUPTION",
            "TRANSIENT_DEPENDENCY_EXHAUSTED",
            "CONTRACT_INVALID",
            "PERSISTENT_DEPENDENCY"
    );

    private final MeterRegistry meterRegistry;

    public LoggingAsyncObservabilityRecorder(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void recordSuccess(String asyncType, String eventType, String businessKey) {
        counter("success", asyncType, eventType, "NONE").increment();
    }

    @Override
    public void recordRetry(String asyncType, String eventType, String businessKey, int attempt, com.xiyu.bid.platform.async.domain.AsyncHandlingDecision decision) {
        counter("retry", asyncType, eventType, normalizeReasonCode(decision.reasonCode())).increment();
    }

    @Override
    public void recordDeadLetter(String asyncType, String eventType, String businessKey, String reasonCode) {
        counter("dead_letter", asyncType, eventType, normalizeReasonCode(reasonCode)).increment();
    }

    @Override
    public void recordDrop(String asyncType, String eventType, String businessKey, String reasonCode, boolean alertRequired) {
        String normalizedReasonCode = normalizeReasonCode(reasonCode);
        boolean shouldAlert = alertRequired || ALERT_REASON_CODES.contains(normalizedReasonCode);
        counter(shouldAlert ? "drop_alert" : "drop", asyncType, eventType, normalizedReasonCode).increment();
    }

    private String normalizeReasonCode(String reasonCode) {
        return reasonCode == null || reasonCode.isBlank() ? "NONE" : reasonCode;
    }

    private Counter counter(String result, String asyncType, String eventType, String reasonCode) {
        return Counter.builder(PREFIX + "_total")
                .tag("result", result)
                .tag("async_type", asyncType)
                .tag("event_type", eventType)
                .tag("reason_code", reasonCode)
                .register(meterRegistry);
    }
}
