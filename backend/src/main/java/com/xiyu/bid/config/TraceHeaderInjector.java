// Input: HttpHeaders 构造时的调用点。
// Output: 从 MDC 获取 traceId 并注入 EHSY-TraceID header；如果 MDC 为空则不做任何事。
// Pos: Config/基础设施层 — 出站请求 TraceID 透传。

package com.xiyu.bid.config;

import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;

/**
 * 出站 HTTP 请求的 TraceID 透传工具。
 * <p>从 MDC 读取当前线程的 traceId，注入到 {@link HttpHeaders} 的
 * {@code EHSY-TraceID} 头中。</p>
 * <p>调用方只需在构造完 headers 后加一行：
 * {@code TraceHeaderInjector.inject(headers);}</p>
 * <p>无 Spring 依赖，可直接单测。</p>
 */
public final class TraceHeaderInjector {

    private TraceHeaderInjector() {
    }

    /**
     * 从 MDC 获取当前线程的 traceId，若存在则注入到指定 headers 中。
     *
     * @param headers 可变的请求头容器，允许为 {@code null}（此时为 no-op）。
     */
    public static void inject(final HttpHeaders headers) {
        if (headers == null) {
            return;
        }
        String traceId = MDC.get(TraceConstants.MDC_TRACE_KEY);
        if (traceId != null && !traceId.isBlank()) {
            headers.set(TraceConstants.EHSY_TRACE_ID, traceId.trim());
        }
    }
}
