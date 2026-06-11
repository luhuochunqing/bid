// 维护声明: 仅维护认证链路；权限规则调整请同步 controller 与 security 配置.
package com.xiyu.bid.service;

import com.xiyu.bid.crm.application.OssDelegationService;
import com.xiyu.bid.admin.service.DataScopeConfigService;
import com.xiyu.bid.entity.RoleProfileCatalog;

import com.xiyu.bid.dto.AuthResponse;
import com.xiyu.bid.dto.AuthSessionResult;
import com.xiyu.bid.dto.LoginRequest;
import com.xiyu.bid.dto.RegisterRequest;
import com.xiyu.bid.entity.RefreshSession;
import com.xiyu.bid.entity.RoleProfile;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.RefreshSessionRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.auth.JwtUtil;
import com.xiyu.bid.auth.TokenRevocationService;
import com.xiyu.bid.util.PasswordValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    private static final String USER_NOT_FOUND = "User not found";

    private final UserRepository userRepository;
    private final RefreshSessionRepository refreshSessionRepository;
    private final ProjectAccessScopeService projectAccessScopeService;
    private final DataScopeConfigService dataScopeConfigService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final RoleProfileService roleProfileService;
    private final TokenRevocationService tokenRevocationService;
    private final OssDelegationService ossDelegationService;

    @Value("${jwt.refresh-expiration:604800000}")
    private long refreshExpiration;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Validate password strength
        PasswordValidator.ValidationResult passwordValidation = PasswordValidator.validate(request.getPassword());
        if (!passwordValidation.isValid()) {
            throw new IllegalArgumentException(passwordValidation.getMessage());
        }

        // Check if username exists
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already exists");
        }

        // Check if email exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }

        // Create new user
        RoleProfile roleProfile = roleProfileService.resolveRoleProfile(request.getResolvedRoleCode(), User.Role.STAFF);
        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .fullName(request.getFullName())
                .role(RoleProfileCatalog.legacyRoleForCode(roleProfile.getCode()))
                .roleProfile(roleProfile)
                .enabled(true)
                .build();

        user = userRepository.save(user);
        log.info("New user registered: {}", user.getUsername());

        String token = jwtUtil.generateAccessToken(user.getUsername());
        return AuthResponse.from(
                token,
                user,
                projectAccessScopeService.getAllowedProjectIds(user),
                projectAccessScopeService.getAllowedDepartmentCodes(user),
                dataScopeConfigService.getRoleMenuPermissions(user)
        );
    }

    @Transactional
    public AuthSessionResult login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException(USER_NOT_FOUND));

        // 组织架构同步用户（有 externalOrgSourceApp），委托给西域 OSS 统一认证
        if (user.getExternalOrgSourceApp() != null && !user.getExternalOrgSourceApp().isBlank()) {
            if (!ossDelegationService.authenticate(user, request.getPassword())) {
                throw new BadCredentialsException("Invalid username or password");
            }
            return loginWithoutPassword(user);
        }

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );
        String token = jwtUtil.generateAccessToken(user.getUsername());
        String refreshToken = createRefreshSession(user);
        log.info("User logged in: {}", user.getUsername());

        return AuthSessionResult.builder()
                .authResponse(AuthResponse.from(
                        token,
                        user,
                        projectAccessScopeService.getAllowedProjectIds(user),
                        projectAccessScopeService.getAllowedDepartmentCodes(user),
                        dataScopeConfigService.getRoleMenuPermissions(user)
                ))
                .refreshToken(refreshToken)
                .build();
    }

    @Transactional
    public AuthSessionResult loginWithoutPassword(User user) {
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new InsufficientAuthenticationException("User account is disabled");
        }

        String token = jwtUtil.generateAccessToken(user.getUsername());
        String refreshToken = createRefreshSession(user);
        log.info("User logged in via SSO/WeCom: {}", user.getUsername());
        return AuthSessionResult.builder()
                .authResponse(AuthResponse.from(
                        token,
                        user,
                        projectAccessScopeService.getAllowedProjectIds(user),
                        projectAccessScopeService.getAllowedDepartmentCodes(user),
                        dataScopeConfigService.getRoleMenuPermissions(user)
                ))
                .refreshToken(refreshToken)
                .build();
    }

    public AuthResponse getCurrentUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(USER_NOT_FOUND));

        return AuthResponse.builder()
                .type("Bearer")
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .phone(user.getPhone())
                .fullName(user.getFullName())
                .role(user.getRoleCode())
                .roleCode(user.getRoleCode())
                .roleName(user.getRoleName())
                .deptCode(user.getDepartmentCode())
                .dept(user.getDepartmentName())
                .allowedProjectIds(projectAccessScopeService.getAllowedProjectIds(user))
                .allowedDepts(projectAccessScopeService.getAllowedDepartmentCodes(user))
                .menuPermissions(dataScopeConfigService.getRoleMenuPermissions(user))
                .build();
    }

    @Transactional(readOnly = true)
    public Long resolveUserIdByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(User::getId)
                .orElseThrow(() -> new UsernameNotFoundException(USER_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public User resolveUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(USER_NOT_FOUND));
    }

    @Transactional
    public void logout(String accessToken, String refreshToken) {
        revokeAccessToken(accessToken);
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        refreshSessionRepository.findByTokenHash(hashToken(refreshToken))
                .filter(session -> session.getRevokedAt() == null)
                .ifPresent(session -> {
                    session.setRevokedAt(LocalDateTime.now());
                    refreshSessionRepository.save(session);
                    log.info("Refresh session revoked for user: {}", session.getUser().getUsername());
                });
    }

    @Transactional
    public void logout(String refreshToken) {
        logout(null, refreshToken);
    }

    private void revokeAccessToken(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return;
        }
        Optional<String> jtiOpt = jwtUtil.extractJti(accessToken);
        if (jtiOpt.isEmpty()) {
            return;
        }
        String jti = jtiOpt.get();
        Optional<Instant> expiresAtOpt = jwtUtil.extractExpirationInstant(accessToken);
        expiresAtOpt.ifPresent(expiresAt -> tokenRevocationService.revoke(jti, expiresAt));
        log.info("Access token revoked (jti={})", jti);
    }

    @Transactional
    public AuthSessionResult refreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InsufficientAuthenticationException("Refresh token is required");
        }

        RefreshSession session = refreshSessionRepository.findByTokenHash(hashToken(refreshToken))
                .orElseThrow(() -> new InsufficientAuthenticationException("Refresh token is invalid"));

        LocalDateTime now = LocalDateTime.now();
        if (session.getRevokedAt() != null || session.getExpiresAt().isBefore(now)) {
            throw new InsufficientAuthenticationException("Refresh token is no longer valid");
        }

        User user = session.getUser();
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new InsufficientAuthenticationException("User account is disabled");
        }

        String accessToken = jwtUtil.generateAccessToken(user.getUsername());
        session.setRevokedAt(now);
        refreshSessionRepository.save(session);
        String rotatedRefreshToken = createRefreshSession(user);
        log.info("Token refreshed for user: {}", user.getUsername());
        return AuthSessionResult.builder()
                .authResponse(AuthResponse.from(
                        accessToken,
                        user,
                        projectAccessScopeService.getAllowedProjectIds(user),
                        projectAccessScopeService.getAllowedDepartmentCodes(user),
                        dataScopeConfigService.getRoleMenuPermissions(user)
                ))
                .refreshToken(rotatedRefreshToken)
                .build();
    }

    String hashTokenForTest(String token) {
        return hashToken(token);
    }

    private String createRefreshSession(User user) {
        String refreshToken = generateRefreshToken();
        RefreshSession session = RefreshSession.builder()
                .user(user)
                .tokenHash(hashToken(refreshToken))
                .expiresAt(LocalDateTime.now().plusNanos(refreshExpiration * 1_000_000L))
                .build();
        refreshSessionRepository.save(session);
        return refreshToken;
    }

    private String generateRefreshToken() {
        return UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
    }

    private String hashToken(String token) {
        if (token == null || token.isBlank()) {
            throw new InsufficientAuthenticationException("Refresh token is invalid");
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest unavailable", ex);
        }
    }
}
