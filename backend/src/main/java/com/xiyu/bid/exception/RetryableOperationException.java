package com.xiyu.bid.exception;

import org.springframework.http.HttpStatus;

/**
 * 可重试失败异常。
 */
public class RetryableOperationException extends AppFailureException {

    public RetryableOperationException(int code, HttpStatus status, String message) {
        this(code, status, message, null, false);
    }

    public RetryableOperationException(int code, HttpStatus status, String message, Throwable cause, boolean alertRequired) {
        super(
                ErrorCategory.RETRYABLE_FAILURE,
                code,
                status,
                message,
                true,
                false,
                alertRequired,
                "retryable_failure",
                message,
                cause
        );
    }
}
