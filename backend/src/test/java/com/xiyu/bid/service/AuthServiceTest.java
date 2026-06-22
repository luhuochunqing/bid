package com.xiyu.bid.service;

import com.xiyu.bid.admin.service.DataScopeConfigService;
import com.xiyu.bid.auth.JwtUtil;
import com.xiyu.bid.auth.TokenRevocationService;
import com.xiyu.bid.crm.application.CrmAuthService;
import com.xiyu.bid.crm.application.OssDelegationService;
import com.xiyu.bid.crm.application.OssLoginFlowService;
import com.xiyu.bid.dto.AuthSessionResult;
import com.xiyu.bid.dto.LoginRequest;
import com.xiyu.bid.entity.RefreshSession;
import com.xiyu.bid.entity.RoleProfile;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.RefreshSessionRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.integration.organization.application.OrganizationUserSyncWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshSessionRepository refreshSessionRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private ProjectAccessScopeService projectAccessScopeService;

    @Mock
    private DataScopeConfigService dataScopeConfigService;

    @Mock
    private RoleProfileService roleProfileService;

    @Mock
    private TokenRevocationService tokenRevocationService;

    @Mock
    private CrmAuthService crmAuthService;

    @Mock
    private OssLoginFlowService ossLoginFlowService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                refreshSessionRepository,
                projectAccessScopeService,
                dataScopeConfigService,
                passwordEncoder,
                jwtUtil,
                authenticationManager,
                roleProfileService,
                tokenRevocationService,
                mock(OssDelegationService.class),
                crmAuthService,
                ossLoginFlowService
        );
        ReflectionTestUtils.setField(authService, "refreshExpiration", 7 * 24 * 60 * 60 * 1000L);
    }

    @Test
    void login_ShouldCreateRefreshSessionAndReturnTokens() {
        User user = buildUser();
        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("secret");

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(jwtUtil.generateAccessToken("alice")).thenReturn("access-token");
        when(projectAccessScopeService.getAllowedProjectIds(user)).thenReturn(List.of(11L, 12L));
        when(projectAccessScopeService.getAllowedDepartmentCodes(user)).thenReturn(List.of("SALES"));
        when(dataScopeConfigService.getRoleMenuPermissions(user)).thenReturn(List.of("all"));

        AuthSessionResult result = authService.login(request);

        verify(authenticationManager).authenticate(any());
        verify(refreshSessionRepository).save(any(RefreshSession.class));
        assertThat(result.getAccessToken()).isEqualTo("access-token");
        assertThat(result.getAuthResponse().getAllowedProjectIds()).containsExactly(11L, 12L);
        assertThat(result.getAuthResponse().getAllowedDepts()).containsExactly("SALES");
        assertThat(result.getRefreshToken()).isNotBlank();
    }

    @Test
    void refreshToken_ShouldRevokePreviousSessionAndCreateNewOne() {
        User user = buildUser();
        RefreshSession session = RefreshSession.builder()
                .id(1L)
                .user(user)
                .tokenHash("existing-hash")
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();

        when(refreshSessionRepository.findByTokenHash(any())).thenReturn(Optional.of(session));
        when(jwtUtil.generateAccessToken("alice")).thenReturn("rotated-access-token");
        when(projectAccessScopeService.getAllowedProjectIds(user)).thenReturn(List.of(21L));
        when(projectAccessScopeService.getAllowedDepartmentCodes(user)).thenReturn(List.of("BID"));
        when(dataScopeConfigService.getRoleMenuPermissions(user)).thenReturn(List.of("all"));

        AuthSessionResult result = authService.refreshToken("raw-refresh-token");

        ArgumentCaptor<RefreshSession> captor = ArgumentCaptor.forClass(RefreshSession.class);
        verify(refreshSessionRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getRevokedAt()).isNotNull();
        assertThat(captor.getAllValues().get(1).getTokenHash()).isNotBlank();
        assertThat(captor.getAllValues().get(1).getUser()).isEqualTo(user);
        assertThat(result.getAccessToken()).isEqualTo("rotated-access-token");
        assertThat(result.getAuthResponse().getAllowedProjectIds()).containsExactly(21L);
        assertThat(result.getAuthResponse().getAllowedDepts()).containsExactly("BID");
        assertThat(result.getRefreshToken()).isNotBlank();
    }

    @Test
    void refreshToken_ShouldRejectMissingRefreshToken() {
        assertThatThrownBy(() -> authService.refreshToken(null))
                .isInstanceOf(InsufficientAuthenticationException.class);
    }

    @Test
    void logout_ShouldRevokeMatchingSession() {
        RefreshSession session = RefreshSession.builder()
                .user(buildUser())
                .tokenHash("existing-hash")
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();

        when(refreshSessionRepository.findByTokenHash(any())).thenReturn(Optional.of(session));

        authService.logout("raw-refresh-token");

        verify(refreshSessionRepository).save(session);
        assertThat(session.getRevokedAt()).isNotNull();
    }

    @Test
    void logout_ShouldRevokeAccessTokenWhenJtiPresent() {
        RefreshSession session = RefreshSession.builder()
                .user(buildUser())
                .tokenHash("existing-hash")
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();

        when(refreshSessionRepository.findByTokenHash(any())).thenReturn(Optional.of(session));
        Instant exp = Instant.now().plusSeconds(3600);
        when(jwtUtil.extractJti("access-jwt")).thenReturn(Optional.of("jti-123"));
        when(jwtUtil.extractExpirationInstant("access-jwt")).thenReturn(Optional.of(exp));

        authService.logout("access-jwt", "raw-refresh-token");

        verify(tokenRevocationService).revoke("jti-123", exp);
        verify(refreshSessionRepository).save(session);
    }

    @Test
    void logout_ShouldSkipAccessRevocationWhenJtiAbsent() {
        when(jwtUtil.extractJti("legacy-token")).thenReturn(Optional.empty());

        authService.logout("legacy-token", null);

        verify(tokenRevocationService, never()).revoke(any(), any());
    }

    @Test
    void login_ossUserWithLocalPassword_shouldFallbackToLocalAuthWhenOssFails() {
        User user = buildOssUser();
        String encodedPassword = "$2a$10$localpasshash";
        user.setPassword(encodedPassword);
        LoginRequest request = new LoginRequest();
        request.setUsername("00444");
        request.setPassword("localpass");

        OssDelegationService ossDelegationService = mock(OssDelegationService.class);
        authService = new AuthService(
                userRepository,
                refreshSessionRepository,
                projectAccessScopeService,
                dataScopeConfigService,
                passwordEncoder,
                jwtUtil,
                authenticationManager,
                roleProfileService,
                tokenRevocationService,
                ossDelegationService,
                crmAuthService,
                ossLoginFlowService
        );
        ReflectionTestUtils.setField(authService, "refreshExpiration", 7 * 24 * 60 * 60 * 1000L);

        when(userRepository.findByUsername("00444")).thenReturn(Optional.of(user));
        when(ossDelegationService.authenticate(user, "localpass")).thenReturn(false);
        when(passwordEncoder.matches("localpass", encodedPassword)).thenReturn(true);
        when(jwtUtil.generateAccessToken("00444")).thenReturn("access-token");
        when(projectAccessScopeService.getAllowedProjectIds(user)).thenReturn(List.of());
        when(projectAccessScopeService.getAllowedDepartmentCodes(user)).thenReturn(List.of());
        when(dataScopeConfigService.getRoleMenuPermissions(user)).thenReturn(List.of());

        AuthSessionResult result = authService.login(request);

        verify(ossDelegationService).authenticate(user, "localpass");
        assertThat(result.getAccessToken()).isEqualTo("access-token");
    }

    @Test
    void login_ossUserWithLockedPassword_shouldThrowWhenOssFails() {
        User user = buildOssUser();
        user.setPassword(OrganizationUserSyncWriter.LOCKED_PASSWORD_HASH);
        LoginRequest request = new LoginRequest();
        request.setUsername("00444");
        request.setPassword("anypass");

        OssDelegationService ossDelegationService = mock(OssDelegationService.class);
        authService = new AuthService(
                userRepository,
                refreshSessionRepository,
                projectAccessScopeService,
                dataScopeConfigService,
                passwordEncoder,
                jwtUtil,
                authenticationManager,
                roleProfileService,
                tokenRevocationService,
                ossDelegationService,
                crmAuthService,
                ossLoginFlowService
        );
        ReflectionTestUtils.setField(authService, "refreshExpiration", 7 * 24 * 60 * 60 * 1000L);

        when(userRepository.findByUsername("00444")).thenReturn(Optional.of(user));
        when(ossDelegationService.authenticate(user, "anypass")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);

        verify(ossDelegationService).authenticate(user, "anypass");
        verify(passwordEncoder, never()).matches(any(), any());
    }

    private User buildOssUser() {
        return User.builder()
                .id(2L)
                .username("00444")
                .email("caiqin@xiyu.com")
                .fullName("蔡勤")
                .role(User.Role.STAFF)
                .roleProfile(RoleProfile.builder().id(2L).code("bid_specialist").name("投标专员").build())
                .enabled(true)
                .password("encoded")
                .externalOrgSourceApp("oss")
                .externalOrgUserId("oss-00444")
                .build();
    }

    private User buildUser() {
        return User.builder()
                .id(1L)
                .username("alice")
                .email("alice@example.com")
                .fullName("Alice")
                .role(User.Role.ADMIN)
                .roleProfile(RoleProfile.builder().id(1L).code("admin").name("管理员").build())
                .enabled(true)
                .password("encoded")
                .build();
    }
}
