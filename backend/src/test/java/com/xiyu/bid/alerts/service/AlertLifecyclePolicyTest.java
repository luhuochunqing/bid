package com.xiyu.bid.alerts.service;

import com.xiyu.bid.alerts.entity.AlertHistory;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlertLifecyclePolicyTest {

    private final AlertLifecyclePolicy policy = new AlertLifecyclePolicy();

    @Test
    void acknowledge_ShouldSetAcknowledgedAt_ForActiveAlert() {
        AlertHistory alertHistory = AlertHistory.builder()
                .resolved(false)
                .build();

        LocalDateTime now = LocalDateTime.of(2026, 4, 21, 10, 0);
        policy.acknowledge(alertHistory, now);

        assertThat(alertHistory.getAcknowledgedAt()).isEqualTo(now);
        assertThat(alertHistory.getResolvedAt()).isNull();
    }

    @Test
    void acknowledge_ShouldRejectResolvedAlert() {
        AlertHistory alertHistory = AlertHistory.builder()
                .resolved(true)
                .resolvedAt(LocalDateTime.of(2026, 4, 21, 9, 0))
                .build();

        assertThatThrownBy(() -> policy.acknowledge(alertHistory, LocalDateTime.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("acknowledged");
    }

    @Test
    void resolve_ShouldAcknowledgeBeforeResolving() {
        AlertHistory alertHistory = AlertHistory.builder()
                .resolved(false)
                .build();

        LocalDateTime now = LocalDateTime.of(2026, 4, 21, 11, 0);
        policy.resolve(alertHistory, now);

        assertThat(alertHistory.getAcknowledgedAt()).isEqualTo(now);
        assertThat(alertHistory.getResolvedAt()).isEqualTo(now);
        assertThat(alertHistory.getResolved()).isTrue();
    }

    @Test
    void resolve_ShouldRejectResolvedAlert() {
        AlertHistory alertHistory = AlertHistory.builder()
                .resolved(true)
                .resolvedAt(LocalDateTime.of(2026, 4, 21, 9, 0))
                .build();

        assertThatThrownBy(() -> policy.resolve(alertHistory, LocalDateTime.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("resolved");
    }
}
