package com.xiyu.bid.crm.application;

import com.xiyu.bid.crm.infrastructure.HomeSsoClient;
import com.xiyu.bid.dto.AuthSessionResult;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("HomeSsoService - Home 平台 SSO 免登业务服务")
@ExtendWith(MockitoExtension.class)
class HomeSsoServiceTest {

    private static final String VALID_TOKEN = "valid-token-123";
    private static final String INVALID_TOKEN = "invalid-token-456";
    private static final String USERNAME = "09118";

    @Mock
    private HomeSsoClient homeSsoClient;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthService authService;

    @InjectMocks
    private HomeSsoService homeSsoService;

    private User enabledUser;
    private User disabledUser;
    private AuthSessionResult sessionResult;

    @BeforeEach
    void setUp() {
        enabledUser = User.builder()
                .id(1L)
                .username(USERNAME)
                .email("test@example.com")
                .fullName("测试用户")
                .enabled(true)
                .build();

        disabledUser = User.builder()
                .id(2L)
                .username(USERNAME)
                .email("disabled@example.com")
                .fullName("禁用用户")
                .enabled(false)
                .build();

        sessionResult = AuthSessionResult.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .build();
    }

    @Test
    @DisplayName("token 有效且用户存在且启用时返回登录结果")
    void ssoLogin_validTokenAndEnabledUser_returnsSessionResult() {
        when(homeSsoClient.validateTokenAndGetUsername(VALID_TOKEN)).thenReturn(Optional.of(USERNAME));
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(enabledUser));
        when(authService.loginWithoutPassword(enabledUser)).thenReturn(sessionResult);

        AuthSessionResult result = homeSsoService.ssoLogin(VALID_TOKEN);

        assertThat(result).isNotNull();
        assertThat(result.getAccessToken()).isEqualTo("access-token");
        assertThat(result.getRefreshToken()).isEqualTo("refresh-token");
        verify(homeSsoClient).validateTokenAndGetUsername(VALID_TOKEN);
        verify(userRepository).findByUsername(USERNAME);
        verify(authService).loginWithoutPassword(enabledUser);
    }

    @Test
    @DisplayName("token 无效时抛出异常")
    void ssoLogin_invalidToken_throwsException() {
        when(homeSsoClient.validateTokenAndGetUsername(INVALID_TOKEN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> homeSsoService.ssoLogin(INVALID_TOKEN))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("SSO token 无效或已过期");

        verify(homeSsoClient).validateTokenAndGetUsername(INVALID_TOKEN);
        verify(userRepository, never()).findByUsername(anyString());
        verify(authService, never()).loginWithoutPassword(any(User.class));
    }

    @Test
    @DisplayName("用户不存在时抛出异常")
    void ssoLogin_userNotFound_throwsException() {
        when(homeSsoClient.validateTokenAndGetUsername(VALID_TOKEN)).thenReturn(Optional.of(USERNAME));
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> homeSsoService.ssoLogin(VALID_TOKEN))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("用户不存在或已禁用，请联系管理员");

        verify(homeSsoClient).validateTokenAndGetUsername(VALID_TOKEN);
        verify(userRepository).findByUsername(USERNAME);
        verify(authService, never()).loginWithoutPassword(any(User.class));
    }

    @Test
    @DisplayName("用户已禁用时抛出异常")
    void ssoLogin_userDisabled_throwsException() {
        when(homeSsoClient.validateTokenAndGetUsername(VALID_TOKEN)).thenReturn(Optional.of(USERNAME));
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(disabledUser));

        assertThatThrownBy(() -> homeSsoService.ssoLogin(VALID_TOKEN))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("用户不存在或已禁用，请联系管理员");

        verify(homeSsoClient).validateTokenAndGetUsername(VALID_TOKEN);
        verify(userRepository).findByUsername(USERNAME);
        verify(authService, never()).loginWithoutPassword(any(User.class));
    }
}
