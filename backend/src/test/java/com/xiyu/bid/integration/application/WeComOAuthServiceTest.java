package com.xiyu.bid.integration.application;

import com.xiyu.bid.integration.domain.WeComApiErrCode;
import com.xiyu.bid.integration.infrastructure.persistence.entity.WeComIntegrationEntity;
import com.xiyu.bid.integration.infrastructure.persistence.repository.WeComIntegrationJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WeComOAuthService — OAuth2 orchestration")
class WeComOAuthServiceTest {

    @Mock
    private WeComApiClient apiClient;
    @Mock
    private WeComAccessTokenProvider tokenProvider;
    @Mock
    private WeComIntegrationJpaRepository integrationRepository;
    @Mock
    private WeComCredentialCipher cipher;

    private WeComOAuthService oAuthService;

    @BeforeEach
    void setUp() {
        oAuthService = new WeComOAuthService(apiClient, tokenProvider, integrationRepository, cipher);
    }

    @Test
    @DisplayName("getAuthenticatedUserInfo success → returns user info")
    void getAuthenticatedUserInfo_success() {
        // Arrange
        WeComIntegrationEntity entity = new WeComIntegrationEntity();
        entity.setSsoEnabled(true);
        entity.setCorpId("CORP");
        entity.setAgentId("AGENT");
        entity.setEncryptedSecret("ENCRYPTED");
        
        when(integrationRepository.findAll()).thenReturn(List.of(entity));
        when(cipher.decrypt("ENCRYPTED")).thenReturn("PLAIN");
        when(tokenProvider.getAccessToken("CORP", "AGENT", "PLAIN")).thenReturn("TOKEN");
        
        var apiResponse = new WeComApiClient.WeComUserInfoResponse(
                0, "ok", "USER123", null, "TICKET", 7200);
        when(apiClient.requestUserInfo("TOKEN", "CODE")).thenReturn(apiResponse);

        // Act
        Optional<WeComApiClient.WeComUserInfoResponse> result = oAuthService.getAuthenticatedUserInfo("CODE");

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().userId()).isEqualTo("USER123");
    }

    @Test
    @DisplayName("getAuthenticatedUserInfo with API error → returns empty")
    void getAuthenticatedUserInfo_apiError_returnsEmpty() {
        WeComIntegrationEntity entity = new WeComIntegrationEntity();
        entity.setSsoEnabled(true);
        entity.setCorpId("CORP");
        entity.setAgentId("AGENT");
        entity.setEncryptedSecret("ENCRYPTED");
        when(integrationRepository.findAll()).thenReturn(List.of(entity));
        when(cipher.decrypt("ENCRYPTED")).thenReturn("PLAIN");
        when(tokenProvider.getAccessToken(anyString(), anyString(), anyString())).thenReturn("TOKEN");

        var apiResponse = new WeComApiClient.WeComUserInfoResponse(
                40001, "invalid code", null, null, null, 0);
        when(apiClient.requestUserInfo("TOKEN", "CODE")).thenReturn(apiResponse);

        Optional<WeComApiClient.WeComUserInfoResponse> result = oAuthService.getAuthenticatedUserInfo("CODE");

        assertThat(result).isEmpty();
    }
}
