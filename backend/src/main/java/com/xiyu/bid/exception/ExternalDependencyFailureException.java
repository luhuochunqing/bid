package com.xiyu.bid.exception;

import org.springframework.http.HttpStatus;

/**
 * 外部依赖失败异常。
 */
public class ExternalDependencyFailureException extends AppFailureException {

    public ExternalDependencyFailureException(String message) {
        this(message, null, true, false);
    }

    public ExternalDependencyFailureException(String message, Throwable cause, boolean retryable, boolean alertRequired) {
        super(
                ErrorCategory.EXTERNAL_DEPENDENCY_FAILURE,
                502,
                HttpStatus.BAD_GATEWAY,
                message,
                retryable,
                false,
                alertRequired,
                "external_dependency_failure",
                message,
                cause
        );
    }
}
