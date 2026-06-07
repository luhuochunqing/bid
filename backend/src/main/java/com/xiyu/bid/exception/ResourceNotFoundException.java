// Input: 业务失败、资源缺失和参数校验异常
// Output: 业务异常类型与标准化错误映射
// Pos: Exception/异常处理层
// 维护声明: 仅维护异常语义与映射；错误码改动请同步前后端契约.
package com.xiyu.bid.exception;

/**
 * 资源不存在异常
 * 当请求的资源未找到时抛出
 */
public class ResourceNotFoundException extends RuntimeException {

    private final String resource;
    private final String resourceId;

    public ResourceNotFoundException(String pResource, String pResourceId) {
        super(String.format("%s not found: %s", pResource, pResourceId));
        this.resource = pResource;
        this.resourceId = pResourceId;
    }

    public ResourceNotFoundException(String message) {
        super(message);
        this.resource = "unknown";
        this.resourceId = null;
    }

    public String getResource() {
        return resource;
    }

    public String getResourceId() {
        return resourceId;
    }
}
