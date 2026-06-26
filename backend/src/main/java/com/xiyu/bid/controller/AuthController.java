// Input: HTTP 请求、路径参数、认证上下文和 DTO
// Output: 标准化 API 响应和用例入口
// Pos: Controller/接口适配层
// 维护声明: 仅维护协议适配与参数校验；业务规则下沉到 service.
package com.xiyu.bid.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.dto.AuthResponse;
import com.xiyu.bid.dto.AuthSessionResult;
import com.xiyu.bid.dto.EmailVerificationResponse;
import com.xiyu.bid.dto.ForgotPasswordRequest;
import com.xiyu.bid.dto.LoginRequest;
import com.xiyu.bid.dto.PasswordResetResponse;
import com.xiyu.bid.dto.RegisterRequest;
import com.xiyu.bid.dto.ResetPasswordRequest;
import com.xiyu.bid.dto.SessionDTO;
import com.xiyu.bid.service.AuthService;
import com.xiyu.bid.service.EmailVerificationService;
import com.xiyu.bid.service.PasswordResetService;
import com.xiyu.bid.service.SessionService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import com.xiyu.bid.util.InputSanitizer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AuthController {

    private static final String PERMIT_ALL_EXPR = "permitAll()";

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final SessionService sessionService;
    private final EmailVerificationService emailVerificationService;

    @Value("${app.auth.refresh-cookie-name:refresh_token}")
    private String refreshCookieName;

    @Value("${app.auth.refresh-cookie-secure:false}")
    private boolean refreshCookieSecure;

    @Value("${app.auth.refresh-cookie-same-site:Lax}")
    private String refreshCookieSameSite;

    @Value("${jwt.refresh-expiration:604800000}")
    private long refreshExpiration;

    @Value("${app.auth.access-cookie-name:access_token}")
    private String accessCookieName;

    @Value("${app.auth.access-cookie-secure:false}")
    private boolean accessCookieSecure;

    @Value("${app.auth.access-cookie-same-site:Lax}")
    private String accessCookieSameSite;

    @Value("${jwt.expiration:86400000}")
    private long accessExpiration;

    @PostMapping("/register")
    @PreAuthorize(PERMIT_ALL_EXPR)
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        sanitizeRegisterRequest(request);
        AuthResponse response = authService.register(request);
        log.info("Auth register success: user={}, emailDomain={}", usernameFingerprint(request.getUsername()), emailDomain(request.getEmail()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("User registered successfully", response));
    }

    @PostMapping("/login")
    @PreAuthorize(PERMIT_ALL_EXPR)
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        String username = request.getUsername();
        String userFp = usernameFingerprint(username);
        if (username != null) request.setUsername(InputSanitizer.sanitizeString(username, 50));
        if (request.getPassword() != null) request.setPassword(request.getPassword().trim());
        try {
            AuthSessionResult sessionResult = authService.login(request);
            log.info("Auth login success: user={}, rememberMe={}", userFp, request.getRememberMe());
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(sessionResult.getRefreshToken(), Boolean.TRUE.equals(request.getRememberMe())).toString())
                    .header(HttpHeaders.SET_COOKIE, buildAccessCookie(sessionResult.getAccessToken()).toString())
                    .body(ApiResponse.success("Login successful", sessionResult.getAuthResponse()));
        } catch (RuntimeException ex) {
            log.warn("Auth login failed: user={}, reason={}", userFp, ex.getMessage());
            throw ex;
        }
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AuthResponse>> getCurrentUser(Authentication authentication) {
        AuthResponse response = authService.getCurrentUser(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Current user retrieved successfully", response));
    }

    @PostMapping("/logout")
    @PreAuthorize(PERMIT_ALL_EXPR)
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request) {
        String accessToken = extractAccessToken(request);
        String refreshToken = extractRefreshToken(request);
        authService.logout(accessToken, refreshToken);
        if (refreshToken != null) {
            log.info("Auth logout: refreshTokenPresent=true");
        } else {
            log.info("Auth logout: refreshTokenPresent=false");
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
                .header(HttpHeaders.SET_COOKIE, clearAccessCookie().toString())
                .body(ApiResponse.success("Logout successful", null));
    }

    private String extractAccessToken(HttpServletRequest request) {
        String cookieToken = extractCookie(request, accessCookieName);
        if (cookieToken != null) {
            return cookieToken;
        }
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null) {
            return null;
        }
        String trimmed = header.trim();
        if (trimmed.startsWith("Bearer ")) {
            return trimmed.substring(7).trim();
        }
        return null;
    }

    @PostMapping("/refresh")
    @PreAuthorize(PERMIT_ALL_EXPR)
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(HttpServletRequest request) {
        String refreshToken = extractRefreshToken(request);
        if (refreshToken == null) {
            log.warn("Auth refresh: missing refresh token");
        } else {
            log.info("Auth refresh: tokenPresent=true");
        }
        AuthSessionResult sessionResult = authService.refreshToken(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(sessionResult.getRefreshToken(), true).toString())
                .header(HttpHeaders.SET_COOKIE, buildAccessCookie(sessionResult.getAccessToken()).toString())
                .body(ApiResponse.success("Token refreshed successfully", sessionResult.getAuthResponse()));
    }

    @PostMapping("/forgot-password")
    @PreAuthorize(PERMIT_ALL_EXPR)
    public ResponseEntity<ApiResponse<PasswordResetResponse>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request
    ) {
        String sanitizedEmail = InputSanitizer.sanitizeString(request.email(), 100);
        String result = passwordResetService.createPasswordResetToken(sanitizedEmail);
        log.info("Auth forgot-password: email={}, tokenGenerated={}", sanitizedEmail, result != null);
        PasswordResetResponse response = new PasswordResetResponse(
                "If an account exists with this email, a password reset link has been sent",
                result);
        return ResponseEntity.ok(ApiResponse.success("Password reset initiated", response));
    }

    @PostMapping("/reset-password")
    @PreAuthorize(PERMIT_ALL_EXPR)
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        passwordResetService.resetPassword(request.token(), request.newPassword());
        log.info("Auth reset-password: tokenProvided={}", request.token() != null);
        return ResponseEntity.ok(ApiResponse.success("Password has been reset successfully", null));
    }

    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<java.util.List<SessionDTO>>> getSessions(Authentication authentication) {
        Long userId = authService.resolveUserIdByUsername(authentication.getName());
        java.util.List<SessionDTO> sessions = sessionService.getUserSessions(userId);
        log.info("Auth getSessions: userId={}, sessionCount={}", userId, sessions.size());
        return ResponseEntity.ok(ApiResponse.success("Sessions retrieved", sessions));
    }

    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<ApiResponse<Void>> revokeSession(
            @PathVariable Long id,
            Authentication authentication
    ) {
        Long userId = authService.resolveUserIdByUsername(authentication.getName());
        sessionService.revokeSession(id, userId);
        log.info("Auth revokeSession: userId={}, sessionId={}", userId, id);
        return ResponseEntity.ok(ApiResponse.success("Session revoked", null));
    }

    @DeleteMapping("/sessions")
    public ResponseEntity<ApiResponse<Void>> revokeAllSessions(Authentication authentication, HttpServletRequest request) {
        Long userId = authService.resolveUserIdByUsername(authentication.getName());
        sessionService.revokeAllSessions(userId, extractRefreshToken(request));
        log.info("Auth revokeAllSessions: userId={}", userId);
        return ResponseEntity.ok(ApiResponse.success("All sessions revoked", null));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse<EmailVerificationResponse>> requestEmailVerification(Authentication authentication) {
        Long userId = authService.resolveUserIdByUsername(authentication.getName());
        String result = emailVerificationService.createVerificationToken(userId);
        log.info("Auth requestEmailVerification: userId={}", userId);
        return ResponseEntity.ok(ApiResponse.success("Verification email sent", new EmailVerificationResponse(result)));
    }

    @GetMapping("/verify-email/{token}")
    @PreAuthorize(PERMIT_ALL_EXPR)
    public ResponseEntity<ApiResponse<Void>> verifyEmail(@PathVariable String token) {
        String sanitizedToken = InputSanitizer.sanitizeString(token, 128);
        log.info("Auth verifyEmail: tokenPrefix={}",
                sanitizedToken != null && sanitizedToken.length() > 8
                        ? sanitizedToken.substring(0, 8) + "***" : "***");
        emailVerificationService.verifyEmail(sanitizedToken);
        return ResponseEntity.ok(ApiResponse.success("Email verified successfully", null));
    }

    private void sanitizeRegisterRequest(RegisterRequest request) {
        if (request.getUsername() != null)
            request.setUsername(InputSanitizer.sanitizeString(request.getUsername(), 50));
        if (request.getEmail() != null)
            request.setEmail(InputSanitizer.sanitizeString(request.getEmail(), 100));
        if (request.getFullName() != null)
            request.setFullName(InputSanitizer.sanitizeString(request.getFullName(), 100));
    }

    /** username 指纹 (前 2 字符+长度+4 位 hash) / email 域部分 (避免 PII 直存)。 */
    private static String usernameFingerprint(String username) {
        if (username == null || username.isEmpty()) return "<empty>";
        int len = username.length();
        String prefix = len > 2 ? username.substring(0, 2) : username;
        return String.format("%s*len%d#%04d", prefix, len, Math.abs(username.hashCode()) % 10000);
    }

    private static String emailDomain(String email) {
        if (email == null) return "<empty>";
        int at = email.indexOf('@');
        return at >= 0 && at < email.length() - 1 ? email.substring(at) : "<invalid>";
    }

    private String extractRefreshToken(HttpServletRequest request) {
        return extractCookie(request, refreshCookieName);
    }

    private String extractCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private ResponseCookie buildRefreshCookie(String token, boolean persistent) {
        ResponseCookie.ResponseCookieBuilder b = ResponseCookie.from(refreshCookieName, token)
                .httpOnly(true).secure(refreshCookieSecure).sameSite(refreshCookieSameSite).path("/");
        if (persistent) b.maxAge(Duration.ofMillis(refreshExpiration));
        return b.build();
    }

    private ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from(refreshCookieName, "")
                .httpOnly(true).secure(refreshCookieSecure).sameSite(refreshCookieSameSite)
                .path("/").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie buildAccessCookie(String accessToken) {
        return ResponseCookie.from(accessCookieName, accessToken == null ? "" : accessToken)
                .httpOnly(true).secure(accessCookieSecure).sameSite(accessCookieSameSite)
                .path("/").maxAge(Duration.ofMillis(accessExpiration)).build();
    }

    private ResponseCookie clearAccessCookie() {
        return ResponseCookie.from(accessCookieName, "")
                .httpOnly(true).secure(accessCookieSecure).sameSite(accessCookieSameSite)
                .path("/").maxAge(Duration.ZERO).build();
    }
}
