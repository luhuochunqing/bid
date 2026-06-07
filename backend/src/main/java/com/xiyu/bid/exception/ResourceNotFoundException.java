// Input: 业务失败、资源缺失和参数校验异常
// Output: 业务异常类型与标准化错误映射
// Pos: Exception/异常处理层
// 维护声明: 仅维护异常语义与映射；错误码改动请同步前后端契约.
package com.xiyu.bid.exception;

import org.springframework.http.HttpStatus;

/**
 * 资源不存在异常。
 */
public class ResourceNotFoundException extends AppFailureException {

    private final String resource;
    private final String resourceId;

    public ResourceNotFoundException(String resource, String resourceId) {
        super(
                ErrorCategory.RESOURCE_NOT_FOUND,
                404,
                HttpStatus.NOT_FOUND,
                "请求的资源不存在",
                false,
                false,
                false,
                "resource_not_found",
                String.format("%s not found: %s", resource, resourceId),
                null
        );
        this.resource = resource;
        this.resourceId = resourceId;
    }

    public ResourceNotFoundException(String message) {
        super(
                ErrorCategory.RESOURCE_NOT_FOUND,
                404,
                HttpStatus.NOT_FOUND,
                "请求的资源不存在",
                false,
                false,
                false,
                "resource_not_found",
                message,
                null
        );
        this.resource = "unknown";
        this.resourceId = null;
    }

    public static ResourceNotFoundException withMessage(String message) {
        return new ResourceNotFoundException(message);
    }

    public String getResource() {
        return resource;
    }

    public String getResourceId() {
        return resourceId;
    }
}
