package com.xiyu.bid.integration.controller;

import com.xiyu.bid.auth.OAuthStateService;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.dto.AuthResponse;
import com.xiyu.bid.dto.AuthSessionResult;
import com.xiyu.bid.integration.application.WeComAuthAppService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WeComAuthController — Web API for OAuth2")
class WeComAuthControllerTest {

    @Mock
    private WeComAuthAppService weComAuthAppService;
    @Mock
    private OAuthStateService oAuthStateService;
    @Mock
    private HttpServletResponse response;

    private WeComAuthController controller;

    @BeforeEach
    void setUp() {
        controller = new WeComAuthController(weComAuthAppService, oAuthStateService);
        ReflectionTestUtils.setField(controller, "refreshCookieName", "refresh_token");
        ReflectionTestUtils.setField(controller, "refreshCookieSecure", false);
        ReflectionTestUtils.setField(controller, "refreshCookieSameSite", "Lax");
        ReflectionTestUtils.setField(controller, "refreshExpiration", 604800000L);
        // H13 根治 (2026-06-14): access cookie 字段 (MockitoExtension 无 Spring context, 需手动注入)
        ReflectionTestUtils.setField(controller, "accessCookieName", "access_token");
        ReflectionTestUtils.setField(controller, "accessCookieSecure", false);
        ReflectionTestUtils.setField(controller, "accessCookieSameSite", "Lax");
        ReflectionTestUtils.setField(controller, "accessExpiration", 86400000L);
    }

    @Test
    @DisplayName("getAuthorizeParams → generates state and returns params")
    void getAuthorizeParams_generatesState() {
        // Arrange
        Map<String, String> mockParams = Map.of("appid", "corp", "agentid", "1", "state", "any");
        when(weComAuthAppService.getAuthorizeParams(anyString())).thenReturn(mockParams);

        // Act
        ResponseEntity<ApiResponse<Map<String, String>>> result = controller.getAuthorizeParams();

        // Assert
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getData()).isEqualTo(mockParams);
        verify(oAuthStateService).storeState(anyString());
    }

    @Test
    @DisplayName("callback with invalid state → returns 403")
    void callback_invalidState() {
        // Arrange
        when(oAuthStateService.validateAndRemoveState("invalid")).thenReturn(false);

        // Act
        ResponseEntity<ApiResponse<?>> result = controller.callback("code", "invalid", response);

        // Assert
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(result.getBody().getMessage()).isEqualTo("INVALID_STATE");
    }

    @Test
    @DisplayName("callback success → returns 200 with AuthResponse")
    void callback_success() {
        // Arrange
        when(oAuthStateService.validateAndRemoveState("valid")).thenReturn(true);
        
        AuthResponse authResponse = new AuthResponse();
        authResponse.setUsername("user");
        
        AuthSessionResult authSessionResult = mock(AuthSessionResult.class);
        when(authSessionResult.getAuthResponse()).thenReturn(authResponse);
        when(authSessionResult.getRefreshToken()).thenReturn("refresh");
        when(authSessionResult.getAccessToken()).thenReturn("access");
        
        when(weComAuthAppService.loginByWeCom("code")).thenReturn(Optional.of(authSessionResult));

        // Act
        ResponseEntity<ApiResponse<?>> result = controller.callback("code", "valid", response);

        // Assert
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getData()).isEqualTo(authResponse);
        assertThat(result.getHeaders().get("Set-Cookie")).isNotEmpty();
    }

    @Test
    @DisplayName("callback not bound → returns 40101")
    void callback_notBound() {
        // Arrange
        when(oAuthStateService.validateAndRemoveState("valid")).thenReturn(true);
        when(weComAuthAppService.loginByWeCom("code")).thenReturn(Optional.empty());

        // Act
        ResponseEntity<ApiResponse<?>> result = controller.callback("code", "valid", response);

        // Assert
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(result.getBody().getCode()).isEqualTo(40101);
    }
}
