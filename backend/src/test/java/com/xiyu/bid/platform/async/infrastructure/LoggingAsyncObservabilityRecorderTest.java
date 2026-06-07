package com.xiyu.bid.platform.async.infrastructure;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingAsyncObservabilityRecorderTest {
    @Test
    void recordDrop_shouldPromoteKnownAlertReasonToDropAlert() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LoggingAsyncObservabilityRecorder recorder = new LoggingAsyncObservabilityRecorder(registry);

        recorder.recordDrop("notification", "notification.wecom_push", "biz-1", "BUG", false);

        assertThat(registry.get("xiyu_async_total")
                .tag("result", "drop_alert")
                .tag("async_type", "notification")
                .tag("event_type", "notification.wecom_push")
                .tag("reason_code", "BUG")
                .counter()
                .count()).isEqualTo(1.0);
    }

    @Test
    void recordDrop_shouldKeepNormalDropForNonAlertReason() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LoggingAsyncObservabilityRecorder recorder = new LoggingAsyncObservabilityRecorder(registry);

        recorder.recordDrop("notification", "notification.wecom_push", "biz-1", "SIDE_EFFECT_OPTIONAL", false);

        assertThat(registry.get("xiyu_async_total")
                .tag("result", "drop")
                .tag("async_type", "notification")
                .tag("event_type", "notification.wecom_push")
                .tag("reason_code", "SIDE_EFFECT_OPTIONAL")
                .counter()
                .count()).isEqualTo(1.0);
    }
}
