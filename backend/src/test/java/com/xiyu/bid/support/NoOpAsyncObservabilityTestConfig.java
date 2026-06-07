package com.xiyu.bid.support;

import com.xiyu.bid.platform.async.domain.AsyncHandlingDecision;
import com.xiyu.bid.platform.async.infrastructure.AsyncObservabilityRecorder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class NoOpAsyncObservabilityTestConfig {
    @Bean
    @Primary
    public AsyncObservabilityRecorder asyncObservabilityRecorder() {
        return new AsyncObservabilityRecorder() {
            @Override
            public void recordSuccess(String asyncType, String eventType, String businessKey) {
            }

            @Override
            public void recordRetry(String asyncType, String eventType, String businessKey, int attempt, AsyncHandlingDecision decision) {
            }

            @Override
            public void recordDeadLetter(String asyncType, String eventType, String businessKey, String reasonCode) {
            }

            @Override
            public void recordDrop(String asyncType, String eventType, String businessKey, String reasonCode, boolean alertRequired) {
            }
        };
    }
}
