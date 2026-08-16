package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AutoFailCircuitTest {

    @Test
    void opensAfterTwoAutoFailures() {
        assertThat(AutoFailCircuit.isOpen(1)).isFalse();
        assertThat(AutoFailCircuit.isOpen(2)).isTrue();
    }

    @Test
    void manualCompletedAfterLatestAutoFailLiftsCircuit() {
        LocalDateTime fail = LocalDateTime.of(2026, 8, 16, 12, 0);
        assertThat(AutoFailCircuit.isOpen(2, fail, fail.minusMinutes(1))).isTrue();
        assertThat(AutoFailCircuit.isOpen(2, fail, fail.plusMinutes(1))).isFalse();
        assertThat(AutoFailCircuit.isOpen(2, fail, fail)).isFalse();
    }

    @Test
    void windowIncludesLastThirtyMinutes() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 16, 12, 0);
        assertThat(AutoFailCircuit.inWindow(now.minusMinutes(29), now)).isTrue();
        assertThat(AutoFailCircuit.inWindow(now.minusMinutes(31), now)).isFalse();
        assertThat(AutoFailCircuit.inWindow(null, now)).isFalse();
    }
}
