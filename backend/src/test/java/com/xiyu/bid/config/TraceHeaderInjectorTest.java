package com.xiyu.bid.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 {@link TraceHeaderInjector} 正确从 MDC 读取 traceId 并注入 headers。
 * <p>纯 JUnit 5 测试，不依赖 Spring 上下文。</p>
 */
class TraceHeaderInjectorTest {

    @AfterEach
    void cleanMdc() {
        MDC.clear();
    }

    @Test
    void shouldInjectTraceIdWhenPresentInMdc() {
        // given
        MDC.put(TraceConstants.MDC_TRACE_KEY, "test-trace-123");
        HttpHeaders headers = new HttpHeaders();

        // when
        TraceHeaderInjector.inject(headers);

        // then
        assertEquals("test-trace-123", headers.getFirst(TraceConstants.EHSY_TRACE_ID));
    }

    @Test
    void shouldNotInjectWhenMdcIsEmpty() {
        // given
        HttpHeaders headers = new HttpHeaders();

        // when
        TraceHeaderInjector.inject(headers);

        // then
        assertNull(headers.getFirst(TraceConstants.EHSY_TRACE_ID));
    }

    @Test
    void shouldNotInjectWhenMdcTraceIdIsBlank() {
        // given
        MDC.put(TraceConstants.MDC_TRACE_KEY, "  ");
        HttpHeaders headers = new HttpHeaders();

        // when
        TraceHeaderInjector.inject(headers);

        // then
        assertNull(headers.getFirst(TraceConstants.EHSY_TRACE_ID));
    }

    @Test
    void shouldNotFailWhenHeadersIsNull() {
        // when & then (should be no-op, not throw)
        assertDoesNotThrow(() -> TraceHeaderInjector.inject(null));
    }

    @Test
    void shouldPreserveExistingHeaders() {
        // given
        MDC.put(TraceConstants.MDC_TRACE_KEY, "trace-456");
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer token123");

        // when
        TraceHeaderInjector.inject(headers);

        // then
        assertEquals("Bearer token123", headers.getFirst("Authorization"));
        assertEquals("trace-456", headers.getFirst(TraceConstants.EHSY_TRACE_ID));
    }

    @Test
    void shouldOverrideExistingEhsyTraceId() {
        // given
        MDC.put(TraceConstants.MDC_TRACE_KEY, "new-trace");
        HttpHeaders headers = new HttpHeaders();
        headers.set(TraceConstants.EHSY_TRACE_ID, "old-trace");

        // when
        TraceHeaderInjector.inject(headers);

        // then
        assertEquals("new-trace", headers.getFirst(TraceConstants.EHSY_TRACE_ID));
    }
}
