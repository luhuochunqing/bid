package com.xiyu.bid.exception;

/**
 * Exception thrown when a requested project is not found.
 */
public class ProjectNotFoundException extends BusinessException {

    public ProjectNotFoundException(Long projectId) {
        super(404, "项目不存在: ID=" + projectId);
    }

    public ProjectNotFoundException(String projectName) {
        super(404, "项目不存在: " + projectName);
    }

    public ProjectNotFoundException(String message, Throwable cause) {
        super(404, message, cause);
    }
}
