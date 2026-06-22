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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class AuthServiceSessionTest {

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
    private User user;

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
        ReflectionTestUtils.setField(authService, "refreshExpiration", 7_200_000L);

        user = User.builder()
                .id(1L)
                .username("alice")
                .password("encoded")
                .email("alice@example.com")
                .fullName("Alice")
                .role(User.Role.ADMIN)
                .roleProfile(RoleProfile.builder().id(1L).code("admin").name("管理员").build())
                .enabled(true)
                .build();
    }

    @Test
    void login_ShouldCreateRefreshSessionAndReturnTokens() {
        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("secret");

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(jwtUtil.generateAccessToken("alice")).thenReturn("access-token");
        when(projectAccessScopeService.getAllowedProjectIds(user)).thenReturn(List.of(8L, 9L));
        when(projectAccessScopeService.getAllowedDepartmentCodes(user)).thenReturn(List.of("SALES"));
        when(dataScopeConfigService.getRoleMenuPermissions(user)).thenReturn(List.of("all"));
        when(refreshSessionRepository.save(any(RefreshSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthSessionResult result = authService.login(request);

        assertThat(result.getAccessToken()).isEqualTo("access-token");
        assertThat(result.getAuthResponse().getUsername()).isEqualTo("alice");
        assertThat(result.getAuthResponse().getAllowedProjectIds()).containsExactly(8L, 9L);
        assertThat(result.getAuthResponse().getAllowedDepts()).containsExactly("SALES");
        assertThat(result.getRefreshToken()).isNotBlank();

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(refreshSessionRepository).save(any(RefreshSession.class));
    }

    @Test
    void refreshToken_ShouldRotateStoredSessionAndReturnNewTokens() {
        RefreshSession session = RefreshSession.builder()
                .id(7L)
                .user(user)
                .tokenHash(authService.hashTokenForTest("old-refresh-token"))
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .build();

        when(refreshSessionRepository.findByTokenHash(authService.hashTokenForTest("old-refresh-token")))
                .thenReturn(Optional.of(session));
        when(jwtUtil.generateAccessToken("alice")).thenReturn("new-access-token");
        when(projectAccessScopeService.getAllowedProjectIds(user)).thenReturn(List.of(99L));
        when(projectAccessScopeService.getAllowedDepartmentCodes(user)).thenReturn(List.of("BID"));
        when(dataScopeConfigService.getRoleMenuPermissions(user)).thenReturn(List.of("all"));
        when(refreshSessionRepository.save(any(RefreshSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthSessionResult result = authService.refreshToken("old-refresh-token");

        assertThat(result.getAccessToken()).isEqualTo("new-access-token");
        assertThat(result.getAuthResponse().getAllowedProjectIds()).containsExactly(99L);
        assertThat(result.getAuthResponse().getAllowedDepts()).containsExactly("BID");
        assertThat(result.getRefreshToken()).isNotBlank();
        assertThat(session.getRevokedAt()).isNotNull();

        verify(refreshSessionRepository, times(2)).save(any(RefreshSession.class));
    }

    @Test
    void refreshToken_ShouldRejectRevokedOrUnknownSession() {
        when(refreshSessionRepository.findByTokenHash(authService.hashTokenForTest("missing-token")))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refreshToken("missing-token"))
                .isInstanceOf(InsufficientAuthenticationException.class);
    }

    @Test
    void logout_ShouldRevokeMatchingRefreshSession() {
        RefreshSession session = RefreshSession.builder()
                .id(7L)
                .user(user)
                .tokenHash(authService.hashTokenForTest("refresh-token"))
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .build();

        when(refreshSessionRepository.findByTokenHash(authService.hashTokenForTest("refresh-token")))
                .thenReturn(Optional.of(session));

        authService.logout("refresh-token");

        assertThat(session.getRevokedAt()).isNotNull();
        verify(refreshSessionRepository).save(session);
    }
}
