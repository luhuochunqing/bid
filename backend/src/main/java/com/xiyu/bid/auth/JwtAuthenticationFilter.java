// Input: HTTP 请求头、JWT 令牌和安全上下文
// Output: 已认证用户上下文或未认证放行结果
// Pos: Auth/认证过滤层
// 维护声明: 仅维护令牌解析与过滤逻辑；认证规则变更请同步 AuthService 和 SecurityConfig.
package com.xiyu.bid.auth;

import com.xiyu.bid.config.TraceConstants;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.security.EffectiveRoleResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
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
    private final UserRepository userRepository;
    private final EffectiveRoleResolver effectiveRoleResolver;

    // H13 根治 (2026-06-14): access token 优先从 HttpOnly cookie 读
    @org.springframework.beans.factory.annotation.Value("${app.auth.access-cookie-name:access_token}")
    private String accessCookieName;

    @org.springframework.beans.factory.annotation.Autowired
    public JwtAuthenticationFilter(
            JwtUtil jwtUtil,
            UserDetailsServiceImpl userDetailsService,
            ObjectProvider<TokenRevocationService> tokenRevocationServiceProvider,
            UserRepository userRepository,
            EffectiveRoleResolver effectiveRoleResolver
    ) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.tokenRevocationServiceProvider = tokenRevocationServiceProvider;
        this.userRepository = userRepository;
        this.effectiveRoleResolver = effectiveRoleResolver;
    }

    // Test-only convenience constructor
    JwtAuthenticationFilter(
            JwtUtil jwtUtil,
            UserDetailsServiceImpl userDetailsService,
            TokenRevocationService tokenRevocationService,
            UserRepository userRepository,
            EffectiveRoleResolver effectiveRoleResolver
    ) {
        this(jwtUtil, userDetailsService, new SimpleProvider<>(tokenRevocationService),
                userRepository, effectiveRoleResolver);
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
                        refreshMdcContext(username);
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
                    refreshMdcContext(username);
                }
            }
        } catch (Exception e) {
            log.error("Cannot set user authentication: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 刷新 MDC 用户上下文（userId + roleCode）。
     *
     * <p>在 {@code setAuthentication} 之后立即调用，覆盖 {@link com.xiyu.bid.config.TraceFilter}
     * 在 filterChain 之前写入的 anonymous 兜底值，使后续业务日志能正确携带用户身份。</p>
     *
     * <p>角色码读取走 {@link EffectiveRoleResolver#resolveRoleCode} 统一入口（CO-373 治理），
     * OSS 用户以缓存角色码为准，缓存未命中时 fail-closed 返回 null。</p>
     *
     * @param username 已认证用户名（来自 JWT）
     */
    private void refreshMdcContext(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        User user = userRepository.findByUsername(username).orElse(null);
        if (user != null) {
            MDC.put(TraceConstants.MDC_USER_ID_KEY, String.valueOf(user.getId()));
            MDC.put(TraceConstants.MDC_ROLE_CODE_KEY, effectiveRoleResolver.resolveRoleCode(user));
        }
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
