// Input: 业务失败、资源缺失和参数校验异常
// Output: 业务异常类型与标准化错误映射
// Pos: Exception/异常处理层
// 维护声明: 仅维护异常语义与映射；错误码改动请同步前后端契约.
package com.xiyu.bid.exception;

/**
 * 业务异常
 * 用于处理业务逻辑中的错误情况
 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int pCode, String message) {
        super(message);
        this.code = pCode;
    }

    public BusinessException(int pCode, String message, Throwable cause) {
        super(message, cause);
        this.code = pCode;
    }

    public BusinessException(String message) {
        super(message);
        this.code = 400;
    }

    public int getCode() {
        return code;
    }
}
