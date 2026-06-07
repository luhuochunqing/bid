package com.xiyu.bid.integration.application;

import com.xiyu.bid.integration.domain.WeComApiErrCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WeComAccessTokenProvider — caching and invalidation")
class WeComAccessTokenProviderTest {

    @Mock
    private WeComApiClient apiClient;

    private WeComAccessTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new WeComAccessTokenProvider(apiClient);
    }

    @Test
    @DisplayName("first call fetches from API and returns token")
    void firstCall_fetchesFromApi() {
        when(apiClient.requestAccessToken("corp", "secret"))
                .thenReturn(new WeComApiClient.WeComAccessTokenResponse("TOKEN_1", 7200L, 0, "ok"));

        String token = provider.getAccessToken("corp", "1000", "secret");

        assertThat(token).isEqualTo("TOKEN_1");
        verify(apiClient, times(1)).requestAccessToken("corp", "secret");
    }

    @Test
    @DisplayName("second call within TTL uses cache — API called only once")
    void secondCall_withinTtl_cacheHit() {
        when(apiClient.requestAccessToken("corp", "secret"))
                .thenReturn(new WeComApiClient.WeComAccessTokenResponse("TOKEN_1", 7200L, 0, "ok"));

        provider.getAccessToken("corp", "1000", "secret");
        String token = provider.getAccessToken("corp", "1000", "secret");

        assertThat(token).isEqualTo("TOKEN_1");
        verify(apiClient, times(1)).requestAccessToken("corp", "secret");
    }

    @Test
    @DisplayName("expired token is re-fetched")
    void expiredToken_refetches() throws Exception {
        // First call: expiresIn=0 → TTL becomes -60s → immediately expired
        when(apiClient.requestAccessToken("corp", "secret"))
                .thenReturn(new WeComApiClient.WeComAccessTokenResponse("TOKEN_OLD", 0L, 0, "ok"))
                .thenReturn(new WeComApiClient.WeComAccessTokenResponse("TOKEN_NEW", 7200L, 0, "ok"));

        provider.getAccessToken("corp", "1000", "secret"); // stores expired token (TTL=0-60 → already past)
        String token = provider.getAccessToken("corp", "1000", "secret"); // should re-fetch

        assertThat(token).isEqualTo("TOKEN_NEW");
        verify(apiClient, times(2)).requestAccessToken("corp", "secret");
    }

    @Test
    @DisplayName("invalidate forces re-fetch on next call")
    void invalidate_forcesRefetch() {
        when(apiClient.requestAccessToken("corp", "secret"))
                .thenReturn(new WeComApiClient.WeComAccessTokenResponse("TOKEN_1", 7200L, 0, "ok"))
                .thenReturn(new WeComApiClient.WeComAccessTokenResponse("TOKEN_2", 7200L, 0, "ok"));

        provider.getAccessToken("corp", "1000", "secret");
        provider.invalidate("corp", "1000");
        String token = provider.getAccessToken("corp", "1000", "secret");

        assertThat(token).isEqualTo("TOKEN_2");
        verify(apiClient, times(2)).requestAccessToken("corp", "secret");
    }

    @Test
    @DisplayName("API non-zero errcode throws WeComApiException")
    void apiError_throwsWeComApiException() {
        when(apiClient.requestAccessToken("corp", "secret"))
                .thenReturn(new WeComApiClient.WeComAccessTokenResponse(null, 0L, 40001, "invalid credential"));

        assertThatThrownBy(() -> provider.getAccessToken("corp", "1000", "secret"))
                .isInstanceOf(WeComApiException.class)
                .satisfies(ex -> assertThat(((WeComApiException) ex).errcode()).isEqualTo(40001));
    }
}
