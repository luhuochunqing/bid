package com.xiyu.bid.integration.controller;

import com.xiyu.bid.auth.OAuthStateService;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.dto.AuthSessionResult;
import com.xiyu.bid.integration.application.WeComAuthAppService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Controller to handle WeCom OAuth2 authentication requests.
 */
@Slf4j
@RestController
@RequestMapping("/api/auth/wecom")
@RequiredArgsConstructor
public class WeComAuthController {

    /** Error code for unbound WeCom account. */
    private static final int ERR_NOT_BOUND = 40101;

    /** Internal business logic for WeCom auth. */
    private final WeComAuthAppService weComAuthAppService;

    /** State management for CSRF protection. */
    private final OAuthStateService oAuthStateService;

    /** Cookie name for refresh token. */
    @Value("${app.auth.refresh-cookie-name:refresh_token}")
    private String refreshCookieName;

    /** Whether refresh cookie should be secure. */
    @Value("${app.auth.refresh-cookie-secure:false}")
    private boolean refreshCookieSecure;

    /** SameSite attribute for refresh cookie. */
    @Value("${app.auth.refresh-cookie-same-site:Lax}")
    private String refreshCookieSameSite;

    /** Expiration time for refresh token in ms. */
    @Value("${jwt.refresh-expiration:604800000}")
    private long refreshExpiration;

    /**
     * Entry point to get the WeCom login parameters.
     *
     * @return ResponseEntity with appid, agentid and state
     */
    @GetMapping("/authorize-params")
    public ResponseEntity<ApiResponse<Map<String, String>>>
    getAuthorizeParams() {
        String state = UUID.randomUUID().toString().replace("-", "");
        oAuthStateService.storeState(state);

        Map<String, String> params =
                weComAuthAppService.getAuthorizeParams(state);
        return ResponseEntity.ok(ApiResponse.success(
                "Auth params generated", params));
    }

    /**
     * OAuth2 callback endpoint.
     *
     * @param code WeCom OAuth2 code
     * @param state CSRF state token
     * @param response HttpServletResponse to set cookies
     * @return ResponseEntity with AuthResponse
     */
    @GetMapping("/callback")
    public ResponseEntity<ApiResponse<?>> callback(
            @RequestParam("code")
            final String code,
            @RequestParam("state")
            final String state,
            final HttpServletResponse response
    ) {
        log.info("Received WeCom OAuth2 callback: code={}, state={}",
                code, state);

        // 1. Validate state (CSRF protection)
        if (!oAuthStateService.validateAndRemoveState(state)) {
            log.warn("OAuth2 callback state validation failed: {}", state);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error(HttpStatus.FORBIDDEN.value(),
                            "INVALID_STATE"));
        }

        // 2. Perform login
        var loginResultOpt =
                weComAuthAppService.loginByWeCom(code);

        if (loginResultOpt.isPresent()) {
            AuthSessionResult result = loginResultOpt.get();
            ResponseCookie cookie = buildRefreshCookie(
                    result.getRefreshToken(), true);
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cookie.toString())
                    .body(ApiResponse.success("WeCom login successful",
                            result.getAuthResponse()));
        } else {
            // User not found, return specific status for binding
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(ERR_NOT_BOUND, "WECOM_NOT_BOUND"));
        }
    }

    private ResponseCookie buildRefreshCookie(final String refreshToken,
                                              final boolean persistent) {
        ResponseCookie.ResponseCookieBuilder builder =
                ResponseCookie.from(refreshCookieName, refreshToken)
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite(refreshCookieSameSite)
                .path("/");

        if (persistent) {
            builder.maxAge(Duration.ofMillis(refreshExpiration));
        }

        return builder.build();
    }
}
