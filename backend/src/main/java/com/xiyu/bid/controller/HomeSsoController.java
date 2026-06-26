// Input: HTTP 请求、SSO token
// Output: 标准化 API 响应和用例入口
// Pos: Controller/接口适配层
// 维护声明: 仅维护 Home SSO 协议适配与参数校验；业务规则下沉到 service.
package com.xiyu.bid.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.dto.AuthResponse;
import com.xiyu.bid.dto.AuthSessionResult;
import com.xiyu.bid.crm.application.HomeSsoService;
import com.xiyu.bid.util.InputSanitizer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
public class HomeSsoController {

    private static final String PERMIT_ALL_EXPR = "permitAll()";

    private final HomeSsoService homeSsoService;

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

    @PostMapping("/home-sso")
    @PreAuthorize(PERMIT_ALL_EXPR)
    public ResponseEntity<ApiResponse<AuthResponse>> homeSsoLogin(@Valid @RequestBody HomeSsoRequest request) {
        String sanitizedToken = InputSanitizer.sanitizeString(request.ssoToken(), 512);
        AuthSessionResult sessionResult = homeSsoService.ssoLogin(sanitizedToken);
        log.info("Auth home-sso success");
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(sessionResult.getRefreshToken(), true).toString())
                .header(HttpHeaders.SET_COOKIE, buildAccessCookie(sessionResult.getAccessToken()).toString())
                .body(ApiResponse.success("SSO login successful", sessionResult.getAuthResponse()));
    }

    record HomeSsoRequest(@NotBlank String ssoToken) {}

    private ResponseCookie buildRefreshCookie(String token, boolean persistent) {
        return ResponseCookie.from(refreshCookieName, token)
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite(refreshCookieSameSite)
                .path("/")
                .maxAge(persistent ? Duration.ofMillis(refreshExpiration) : Duration.ofMillis(-1))
                .build();
    }

    private ResponseCookie buildAccessCookie(String accessToken) {
        return ResponseCookie.from(accessCookieName, accessToken)
                .httpOnly(true)
                .secure(accessCookieSecure)
                .sameSite(accessCookieSameSite)
                .path("/")
                .maxAge(Duration.ofMillis(accessExpiration))
                .build();
    }
}
