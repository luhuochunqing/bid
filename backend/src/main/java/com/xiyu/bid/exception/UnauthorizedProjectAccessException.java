package com.xiyu.bid.exception;

/**
 * Exception thrown when a user attempts to access a project they don't have permission for.
 */
public class UnauthorizedProjectAccessException extends BusinessException {

    public UnauthorizedProjectAccessException(Long projectId, Long userId) {
        super(403, "无权访问该项目: 项目ID=" + projectId + ", 用户ID=" + userId);
    }

    public UnauthorizedProjectAccessException(Long projectId) {
        super(403, "无权访问该项目: 项目ID=" + projectId);
    }

    public UnauthorizedProjectAccessException(String message) {
        super(403, message);
    }

    public UnauthorizedProjectAccessException(String message, Throwable cause) {
        super(403, message, cause);
    }
}
