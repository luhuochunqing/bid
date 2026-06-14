package com.xiyu.bid.config;

import com.xiyu.bid.util.DigestUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 全局限流过滤器。
 * 覆盖登录限流 + API 数据端点限流（含 X-RateLimit-* 响应头）。
 */
@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitConfig.RateLimiter rateLimiter;

    @Value("${rate.limit.login.max-attempts:5}")
    private int maxLoginAttempts;

    @Value("${rate.limit.login.window-minutes:15}")
    private int loginWindowMinutes;

    @Value("${rate.limit.default.max:100}")
    private int defaultMaxRequests;

    @Value("${rate.limit.default.window-seconds:60}")
    private int defaultWindowSeconds;

    @Value("${rate.limit.api-key.max:1000}")
    private int apiKeyMaxRequests;

    @Value("${rate.limit.api-key.window-seconds:60}")
    private int apiKeyWindowSeconds;

    @Value("${rate.limit.auth-account.max:5}")
    private int authAccountMaxAttempts;

    @Value("${rate.limit.auth-account.window-minutes:15}")
    private int authAccountWindowMinutes;

    public RateLimitFilter(final RateLimitConfig.RateLimiter pRateLimiter) {
        this.rateLimiter = pRateLimiter;
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                     final HttpServletResponse response,
                                     final FilterChain chain)
            throws ServletException, IOException {

        String requestURI = request.getRequestURI();
        String method = request.getMethod();
        String clientIp = getClientIp(request);

        if ("/api/auth/login".equals(requestURI) && "POST".equalsIgnoreCase(method)) {
            applyRateLimit(response, chain, request, "login:" + clientIp,
                    maxLoginAttempts, Duration.ofMinutes(loginWindowMinutes), false);
            return;
        }

        // H3 扩展：对账号类敏感端点统一限流（防止枚举/撞库/垃圾注册）。
        if ("POST".equalsIgnoreCase(method) && isAccountResetOrRegisterEndpoint(requestURI)) {
            applyRateLimit(response, chain, request, "auth-account:" + clientIp,
                    authAccountMaxAttempts, Duration.ofMinutes(authAccountWindowMinutes), false);
            return;
        }

        if (requestURI.startsWith("/api/") && "GET".equalsIgnoreCase(method)) {
            String apiKey = request.getHeader("X-API-Key");
            String rateLimitApiKey = apiKey != null ? DigestUtils.sha256(apiKey).substring(0, 16) : "anonymous";
            if (apiKey != null && !apiKey.isEmpty()) {
                applyRateLimit(response, chain, request, "api:" + rateLimitApiKey,
                        apiKeyMaxRequests, Duration.ofSeconds(apiKeyWindowSeconds), true);
                return;
            }
            applyRateLimit(response, chain, request, "user:" + clientIp,
                    defaultMaxRequests, Duration.ofSeconds(defaultWindowSeconds), true);
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * H3 扩展：账号相关敏感端点判定。集中维护避免散落。
     */
    private static boolean isAccountResetOrRegisterEndpoint(final String requestURI) {
        if (requestURI == null) {
            return false;
        }
        return "/api/auth/forgot-password".equals(requestURI)
                || "/api/auth/register".equals(requestURI)
                || "/api/auth/reset-password".equals(requestURI)
                || "/api/auth/verify-email".equals(requestURI);
    }

    private void applyRateLimit(final HttpServletResponse response, final FilterChain chain,
                               final HttpServletRequest request, final String key,
                               final int maxRequests, final Duration window, final boolean addHeaders)
            throws IOException, ServletException {
        boolean allowed = rateLimiter.allowRequest(key, maxRequests, window);
        if (addHeaders) {
            response.setHeader("X-RateLimit-Limit", String.valueOf(maxRequests));
            response.setHeader("X-RateLimit-Reset",
                    String.valueOf(System.currentTimeMillis() / 1000 + window.getSeconds()));
        }
        if (!allowed) {
            log.warn("Rate limit exceeded for key: {}", key);
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\":{\"code\":\"rate_limit_exceeded\","
                    + "\"message\":\"Too many requests. Please try again later.\"}}");
            return;
        }
        chain.doFilter(request, response);
    }

    private String getClientIp(final HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
