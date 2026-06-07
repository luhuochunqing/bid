// Input: 业务失败、资源缺失和参数校验异常
// Output: 业务异常类型与标准化错误映射
// Pos: Exception/异常处理层
// 维护声明: 仅维护异常语义与映射；错误码改动请同步前后端契约.
package com.xiyu.bid.exception;

import org.springframework.http.HttpStatus;

/**
 * 业务异常。
 */
public class BusinessException extends AppFailureException {

    public BusinessException(int code, String message) {
        this(code, message, null);
    }

    public BusinessException(int code, String message, Throwable cause) {
        super(
                ErrorCategory.BUSINESS_UNAVAILABLE,
                code,
                resolveHttpStatus(code),
                message,
                false,
                false,
                false,
                "business_unavailable",
                message,
                cause
        );
    }

    public BusinessException(String message) {
        this(400, message, null);
    }

    private static HttpStatus resolveHttpStatus(int code) {
        return switch (code) {
            case 400 -> HttpStatus.BAD_REQUEST;
            case 401 -> HttpStatus.UNAUTHORIZED;
            case 403 -> HttpStatus.FORBIDDEN;
            case 404 -> HttpStatus.NOT_FOUND;
            case 409 -> HttpStatus.CONFLICT;
            case 423 -> HttpStatus.LOCKED;
            case 429 -> HttpStatus.TOO_MANY_REQUESTS;
            case 502 -> HttpStatus.BAD_GATEWAY;
            case 503 -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_REQUEST;
        };
    }
}
