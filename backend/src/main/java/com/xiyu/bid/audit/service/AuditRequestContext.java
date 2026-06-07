package com.xiyu.bid.audit.service;

/**
 * 请求边界采集到的审计上下文。
 */
public record AuditRequestContext(String ipAddress, String userAgent) {
}
