package com.xiyu.bid.exception;

import org.springframework.http.HttpStatus;

/**
 * 业务不可用异常。
 */
public class BusinessUnavailableException extends AppFailureException {

    public BusinessUnavailableException(int code, HttpStatus httpStatus, String message) {
        this(code, httpStatus, message, null);
    }

    public BusinessUnavailableException(int code, HttpStatus httpStatus, String message, Throwable cause) {
        super(
                ErrorCategory.BUSINESS_UNAVAILABLE,
                code,
                httpStatus,
                message,
                false,
                false,
                false,
                "business_unavailable",
                message,
                cause
        );
    }
}
