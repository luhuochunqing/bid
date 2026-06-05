package com.xiyu.bid.integration.organization.infrastructure.client;

import com.xiyu.bid.integration.organization.application.OrganizationIntegrationProperties;
import com.xiyu.bid.integration.organization.domain.OrganizationDirectoryLookupContext;
import org.springframework.http.HttpHeaders;

public class OrganizationDirectoryAuthHeaders {
    private final OrganizationIntegrationProperties.Directory directory;

    OrganizationDirectoryAuthHeaders(
            OrganizationIntegrationProperties.Directory directory
    ) {
        this.directory = directory;
    }

    HttpHeaders headers(OrganizationDirectoryLookupContext context) {
        HttpHeaders headers = new HttpHeaders();
        set(headers, directory.getTraceHeaderName(), value(context.traceId()));
        set(headers, directory.getSourceHeaderName(), firstPresent(directory.getSourceApp(), context.sourceApp()));
        return headers;
    }

    private void set(HttpHeaders headers, String name, String val) {
        if (!isBlank(name) && !isBlank(val)) {
            headers.set(name.trim(), val.trim());
        }
    }

    private String firstPresent(String preferred, String fallback) {
        return isBlank(preferred) ? value(fallback) : preferred.trim();
    }

    private String value(String val) {
        return val == null ? "" : val.trim();
    }

    private boolean isBlank(String val) {
        return val == null || val.isBlank();
    }
}
