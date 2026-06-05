package com.xiyu.bid.integration.application;

import com.xiyu.bid.integration.domain.WeComConnectivityResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WeComHttpConnectivityProbe — real probe via token fetch")
class WeComHttpConnectivityProbeTest {

    @Mock
    private WeComAccessTokenProvider tokenProvider;

    private WeComHttpConnectivityProbe probe;

    @BeforeEach
    void setUp() {
        probe = new WeComHttpConnectivityProbe(tokenProvider);
    }

    @Test
    @DisplayName("provider succeeds → returns success result")
    void providerSucceeds_returnsSuccess() {
        when(tokenProvider.getAccessToken("corp", "1000", "secret")).thenReturn("TOKEN");

        WeComConnectivityResult result = probe.probe("corp", "1000", "secret");

        assertThat(result.success()).isTrue();
        assertThat(result.message()).contains("成功");
        assertThat(result.probedAt()).isNotNull();
    }

    @Test
    @DisplayName("provider throws WeComApiException(40001) → returns failure result with errcode info")
    void providerThrowsApiException_returnsFailure() {
        when(tokenProvider.getAccessToken("corp", "1000", "secret"))
                .thenThrow(new WeComApiException(40001, "invalid credential"));

        WeComConnectivityResult result = probe.probe("corp", "1000", "secret");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("40001");
        assertThat(result.probedAt()).isNotNull();
    }
}
