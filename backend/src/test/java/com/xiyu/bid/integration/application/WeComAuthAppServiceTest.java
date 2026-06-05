package com.xiyu.bid.integration.application;

import com.xiyu.bid.dto.AuthSessionResult;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.integration.infrastructure.persistence.entity.WeComIntegrationEntity;
import com.xiyu.bid.integration.infrastructure.persistence.repository.WeComIntegrationJpaRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WeComAuthAppService — Authentication Orchestration")
class WeComAuthAppServiceTest {

    @Mock
    private WeComOAuthService weComOAuthService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuthService authService;
    @Mock
    private WeComIntegrationJpaRepository integrationRepository;

    private WeComAuthAppService authAppService;

    @BeforeEach
    void setUp() {
        authAppService = new WeComAuthAppService(weComOAuthService, userRepository, authService, integrationRepository);
    }

    @Test
    @DisplayName("loginByWeCom: existing user → returns login result")
    void loginByWeCom_existingUser() {
        // Arrange
        var userInfo = new WeComApiClient.WeComUserInfoResponse(0, "ok", "USER123", null, null, 0);
        when(weComOAuthService.getAuthenticatedUserInfo("CODE")).thenReturn(Optional.of(userInfo));
        
        User user = new User();
        user.setWecomUserId("USER123");
        when(userRepository.findByWecomUserId("USER123")).thenReturn(Optional.of(user));
        
        AuthSessionResult authResult = mock(AuthSessionResult.class);
        when(authService.loginWithoutPassword(user)).thenReturn(authResult);

        // Act
        Optional<AuthSessionResult> result = authAppService.loginByWeCom("CODE");

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(authResult);
    }

    @Test
    @DisplayName("loginByWeCom: new user but found by mobile → links and returns login result")
    void loginByWeCom_foundByMobile() {
        // Arrange
        var userInfo = new WeComApiClient.WeComUserInfoResponse(0, "ok", "USER123", null, "TICKET", 7200);
        when(weComOAuthService.getAuthenticatedUserInfo("CODE")).thenReturn(Optional.of(userInfo));
        
        when(userRepository.findByWecomUserId("USER123")).thenReturn(Optional.empty());
        
        var userDetail = new WeComApiClient.WeComUserDetailResponse(0, "ok",
                "USER123", "User Name", "1", "avatar", "qrcode",
                "13800138000", "test@example.com", null, "Address");
        when(weComOAuthService.getUserDetail("TICKET")).thenReturn(Optional.of(userDetail));
        
        User user = new User();
        user.setPhone("13800138000");
        when(userRepository.findByPhone("13800138000")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);
        
        AuthSessionResult authResult = mock(AuthSessionResult.class);
        when(authService.loginWithoutPassword(user)).thenReturn(authResult);

        // Act
        Optional<AuthSessionResult> result = authAppService.loginByWeCom("CODE");

        // Assert
        assertThat(result).isPresent();
        assertThat(user.getWecomUserId()).isEqualTo("USER123");
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("getAuthorizeParams: success → returns params")
    void getAuthorizeParams_success() {
        // Arrange
        WeComIntegrationEntity entity = new WeComIntegrationEntity();
        entity.setCorpId("CORP123");
        entity.setAgentId("AGENT123");
        when(integrationRepository.findById(1L)).thenReturn(Optional.of(entity));

        // Act
        Map<String, String> params = authAppService.getAuthorizeParams("MYSTATE");

        // Assert
        assertThat(params).containsEntry("appid", "CORP123");
        assertThat(params).containsEntry("agentid", "AGENT123");
        assertThat(params).containsEntry("state", "MYSTATE");
    }
}
