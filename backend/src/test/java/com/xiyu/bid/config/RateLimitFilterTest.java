package com.xiyu.bid.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.auth.JwtUtil;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitFilterTest {

    private static final String LOGGER_NAME = "com.xiyu.bid.config.RateLimitFilter";

    private RateLimitConfig.RateLimiter rateLimiter;
    private JwtUtil jwtUtil;
    private RateLimitFilter filter;
    private ObjectMapper objectMapper;
    private ListAppender<ILoggingEvent> logAppender;
    private Logger rateLimitLogger;

    @BeforeEach
    void setUp() {
        rateLimiter = mock(RateLimitConfig.RateLimiter.class);
        jwtUtil = mock(JwtUtil.class);
        filter = new RateLimitFilter(rateLimiter, jwtUtil);
        objectMapper = new ObjectMapper();
        ReflectionTestUtils.setField(filter, "maxLoginAttempts", 5);
        ReflectionTestUtils.setField(filter, "loginWindowMinutes", 15);
        ReflectionTestUtils.setField(filter, "defaultMaxRequests", 100);
        ReflectionTestUtils.setField(filter, "defaultWindowSeconds", 60);
        ReflectionTestUtils.setField(filter, "apiKeyMaxRequests", 1000);
        ReflectionTestUtils.setField(filter, "apiKeyWindowSeconds", 60);
        ReflectionTestUtils.setField(filter, "authAccountMaxAttempts", 5);
        ReflectionTestUtils.setField(filter, "authAccountWindowMinutes", 15);
        ReflectionTestUtils.setField(filter, "accessCookieName", "access_token");
        when(rateLimiter.allowRequest(anyString(), anyInt(), any(Duration.class))).thenReturn(true);
        rateLimitLogger = (Logger) LoggerFactory.getLogger(LOGGER_NAME);
        rateLimitLogger.setLevel(Level.DEBUG);
        logAppender = new ListAppender<>();
        logAppender.start();
        rateLimitLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        rateLimitLogger.detachAppender(logAppender);
        logAppender.stop();
    }

    @Test
    void shouldUseUsernameAsRateLimitKeyWhenJwtCookiePresent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects");
        request.setRemoteAddr("127.0.0.1");
        request.setCookies(new Cookie("access_token", "valid-jwt-token"));
        when(jwtUtil.extractUsername("valid-jwt-token")).thenReturn("admin");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        verify(rateLimiter).allowRequest(eq("user:admin"), eq(100), any(Duration.class));
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldUseUsernameAsRateLimitKeyWhenBearerTokenPresent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/tasks");
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("Authorization", "Bearer my-bearer-token");
        when(jwtUtil.extractUsername("my-bearer-token")).thenReturn("xiaowang");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        verify(rateLimiter).allowRequest(eq("user:xiaowang"), eq(100), any(Duration.class));
    }

    @Test
    void shouldFallbackToIpWhenNoJwtToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects");
        request.setRemoteAddr("192.168.1.100");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        verify(rateLimiter).allowRequest(eq("user:192.168.1.100"), eq(100), any(Duration.class));
    }

    @Test
    void shouldFallbackToIpWhenJwtTokenInvalid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects");
        request.setRemoteAddr("172.16.38.78");
        request.addHeader("Authorization", "Bearer invalid-token");
        when(jwtUtil.extractUsername("invalid-token")).thenThrow(new MalformedJwtException("invalid token"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        verify(rateLimiter).allowRequest(eq("user:172.16.38.78"), eq(100), any(Duration.class));
    }

    @Test
    void shouldFallbackToIpWhenJwtExtractsEmptyUsername() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects");
        request.setRemoteAddr("10.0.0.50");
        request.setCookies(new Cookie("access_token", "token-with-empty-subject"));
        when(jwtUtil.extractUsername("token-with-empty-subject")).thenReturn("");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        verify(rateLimiter).allowRequest(eq("user:10.0.0.50"), eq(100), any(Duration.class));
    }

    @Test
    void shouldUseApiKeyAsRateLimitKeyWhenXApiKeyHeaderPresent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects");
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-API-Key", "test-api-key-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        verify(rateLimiter).allowRequest(eq("api:" + com.xiyu.bid.util.DigestUtils.sha256("test-api-key-123").substring(0, 16)),
                eq(1000), any(Duration.class));
    }

    @Test
    void shouldUseIpAsRateLimitKeyForLoginEndpoint() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        verify(rateLimiter).allowRequest(eq("login:10.0.0.1"), eq(5), any(Duration.class));
    }

    @Test
    void shouldReturn429WhenRateLimitExceeded() throws Exception {
        when(rateLimiter.allowRequest(anyString(), anyInt(), any(Duration.class))).thenReturn(false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects");
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertEquals(429, response.getStatus());
        assertNotNull(response.getContentType());
        assertTrue(response.getContentType().startsWith("application/json"));
    }

    @Test
    void shouldReturnApiResponseWithChineseMessageWhenRateLimitExceeded() throws Exception {
        when(rateLimiter.allowRequest(anyString(), anyInt(), any(Duration.class))).thenReturn(false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects");
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertFalse(body.get("success").asBoolean());
        assertEquals(429, body.get("code").asInt());
        assertNotNull(body.get("msg"));
        assertTrue(body.get("msg").asText().contains("操作太快了"));
        assertFalse(body.get("msg").asText().contains("Too many requests"));
        assertFalse(body.get("msg").asText().contains("rate_limit_exceeded"));
    }

    @Test
    void shouldIncludeRetryAfterHeaderWhenRateLimitExceeded() throws Exception {
        when(rateLimiter.allowRequest(anyString(), anyInt(), any(Duration.class))).thenReturn(false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects");
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        String retryAfter = response.getHeader("Retry-After");
        assertNotNull(retryAfter);
        int retryAfterSeconds = Integer.parseInt(retryAfter);
        assertTrue(retryAfterSeconds >= 1 && retryAfterSeconds <= 60,
                "Retry-After 应在 1~60 秒之间，实际为 " + retryAfterSeconds);
    }

    @Test
    void shouldLogKeyPathAndRetryAfterWhenRateLimitExceeded() throws Exception {
        when(rateLimiter.allowRequest(anyString(), anyInt(), any(Duration.class))).thenReturn(false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects");
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        String logOutput = logAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (a, b) -> a + "\n" + b);
        assertTrue(logOutput.contains("Rate limit exceeded"));
        assertTrue(logOutput.contains("key=user:10.0.0.1"));
        assertTrue(logOutput.contains("path=/api/projects"));
        assertTrue(logOutput.contains("retryAfter="));
        assertTrue(logAppender.list.stream().anyMatch(event -> event.getLevel().equals(Level.WARN)));
    }

    @Test
    void shouldUseXForwardedForIpWhenPresent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects");
        request.addHeader("X-Forwarded-For", "203.0.113.50, 10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        verify(rateLimiter).allowRequest(eq("user:203.0.113.50"), eq(100), any(Duration.class));
    }

    @Test
    void shouldPreferCookieOverBearerHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects");
        request.setRemoteAddr("10.0.0.1");
        request.setCookies(new Cookie("access_token", "cookie-token"));
        request.addHeader("Authorization", "Bearer header-token");
        when(jwtUtil.extractUsername("cookie-token")).thenReturn("cookie-user");
        when(jwtUtil.extractUsername("header-token")).thenReturn("header-user");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        verify(rateLimiter).allowRequest(eq("user:cookie-user"), eq(100), any(Duration.class));
        verify(jwtUtil, times(0)).extractUsername("header-token");
    }
}
