package com.xiyu.bid.exception;

import org.springframework.http.HttpStatus;

/**
 * 统一失败语义基类。
 *
 * <p>用于把错误分类、HTTP 映射、恢复策略和对外消息收敛到一个稳定契约，
 * 避免业务层散落使用 RuntimeException / ResponseStatusException / IllegalArgumentException。
 */
public abstract class AppFailureException extends RuntimeException {

    private final ErrorCategory category;
    private final int code;
    private final HttpStatus httpStatus;
    private final String userMessage;
    private final boolean retryable;
    private final boolean ignorable;
    private final boolean alertRequired;
    private final String errorKey;

    protected AppFailureException(
            ErrorCategory category,
            int code,
            HttpStatus httpStatus,
            String userMessage,
            boolean retryable,
            boolean ignorable,
            boolean alertRequired,
            String errorKey,
            String message,
            Throwable cause) {
        super(message, cause);
        this.category = category;
        this.code = code;
        this.httpStatus = httpStatus;
        this.userMessage = userMessage;
        this.retryable = retryable;
        this.ignorable = ignorable;
        this.alertRequired = alertRequired;
        this.errorKey = errorKey;
    }

    public ErrorCategory getCategory() {
        return category;
    }

    public int getCode() {
        return code;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public boolean isIgnorable() {
        return ignorable;
    }

    public boolean isAlertRequired() {
        return alertRequired;
    }

    public String getErrorKey() {
        return errorKey;
    }
}
