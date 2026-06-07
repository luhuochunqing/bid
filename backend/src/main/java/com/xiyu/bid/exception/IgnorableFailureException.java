package com.xiyu.bid.exception;

import org.springframework.http.HttpStatus;

/**
 * 可忽略失败异常。
 */
public class IgnorableFailureException extends AppFailureException {

    public IgnorableFailureException(String message) {
        this(message, null);
    }

    public IgnorableFailureException(String message, Throwable cause) {
        super(
                ErrorCategory.IGNORABLE_FAILURE,
                200,
                HttpStatus.OK,
                message,
                false,
                true,
                false,
                "ignorable_failure",
                message,
                cause
        );
    }
}
