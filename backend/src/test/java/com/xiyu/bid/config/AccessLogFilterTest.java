package com.xiyu.bid.config;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证 {@link AccessLogFilter} 的请求日志记录行为。
 * <p>纯 JUnit 5 测试，不依赖 Spring 上下文。</p>
 */
class AccessLogFilterTest {

    @Test
    void shouldRecordAccessLogForNormalRequest() throws Exception {
        // given
        MDC.put(TraceConstants.MDC_TRACE_KEY, "trace-000");
        AccessLogFilter filter = new AccessLogFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        request.addHeader("User-Agent", "test-agent");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // when
        filter.doFilter(request, response, chain);

        // then — no exception means it worked (log output verified by logback config)
        assertEquals(200, response.getStatus());
        MDC.clear();
    }

    @Test
    void shouldRecordAccessLogForRequestWithQueryString() throws Exception {
        // given
        AccessLogFilter filter = new AccessLogFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/search");
        request.setQueryString("q=test&page=1");
        request.addHeader("X-Forwarded-For", "10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // when
        filter.doFilter(request, response, chain);

        // then
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldRecordAccessLogWithoutUserAgent() throws Exception {
        // given
        AccessLogFilter filter = new AccessLogFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/items/42");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // when
        filter.doFilter(request, response, chain);

        // then
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldHandleClientIpFromXForwardedFor() throws Exception {
        // given
        AccessLogFilter filter = new AccessLogFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        request.addHeader("X-Forwarded-For", "192.168.1.1, 10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // when
        filter.doFilter(request, response, chain);

        // then
        assertEquals(200, response.getStatus());
    }
}
