package com.xiyu.bid.exception;

import org.springframework.http.HttpStatus;

/**
 * 必须告警失败异常。
 */
public class AlertRequiredFailureException extends AppFailureException {

    public AlertRequiredFailureException(int code, HttpStatus status, String message) {
        this(code, status, message, null);
    }

    public AlertRequiredFailureException(int code, HttpStatus status, String message, Throwable cause) {
        super(
                ErrorCategory.ALERT_REQUIRED_FAILURE,
                code,
                status,
                message,
                false,
                false,
                true,
                "alert_required_failure",
                message,
                cause
        );
    }
}
