package com.xiyu.bid.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * API 版本兼容过滤器。
 * 将 /api/v1/Xxx 请求重写为 /api/Xxx，使得现有 Controller 无需修改即可同时响应 /api/ 和 /api/v1/ 路径。
 */
@Slf4j
@Component
@Order(-101)
public class ApiVersionFilter extends OncePerRequestFilter {

    private static final String V1_PREFIX = "/api/v1/";
    private static final String REWRITTEN_PREFIX = "/api/";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (uri.startsWith(V1_PREFIX)) {
            String rewrittenUri = REWRITTEN_PREFIX + uri.substring(V1_PREFIX.length());
            log.debug("API version rewrite: {} -> {}", uri, rewrittenUri);
            chain.doFilter(new VersionedRequestWrapper(request, rewrittenUri), response);
            return;
        }
        chain.doFilter(request, response);
    }

    /** 覆盖 getRequestURI、getServletPath、getPathInfo、getContextPath */
    private static class VersionedRequestWrapper extends HttpServletRequestWrapper {
        private final String rewrittenUri;

        VersionedRequestWrapper(HttpServletRequest request, String rewrittenUri) {
            super(request);
            this.rewrittenUri = rewrittenUri;
        }

        @Override
        public String getRequestURI() { return rewrittenUri; }

        @Override
        public String getServletPath() { return rewrittenUri; }

        @Override
        public String getPathInfo() {
            // /api/v1/xxx → pathInfo was null in v1 form, stays null
            return null;
        }

        @Override
        public String getContextPath() {
            // Delegates to original — context path unchanged
            return ((HttpServletRequest) getRequest()).getContextPath();
        }
    }
}
