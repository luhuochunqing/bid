// 维护声明: 仅维护认证链路；权限规则调整请同步 controller 与 security 配置.
package com.xiyu.bid.service;
import com.xiyu.bid.crm.application.CrmAuthService;
import com.xiyu.bid.crm.application.OssDelegationService;
import com.xiyu.bid.crm.application.OssDirectLoginService;
import com.xiyu.bid.crm.application.OssLoginFlowService;
import com.xiyu.bid.admin.service.DataScopeConfigService;
import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.integration.organization.application.OrganizationUserSyncWriter;
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
    private final CrmAuthService crmAuthService;
    private final OssLoginFlowService ossLoginFlowService;
    private final OssDirectLoginService ossDirectLoginService;
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
        RoleProfile roleProfile = roleProfileService.resolveRoleProfile(request.getResolvedRoleCode(), User.Role.MANAGER);
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
        return buildAuthResponse(user);
    }
    @Transactional
    public AuthSessionResult login(LoginRequest request) {
        Optional<User> userOpt = userRepository.findByUsername(request.getUsername());
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (user.isOssUser()) {
                return loginOssUser(user, request);
            }
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
            log.info("User logged in: {}", user.getUsername());
            return buildSession(user, null);
        }
        // 本地无记录 → 尝试 OSS 直接鉴权 + 自动创建本地记录
        // 根因修复：OSS 实时鉴权是唯一真相源，本地 DB 查不到时不应立即抛 UsernameNotFoundException
        Optional<User> ossUser = ossDirectLoginService.tryDirectLogin(request);
        if (ossUser.isEmpty()) {
            throw new UsernameNotFoundException(USER_NOT_FOUND);
        }
        log.info("User logged in via OSS direct login (auto-created): {}", ossUser.get().getUsername());
        return buildSession(ossUser.get(), null);
    }

    /** 构建 AuthSessionResult；logTag 非空时记 info 日志。 */
    private AuthSessionResult buildSession(User user, String logTag) {
        String token = jwtUtil.generateAccessToken(user.getUsername());
        String refreshToken = createRefreshSession(user);
        if (logTag != null) {
            log.info("User logged in via {}: {}", logTag, user.getUsername());
        }
        return AuthSessionResult.builder()
                .authResponse(buildAuthResponse(user))
                .refreshToken(refreshToken)
                .accessToken(token)
                .build();
    }
    /** OSS 用户登录：优先 OSS 认证；OSS 失败但本地密码匹配时走 fallback（跳过 requireOssRole）。 */
    private AuthSessionResult loginOssUser(User user, LoginRequest request) {
        if (!ossDelegationService.authenticate(user, request.getPassword())) {
            if (!isLocalPasswordValid(user, request.getPassword())) {
                throw new BadCredentialsException("Invalid username or password");
            }
            log.warn("OSS auth failed but local password valid for user={}, using local login", user.getUsername());
            return buildSession(user, "local password fallback (OSS auth failed)");
        }
        try {
            ossLoginFlowService.authenticateDirect(request.getUsername(), request.getPassword());
        } catch (RuntimeException e) {
            log.error("OSS permission sync FAILED for user={}, using stale cache. {}: {}",
                    user.getUsername(), e.getClass().getSimpleName(), e.getMessage());
        }
        return loginWithoutPassword(user);
    }

    /** OSS 同步用户 OSS 认证失败时的本地密码回退验证。 */
    private boolean isLocalPasswordValid(User user, String rawPassword) {
        String password = user.getPassword();
        if (password == null || password.isBlank()
                || password.equals(OrganizationUserSyncWriter.LOCKED_PASSWORD_HASH)) {
            return false;
        }
        try {
            return passwordEncoder.matches(rawPassword, password);
        } catch (IllegalArgumentException e) {
            log.warn("Local password validation failed for user: {}", user.getUsername());
            return false;
        }
    }
    @Transactional
    public AuthSessionResult loginWithoutPassword(User user) {
        ossDirectLoginService.requireOssRole(user);
        return buildSession(user, "SSO/WeCom");
    }

    public AuthResponse getCurrentUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(USER_NOT_FOUND));
        return buildAuthResponse(user);
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
        // 登出不清 OSS 权限缓存（CO-362: Redis 持久化，清了会导致 cache miss → 403 → 看板空）。
        // 安全由 revokeAccessToken + 撤销 refresh session 保证。下次登录 OssLoginFlowService 会覆盖刷新。
        revokeAccessToken(accessToken);
        // CO-152: 登出不清 CRM token 缓存，让 TTL 自然过期避免重复 generateToken
        if (refreshToken == null || refreshToken.isBlank()) return;
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
        ossDirectLoginService.requireOssRole(user);
        String accessToken = jwtUtil.generateAccessToken(user.getUsername());
        session.setRevokedAt(now);
        refreshSessionRepository.save(session);
        String rotatedRefreshToken = createRefreshSession(user);
        log.info("Token refreshed for user: {}", user.getUsername());
        return AuthSessionResult.builder()
                .authResponse(buildAuthResponse(user))
                .refreshToken(rotatedRefreshToken)
                .accessToken(accessToken)
                .build();
    }

    String hashTokenForTest(String token) {
        return hashToken(token);
    }

    /**
     * 构造 AuthResponse：角色信息优先从 OSS 权限缓存读取，缓存未命中用本地 DB 兜底。
     */
    private AuthResponse buildAuthResponse(User user) {
        return AuthResponse.from(null, user,
                projectAccessScopeService.getAllowedProjectIds(user),
                projectAccessScopeService.getAllowedDepartmentCodes(user),
                dataScopeConfigService.getRoleMenuPermissions(user),
                dataScopeConfigService.getRoleCode(user),
                dataScopeConfigService.getRoleName(user));
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
