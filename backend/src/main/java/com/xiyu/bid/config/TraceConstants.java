package com.xiyu.bid.config;

/**
 * 链路追踪相关常量。
 * <p>集中管理 TraceID 的 Header 键名、MDC key 等常量，避免各路硬编码。</p>
 */
public final class TraceConstants {

    /** 出站请求的 TraceID header 键名（来源系统透传）。 */
    public static final String EHSY_TRACE_ID = "EHSY-TraceID";

    /** 出站请求的源应用 header 键名。 */
    public static final String EHSY_SRCAPP = "EHSY-SRCAPP";

    /**
     * 响应头中返回客户端的 TraceID 键名。
     * <p>用于入站响应回写，与 {@link #EHSY_TRACE_ID}（用于出站请求头）区分。</p>
     */
    public static final String X_TRACE_ID = "X-Trace-Id";

    /** MDC 中存储 traceId 的 key。 */
    public static final String MDC_TRACE_KEY = "traceId";

    private TraceConstants() {
    }
}
