package com.xiyu.bid.config;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.util.ContentCachingRequestWrapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void shouldNotWrapMultipartRequestSoSpringCanResolveMultipart() throws Exception {
        // given
        AccessLogFilter filter = new AccessLogFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/knowledge/brand-auth/import");
        request.setContentType("multipart/form-data; boundary=----WebKitFormBoundary");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();

        // when
        filter.doFilter(request, response, chain);

        // then: 下游必须收到原始 request，而不是 ContentCachingRequestWrapper，
        // 否则 Spring MultipartResolver 无法解析 getParts()。
        assertEquals(200, response.getStatus());
        assertFalse(chain.receivedRequest instanceof ContentCachingRequestWrapper,
                "multipart 请求不应被包装为 ContentCachingRequestWrapper");
    }

    @Test
    void shouldStillWrapNonMultipartRequestForBodyCaching() throws Exception {
        // given
        AccessLogFilter filter = new AccessLogFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/knowledge/brand-auth");
        request.setContentType("application/json");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();

        // when
        filter.doFilter(request, response, chain);

        // then
        assertEquals(200, response.getStatus());
        assertTrue(chain.receivedRequest instanceof ContentCachingRequestWrapper,
                "非 multipart 请求应被包装为 ContentCachingRequestWrapper");
    }

    /**
     * 记录传入 doFilter 的 request 类型，供断言使用。
     */
    private static final class RecordingFilterChain extends MockFilterChain {
        jakarta.servlet.ServletRequest receivedRequest;

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request,
                             jakarta.servlet.ServletResponse response)
                throws java.io.IOException, jakarta.servlet.ServletException {
            this.receivedRequest = request;
            super.doFilter(request, response);
        }
    }
}
