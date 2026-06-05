package com.xiyu.bid.audit.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class AuditRequestContextProvider {

    public AuditRequestContext currentContext() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return new AuditRequestContext(null, null);
        }
        HttpServletRequest request = attributes.getRequest();
        return new AuditRequestContext(getClientIp(request), request.getHeader("User-Agent"));
    }

    private String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String ip = firstKnownHeader(request, "X-Forwarded-For");
        if (ip == null) {
            ip = firstKnownHeader(request, "X-Real-IP");
        }
        if (ip == null) {
            ip = firstKnownHeader(request, "Proxy-Client-IP");
        }
        if (ip == null) {
            ip = firstKnownHeader(request, "WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private String firstKnownHeader(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null || value.isBlank() || "unknown".equalsIgnoreCase(value)) {
            return null;
        }
        return value;
    }
}
