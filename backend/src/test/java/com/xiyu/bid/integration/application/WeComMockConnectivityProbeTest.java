package com.xiyu.bid.integration.application;

import com.xiyu.bid.integration.domain.WeComConnectivityResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WeComMockConnectivityProbe — always returns success")
class WeComMockConnectivityProbeTest {

    private final WeComMockConnectivityProbe probe = new WeComMockConnectivityProbe();

    @Test
    @DisplayName("probe returns success=true")
    void probe_returnsSuccess() {
        WeComConnectivityResult result = probe.probe("wwcorp", "1000001", "secret");
        assertThat(result.success()).isTrue();
    }

    @Test
    @DisplayName("probe result has a non-null message")
    void probe_hasMessage() {
        WeComConnectivityResult result = probe.probe("wwcorp", "1000001", "secret");
        assertThat(result.message()).isNotBlank();
    }

    @Test
    @DisplayName("probe result has a non-null probedAt timestamp")
    void probe_hasProbedAt() {
        WeComConnectivityResult result = probe.probe("wwcorp", "1000001", "secret");
        assertThat(result.probedAt()).isNotNull();
    }
}
