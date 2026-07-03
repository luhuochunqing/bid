package com.xiyu.bid.config;

import com.xiyu.bid.auth.JwtUtil;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CO-478: 验证 {@link RateLimitFilter} 的限流 key 解析逻辑。
 * 核心断言：GET /api/* 请求优先按 JWT 用户名限流，无法提取时 fallback 到 IP。
 */
class RateLimitFilterTest {

    private RateLimitConfig.RateLimiter rateLimiter;
    private JwtUtil jwtUtil;
    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        rateLimiter = mock(RateLimitConfig.RateLimiter.class);
        jwtUtil = mock(JwtUtil.class);
        filter = new RateLimitFilter(rateLimiter, jwtUtil);

        // 设置默认限流参数
        ReflectionTestUtils.setField(filter, "maxLoginAttempts", 5);
        ReflectionTestUtils.setField(filter, "loginWindowMinutes", 15);
        ReflectionTestUtils.setField(filter, "defaultMaxRequests", 100);
        ReflectionTestUtils.setField(filter, "defaultWindowSeconds", 60);
        ReflectionTestUtils.setField(filter, "apiKeyMaxRequests", 1000);
        ReflectionTestUtils.setField(filter, "apiKeyWindowSeconds", 60);
        ReflectionTestUtils.setField(filter, "authAccountMaxAttempts", 5);
        ReflectionTestUtils.setField(filter, "authAccountWindowMinutes", 15);
        ReflectionTestUtils.setField(filter, "accessCookieName", "access_token");

        // 默认允许请求通过
        when(rateLimiter.allowRequest(anyString(), anyInt(), any(Duration.class))).thenReturn(true);
    }

    @Test
    void shouldUseUsernameAsRateLimitKeyWhenJwtCookiePresent() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects");
        request.setRemoteAddr("127.0.0.1");
        request.setCookies(new Cookie("access_token", "valid-jwt-token"));
        when(jwtUtil.extractUsername("valid-jwt-token")).thenReturn("admin");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // when
        filter.doFilter(request, response, chain);

        // then — 限流 key 应为 user:admin
        verify(rateLimiter).allowRequest(eq("user:admin"), eq(100), any(Duration.class));
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldUseUsernameAsRateLimitKeyWhenBearerTokenPresent() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/tasks");
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("Authorization", "Bearer my-bearer-token");
        when(jwtUtil.extractUsername("my-bearer-token")).thenReturn("xiaowang");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // when
        filter.doFilter(request, response, chain);

        // then — 限流 key 应为 user:xiaowang
        verify(rateLimiter).allowRequest(eq("user:xiaowang"), eq(100), any(Duration.class));
    }

    @Test
    void shouldFallbackToIpWhenNoJwtToken() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects");
        request.setRemoteAddr("192.168.1.100");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // when
        filter.doFilter(request, response, chain);

        // then — 无 token 时 fallback 到 IP
        verify(rateLimiter).allowRequest(eq("user:192.168.1.100"), eq(100), any(Duration.class));
    }

    @Test
    void shouldFallbackToIpWhenJwtTokenInvalid() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects");
        request.setRemoteAddr("172.16.38.78");
        request.addHeader("Authorization", "Bearer invalid-token");
        when(jwtUtil.extractUsername("invalid-token")).thenThrow(new MalformedJwtException("invalid token"));

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // when
        filter.doFilter(request, response, chain);

        // then — token 解析失败时 fallback 到 IP
        verify(rateLimiter).allowRequest(eq("user:172.16.38.78"), eq(100), any(Duration.class));
    }

    @Test
    void shouldFallbackToIpWhenJwtExtractsEmptyUsername() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects");
        request.setRemoteAddr("10.0.0.50");
        request.setCookies(new Cookie("access_token", "token-with-empty-subject"));
        when(jwtUtil.extractUsername("token-with-empty-subject")).thenReturn("");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // when
        filter.doFilter(request, response, chain);

        // then — username 为空时 fallback 到 IP
        verify(rateLimiter).allowRequest(eq("user:10.0.0.50"), eq(100), any(Duration.class));
    }

    @Test
    void shouldUseApiKeyAsRateLimitKeyWhenXApiKeyHeaderPresent() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects");
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-API-Key", "test-api-key-123");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // when
        filter.doFilter(request, response, chain);

        // then — API Key 限流不受 JWT 影响，走独立 key
        verify(rateLimiter).allowRequest(eq("api:" + com.xiyu.bid.util.DigestUtils.sha256("test-api-key-123").substring(0, 16)),
                eq(1000), any(Duration.class));
    }

    @Test
    void shouldUseIpAsRateLimitKeyForLoginEndpoint() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("10.0.0.1");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // when
        filter.doFilter(request, response, chain);

        // then — 登录端点仍按 IP 限流
        verify(rateLimiter).allowRequest(eq("login:10.0.0.1"), eq(5), any(Duration.class));
    }

    @Test
    void shouldReturn429WhenRateLimitExceeded() throws Exception {
        // given
        when(rateLimiter.allowRequest(anyString(), anyInt(), any(Duration.class))).thenReturn(false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects");
        request.setRemoteAddr("10.0.0.1");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // when
        filter.doFilter(request, response, chain);

        // then
        assertEquals(429, response.getStatus());
        assertEquals("application/json", response.getContentType());
    }

    @Test
    void shouldUseXForwardedForIpWhenPresent() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects");
        request.addHeader("X-Forwarded-For", "203.0.113.50, 10.0.0.1");
        // 无 JWT → fallback to IP，应使用 X-Forwarded-For 的第一个 IP

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // when
        filter.doFilter(request, response, chain);

        // then — 应提取 X-Forwarded-For 第一个 IP 作为限流 key
        verify(rateLimiter).allowRequest(eq("user:203.0.113.50"), eq(100), any(Duration.class));
    }

    @Test
    void shouldPreferCookieOverBearerHeader() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects");
        request.setRemoteAddr("10.0.0.1");
        request.setCookies(new Cookie("access_token", "cookie-token"));
        request.addHeader("Authorization", "Bearer header-token");
        when(jwtUtil.extractUsername("cookie-token")).thenReturn("cookie-user");
        // header-token 不应被调用
        when(jwtUtil.extractUsername("header-token")).thenReturn("header-user");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // when
        filter.doFilter(request, response, chain);

        // then — cookie 优先
        verify(rateLimiter).allowRequest(eq("user:cookie-user"), eq(100), any(Duration.class));
        verify(jwtUtil, times(0)).extractUsername("header-token");
    }
}
