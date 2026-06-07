package com.xiyu.bid.integration.application;

import com.xiyu.bid.integration.domain.WeComSendResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WeComMessagePublisher — text message sending with retry")
class WeComMessagePublisherTest {

    @Mock
    private WeComAccessTokenProvider tokenProvider;

    @Mock
    private WeComApiClient apiClient;

    private WeComMessagePublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new WeComMessagePublisher(tokenProvider, apiClient);
    }

    @Test
    @DisplayName("happy path → success result")
    void happyPath_success() {
        when(tokenProvider.getAccessToken("corp", "1000", "secret")).thenReturn("TOKEN");
        when(apiClient.sendAppMessage(eq("TOKEN"), any()))
                .thenReturn(new WeComApiClient.WeComSendResponse(0, "ok", null));

        WeComSendResult result = publisher.sendTextMessage(
                "corp", "1000", "secret", List.of("user1"), "hello");

        assertThat(result.success()).isTrue();
        assertThat(result.errcode()).isEqualTo(0);
        assertThat(result.sentTo()).containsExactly("user1");
    }

    @Test
    @DisplayName("40014 on first send → invalidate, retry once, success")
    void invalidToken_retriesOnce_success() {
        when(tokenProvider.getAccessToken("corp", "1000", "secret"))
                .thenReturn("STALE_TOKEN")
                .thenReturn("FRESH_TOKEN");
        when(apiClient.sendAppMessage(eq("STALE_TOKEN"), any()))
                .thenReturn(new WeComApiClient.WeComSendResponse(40014, "invalid access_token", null));
        when(apiClient.sendAppMessage(eq("FRESH_TOKEN"), any()))
                .thenReturn(new WeComApiClient.WeComSendResponse(0, "ok", null));

        WeComSendResult result = publisher.sendTextMessage(
                "corp", "1000", "secret", List.of("user1"), "hello");

        assertThat(result.success()).isTrue();
        verify(tokenProvider, times(1)).invalidate("corp", "1000");
        verify(tokenProvider, times(2)).getAccessToken("corp", "1000", "secret");
    }

    @Test
    @DisplayName("40014 twice → final failure result (no infinite loop)")
    void invalidToken_twiceFails_returnsFailure() {
        when(tokenProvider.getAccessToken("corp", "1000", "secret"))
                .thenReturn("STALE_TOKEN")
                .thenReturn("FRESH_TOKEN");
        when(apiClient.sendAppMessage(eq("STALE_TOKEN"), any()))
                .thenReturn(new WeComApiClient.WeComSendResponse(40014, "invalid access_token", null));
        when(apiClient.sendAppMessage(eq("FRESH_TOKEN"), any()))
                .thenReturn(new WeComApiClient.WeComSendResponse(40014, "still invalid", null));

        WeComSendResult result = publisher.sendTextMessage(
                "corp", "1000", "secret", List.of("user1"), "hello");

        assertThat(result.success()).isFalse();
        assertThat(result.errcode()).isEqualTo(40014);
        // Only one retry — total 2 sendAppMessage calls
        verify(apiClient, times(2)).sendAppMessage(anyString(), any());
        verify(tokenProvider, times(1)).invalidate("corp", "1000");
    }

    @Test
    @DisplayName("empty toUserIds → throws IllegalArgumentException")
    void emptyToUserIds_throwsIAE() {
        assertThatThrownBy(() -> publisher.sendTextMessage(
                "corp", "1000", "secret", List.of(), "hello"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(apiClient, never()).sendAppMessage(anyString(), any());
    }

    @Test
    @DisplayName("null toUserIds → throws IllegalArgumentException")
    void nullToUserIds_throwsIAE() {
        assertThatThrownBy(() -> publisher.sendTextMessage(
                "corp", "1000", "secret", null, "hello"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
