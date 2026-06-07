package com.xiyu.bid.exception;

import org.slf4j.event.Level;
import org.springframework.http.HttpStatus;

/**
 * ErrorCategory 的集中式默认契约。
 *
 * <p>每个 ErrorCategory 声明三项默认值：HTTP 状态、日志级别、是否可重试。
 * 具名异常在构造时应从此类取默认值，确保 HTTP 映射/日志策略不再分散。
 *
 * <p>日志级别说明：
 * <ul>
 *   <li>WARN - 可预期的业务失败（非法参数、资源不存在、业务不可用）
 *   <li>ERROR - 外部依赖失败、需要告警的情况
 * </ul>
 *
 * <p>设计原则：
 * <ul>
 *   <li>单一事实来源：具名异常的默认值只从此类读取。
 *   <li>向后兼容：允许具名异常在构造时显式覆盖默认值。
 *   <li>可发现性：所有异常类型的默认行为一目了然。
 * </ul>
 */
public enum ErrorCategoryDefaults {

    /**
     * 业务暂时不可用（如余额不足、无权限操作等），HTTP 400/409。
     * 可重试：取决于具体业务场景。
     */
    BUSINESS_UNAVAILABLE(HttpStatus.BAD_REQUEST, Level.WARN, false),

    /**
     * 非法参数或请求格式错误，HTTP 400。
     * 客户端需修正请求后重试，不可重试。
     */
    INVALID_ARGUMENT(HttpStatus.BAD_REQUEST, Level.WARN, false),

    /**
     * 请求的资源不存在，HTTP 404。
     * 不可重试，资源已删除或 ID 错误。
     */
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, Level.WARN, false),

    /**
     * 外部依赖（数据库、第三方 API）失败，HTTP 502/503。
     * 通常可重试，取决于外部服务状态。
     */
    EXTERNAL_DEPENDENCY_FAILURE(HttpStatus.BAD_GATEWAY, Level.ERROR, true),

    /**
     * 可忽略的失败（如业务校验跳过），HTTP 200（视为成功但有副作用）。
     * 不需要重试，不需要告警。
     */
    IGNORABLE_FAILURE(HttpStatus.OK, Level.WARN, false),

    /**
     * 可重试的暂时性失败（如乐观锁冲突、网络抖动），HTTP 409/503。
     * 需要重试，通常需要告警（如重试次数超限）。
     */
    RETRYABLE_FAILURE(HttpStatus.CONFLICT, Level.WARN, true),

    /**
     * 降级状态，服务仍可用但部分功能受损，HTTP 200/503。
     * 可重试：取决于降级范围。
     */
    DEGRADED(HttpStatus.SERVICE_UNAVAILABLE, Level.WARN, true),

    /**
     * 必须触发告警的严重失败，HTTP 500/502/503。
     * 不可重试（需人工介入），必须告警。
     */
    ALERT_REQUIRED_FAILURE(HttpStatus.INTERNAL_SERVER_ERROR, Level.ERROR, false);

    private final HttpStatus defaultHttpStatus;
    private final Level defaultLogLevel;
    private final boolean defaultRetryable;

    ErrorCategoryDefaults(HttpStatus defaultHttpStatus, Level defaultLogLevel, boolean defaultRetryable) {
        this.defaultHttpStatus = defaultHttpStatus;
        this.defaultLogLevel = defaultLogLevel;
        this.defaultRetryable = defaultRetryable;
    }

    public HttpStatus defaultHttpStatus() {
        return defaultHttpStatus;
    }

    public Level defaultLogLevel() {
        return defaultLogLevel;
    }

    public boolean defaultRetryable() {
        return defaultRetryable;
    }
}
