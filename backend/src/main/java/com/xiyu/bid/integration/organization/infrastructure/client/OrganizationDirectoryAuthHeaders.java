package com.xiyu.bid.integration.organization.infrastructure.client;

import com.xiyu.bid.config.TraceHeaderInjector;
import com.xiyu.bid.integration.organization.application.OrganizationIntegrationProperties;
import com.xiyu.bid.integration.organization.domain.OrganizationDirectoryLookupContext;
import org.springframework.http.HttpHeaders;

public class OrganizationDirectoryAuthHeaders {
    private final OrganizationIntegrationProperties.Directory directory;

    OrganizationDirectoryAuthHeaders(
            final OrganizationIntegrationProperties.Directory directory
    ) {
        this.directory = directory;
    }

    HttpHeaders headers(final OrganizationDirectoryLookupContext context) {
        HttpHeaders headers = new HttpHeaders();
        // 1. 从 MDC 注入当前线程的 traceId（统一的 TraceID 透传路径）
        TraceHeaderInjector.inject(headers);
        // 2. 业务上下文兜底：下文的 set() 会覆盖第 1 步 inject 写入的同名 header，
        //    因此最终优先级为 context.traceId() > MDC traceId
        set(headers, directory.getTraceHeaderName(), value(context.traceId()));
        // 3. 源应用标识
        set(headers, directory.getSourceHeaderName(), firstPresent(directory.getSourceApp(), context.sourceApp()));
        // 4. 可选组织目录鉴权 Header；仅从配置注入，不记录 token
        set(headers, directory.getAuthHeaderName(), directory.getAuthToken());
        return headers;
    }

    private void set(final HttpHeaders headers, final String name, final String val) {
        if (!isBlank(name) && !isBlank(val)) {
            headers.set(name.trim(), val.trim());
        }
    }

    private String firstPresent(final String preferred, final String fallback) {
        return isBlank(preferred) ? value(fallback) : preferred.trim();
    }

    private String value(final String val) {
        return val == null ? "" : val.trim();
    }

    private boolean isBlank(final String val) {
        return val == null || val.isBlank();
    }
}
