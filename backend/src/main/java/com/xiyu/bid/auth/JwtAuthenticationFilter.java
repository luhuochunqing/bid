// Input: HTTP 请求头、JWT 令牌和安全上下文
// Output: 已认证用户上下文或未认证放行结果
// Pos: Auth/认证过滤层
// 维护声明: 仅维护令牌解析与过滤逻辑；认证规则变更请同步 AuthService 和 SecurityConfig.
package com.xiyu.bid.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;
    private final ObjectProvider<TokenRevocationService> tokenRevocationServiceProvider;

    // H13 根治 (2026-06-14): access token 优先从 HttpOnly cookie 读
    @org.springframework.beans.factory.annotation.Value("${app.auth.access-cookie-name:access_token}")
    private String accessCookieName;

    @org.springframework.beans.factory.annotation.Autowired
    public JwtAuthenticationFilter(
            JwtUtil jwtUtil,
            UserDetailsServiceImpl userDetailsService,
            ObjectProvider<TokenRevocationService> tokenRevocationServiceProvider
    ) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.tokenRevocationServiceProvider = tokenRevocationServiceProvider;
    }

    // Test-only convenience constructor
    JwtAuthenticationFilter(
            JwtUtil jwtUtil,
            UserDetailsServiceImpl userDetailsService,
            TokenRevocationService tokenRevocationService
    ) {
        this(jwtUtil, userDetailsService, new SimpleProvider<>(tokenRevocationService));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            if (hasApiKeyAuthentication()) {
                filterChain.doFilter(request, response);
                return;
            }
            String jwt = extractJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && jwtUtil.validateToken(jwt, jwtUtil.extractUsername(jwt))) {
                TokenRevocationService revocation = tokenRevocationServiceProvider.getIfAvailable();
                if (revocation != null) {
                    String jti = jwtUtil.extractJti(jwt).orElse(null);
                    if (jti != null && revocation.isRevoked(jti)) {
                        log.debug("Rejecting revoked JWT (jti={})", jti);
                    } else {
                        String username = jwtUtil.extractUsername(jwt);
                        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                        userDetails,
                                        null,
                                        userDetails.getAuthorities()
                                );
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                        log.debug("Set authentication for user: {}", username);
                    }
                } else {
                    String username = jwtUtil.extractUsername(jwt);
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    log.debug("Set authentication for user: {}", username);
                }
            }
        } catch (Exception e) {
            log.error("Cannot set user authentication: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private boolean hasApiKeyAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() != null
                && authentication.getPrincipal().toString().startsWith("api-key:");
    }

    private String extractJwtFromRequest(HttpServletRequest request) {
        // H13 根治 (2026-06-14): 优先从 HttpOnly access cookie 读 (XSS 不可达);
        // fallback Authorization Bearer header 兼容 E2E 浏览器外调用 / 旧客户端
        String cookieToken = extractAccessTokenFromCookie(request);
        if (StringUtils.hasText(cookieToken)) {
            return cookieToken;
        }
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private String extractAccessTokenFromCookie(HttpServletRequest request) {
        jakarta.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (jakarta.servlet.http.Cookie cookie : cookies) {
            if (accessCookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private static final class SimpleProvider<T> implements ObjectProvider<T> {
        private final T value;

        SimpleProvider(T value) {
            this.value = value;
        }

        @Override public T getObject() { return value; }
        @Override public T getObject(Object... args) { return value; }
        @Override public T getIfAvailable() { return value; }
        @Override public T getIfUnique() { return value; }
    }
}
