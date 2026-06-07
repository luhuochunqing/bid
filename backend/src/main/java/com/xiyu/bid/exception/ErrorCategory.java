package com.xiyu.bid.exception;

/**
 * 统一失败语义分类。
 */
public enum ErrorCategory {
    BUSINESS_UNAVAILABLE,
    INVALID_ARGUMENT,
    RESOURCE_NOT_FOUND,
    EXTERNAL_DEPENDENCY_FAILURE,
    IGNORABLE_FAILURE,
    RETRYABLE_FAILURE,
    DEGRADED,
    ALERT_REQUIRED_FAILURE
}
