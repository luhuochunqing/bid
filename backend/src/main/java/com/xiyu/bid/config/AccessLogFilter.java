// Input: HTTP 请求进入/响应返回
// Output: 结构化日志记录每个请求的方法、URI、耗时、状态码、客户端 IP
// Pos: Config/基础设施层 — 请求访问日志
package com.xiyu.bid.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;

/**
 * 统一请求访问日志过滤器。
 * <p>记录每个 HTTP 请求的方法、URI、耗时、状态码和客户端 IP，与 MDC traceId 关联。</p>
 * <p>注册在 Filter 链最外层，确保所有请求（含被拦截的）都被记录。</p>
 */
@Component
@Order(1)
public class AccessLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AccessLogFilter.class);

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                     final HttpServletResponse response,
                                     final FilterChain chain)
            throws ServletException, IOException {
        long start = System.currentTimeMillis();
        String uri = request.getRequestURI();
        String method = request.getMethod();
        String query = request.getQueryString();
        String clientIp = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(request, wrappedResponse);
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            int status = wrappedResponse.getStatus();
            String traceId = MDC.get(TraceConstants.MDC_TRACE_KEY);
            String fullPath = query != null ? uri + "?" + query : uri;

            log.info("access_log method={} uri={} status={} elapsed={}ms clientIp={} userAgent=\"{}\" traceId={}",
                    method, fullPath, status, elapsed, clientIp,
                    userAgent != null ? userAgent : "-",
                    traceId != null ? traceId : "-");

            wrappedResponse.copyBodyToResponse();
        }
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
        return ip != null ? ip : "-";
    }
}
