package com.xiyu.bid.exception;

import org.springframework.http.HttpStatus;

/**
 * 非法参数异常。
 */
public class InvalidArgumentException extends AppFailureException {

    public InvalidArgumentException(String message) {
        this(message, null);
    }

    public InvalidArgumentException(String message, Throwable cause) {
        super(
                ErrorCategory.INVALID_ARGUMENT,
                400,
                HttpStatus.BAD_REQUEST,
                message,
                false,
                false,
                false,
                "invalid_argument",
                message,
                cause
        );
    }
}
