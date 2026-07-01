package com.xiyu.bid.service;

import com.xiyu.bid.admin.service.DataScopeConfigService;
import com.xiyu.bid.auth.JwtUtil;
import com.xiyu.bid.auth.TokenRevocationService;
import com.xiyu.bid.crm.application.CrmAuthService;
import com.xiyu.bid.crm.application.OssDelegationService;
import com.xiyu.bid.crm.application.OssLoginFlowService;
import com.xiyu.bid.crm.application.OssPermissionCache;
import com.xiyu.bid.dto.AuthSessionResult;
import com.xiyu.bid.dto.LoginRequest;
import com.xiyu.bid.entity.RefreshSession;
import com.xiyu.bid.entity.RoleProfile;
import com.xiyu.bid.entity.RoleProfileCatalog;
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
import static org.mockito.ArgumentMatchers.eq;
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

    @Mock
    private OssPermissionCache ossPermissionCache;

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
                ossLoginFlowService,
                ossPermissionCache
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

    /**
     * CO-361 回归根因：logout 不应清 OSS 权限缓存。
     * <p>
     * 历史 bug：logout 调用 invalidateOssCache → ossPermissionCache.invalidate(username)
     * → 删除 Redis 里的 oss:perm:&lt;username&gt;。CO-362 把缓存迁到 Redis 持久化后，
     * 登出即丢缓存，用户重新登录前用未过期 JWT 访问会 cache miss → fail-closed → 看板空。
     * <p>
     * 修复：logout 只撤销 token + refresh session，不清权限缓存。下次登录时
     * OssLoginFlowService.put() 会覆盖刷新。
     */
    @Test
    void logout_ShouldNotInvalidateOssPermissionCache() {
        RefreshSession session = RefreshSession.builder()
                .user(buildUser())
                .tokenHash("existing-hash")
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        when(refreshSessionRepository.findByTokenHash(any())).thenReturn(Optional.of(session));
        when(jwtUtil.extractJti("access-jwt-co361")).thenReturn(Optional.of("jti-co361"));
        when(jwtUtil.extractExpirationInstant("access-jwt-co361")).thenReturn(Optional.of(Instant.now().plusSeconds(3600)));

        authService.logout("access-jwt-co361", "raw-refresh-token");

        // 关键断言：logout 不得清 OSS 权限缓存
        verify(ossPermissionCache, never()).invalidate(any());
        // 但仍应撤销 token 和 refresh session
        verify(tokenRevocationService).revoke(eq("jti-co361"), any());
        verify(refreshSessionRepository).save(session);
    }

    /**
     * CO-152 Review D5-1 回归：logout 不应清 CRM token 缓存。
     * <p>
     * 理由与 CO-361 一致：登出后旧 access token 已被 revoke，CRM token 缓存不会被滥用；
     * 用户重新登录时可直接复用未过期的 CRM token，避免每次登录都触发 generateToken。
     */
    @Test
    void logout_ShouldNotInvalidateCrmUserTokenCache() {
        RefreshSession session = RefreshSession.builder()
                .user(buildUser())
                .tokenHash("crm-co152-hash")
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        when(refreshSessionRepository.findByTokenHash(any())).thenReturn(Optional.of(session));
        when(jwtUtil.extractJti("access-jwt-crm")).thenReturn(Optional.of("jti-crm"));
        when(jwtUtil.extractExpirationInstant("access-jwt-crm")).thenReturn(Optional.of(Instant.now().plusSeconds(3600)));

        authService.logout("access-jwt-crm", "raw-refresh-token");

        // 关键断言：logout 不得清 CRM 用户 token 缓存
        verify(crmAuthService, never()).logoutUser(any());
        verify(crmAuthService, never()).handleUnauthorizedForUser(any());
        // 但仍应撤销 token 和 refresh session
        verify(tokenRevocationService).revoke(eq("jti-crm"), any());
        verify(refreshSessionRepository).save(session);
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
                ossLoginFlowService,
                ossPermissionCache
        );
        ReflectionTestUtils.setField(authService, "refreshExpiration", 7 * 24 * 60 * 60 * 1000L);

        when(userRepository.findByUsername("00444")).thenReturn(Optional.of(user));
        when(ossDelegationService.authenticate(user, "localpass")).thenReturn(false);
        when(passwordEncoder.matches("localpass", encodedPassword)).thenReturn(true);
        when(jwtUtil.generateAccessToken("00444")).thenReturn("access-token");
        when(projectAccessScopeService.getAllowedProjectIds(user)).thenReturn(List.of());
        when(projectAccessScopeService.getAllowedDepartmentCodes(user)).thenReturn(List.of());
        when(dataScopeConfigService.getRoleMenuPermissions(user)).thenReturn(List.of());
        when(ossPermissionCache.getRoleCode("00444")).thenReturn(Optional.of("bid-Team"));

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
                ossLoginFlowService,
                ossPermissionCache
        );
        ReflectionTestUtils.setField(authService, "refreshExpiration", 7 * 24 * 60 * 60 * 1000L);

        when(userRepository.findByUsername("00444")).thenReturn(Optional.of(user));
        when(ossDelegationService.authenticate(user, "anypass")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);

        verify(ossDelegationService).authenticate(user, "anypass");
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void refreshToken_ShouldAllowLocalAccountWithoutOssRole() {
        User localUser = buildUser();
        RefreshSession session = RefreshSession.builder()
                .id(2L)
                .user(localUser)
                .tokenHash("local-user-hash")
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();

        when(refreshSessionRepository.findByTokenHash(any())).thenReturn(Optional.of(session));
        when(jwtUtil.generateAccessToken("alice")).thenReturn("local-access-token");
        when(projectAccessScopeService.getAllowedProjectIds(localUser)).thenReturn(List.of(1L));
        when(projectAccessScopeService.getAllowedDepartmentCodes(localUser)).thenReturn(List.of("ADMIN"));
        when(dataScopeConfigService.getRoleMenuPermissions(localUser)).thenReturn(List.of("all"));

        AuthSessionResult result = authService.refreshToken("raw-local-refresh-token");

        verify(ossPermissionCache, never()).getRoleCode(any());
        assertThat(result.getAccessToken()).isEqualTo("local-access-token");
        assertThat(result.getRefreshToken()).isNotBlank();
    }

    private User buildOssUser() {
        return User.builder()
                .id(2L)
                .username("00444")
                .email("caiqin@xiyu.com")
                .fullName("蔡勤")
                .role(User.Role.MANAGER)
                .roleProfile(RoleProfile.builder().id(2L).code("bid-Team").name("投标专员").build())
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

    @Test
    void loginWithoutPassword_ShouldFailWhenOssRoleCacheEmpty() {
        User user = buildOssUser();

        when(ossPermissionCache.getRoleCode("00444")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.loginWithoutPassword(user))
                .isInstanceOf(com.xiyu.bid.exception.RoleNotAuthorizedException.class)
                .hasMessageContaining("无有效 OSS 角色");
    }

    @Test
    void loginWithoutPassword_ShouldFailWhenOssRoleBlank() {
        User user = buildOssUser();

        when(ossPermissionCache.getRoleCode("00444")).thenReturn(Optional.of(""));

        assertThatThrownBy(() -> authService.loginWithoutPassword(user))
                .isInstanceOf(com.xiyu.bid.exception.RoleNotAuthorizedException.class)
                .hasMessageContaining("无有效 OSS 角色");
    }

    @Test
    void loginWithoutPassword_ShouldSucceedWhenOssRolePresent() {
        User user = buildOssUser();

        when(jwtUtil.generateAccessToken("00444")).thenReturn("sso-access-token");
        when(projectAccessScopeService.getAllowedProjectIds(user)).thenReturn(List.of());
        when(projectAccessScopeService.getAllowedDepartmentCodes(user)).thenReturn(List.of());
        when(dataScopeConfigService.getRoleMenuPermissions(user)).thenReturn(List.of());
        when(dataScopeConfigService.getRoleCode(user)).thenReturn(RoleProfileCatalog.BID_SPECIALIST_CODE);
        when(dataScopeConfigService.getRoleName(user)).thenReturn("投标专员");
        when(ossPermissionCache.getRoleCode("00444")).thenReturn(Optional.of(RoleProfileCatalog.BID_SPECIALIST_CODE));

        AuthSessionResult result = authService.loginWithoutPassword(user);

        assertThat(result.getAccessToken()).isEqualTo("sso-access-token");
        verify(refreshSessionRepository).save(any(RefreshSession.class));
    }

    @Test
    void refreshToken_ShouldFailWhenOssRoleCacheEmpty() {
        User user = buildOssUser();
        RefreshSession session = RefreshSession.builder()
                .id(1L)
                .user(user)
                .tokenHash("existing-hash")
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();

        when(refreshSessionRepository.findByTokenHash(any())).thenReturn(Optional.of(session));
        when(ossPermissionCache.getRoleCode("00444")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refreshToken("raw-refresh-token"))
                .isInstanceOf(com.xiyu.bid.exception.RoleNotAuthorizedException.class)
                .hasMessageContaining("无有效 OSS 角色");
    }
}
