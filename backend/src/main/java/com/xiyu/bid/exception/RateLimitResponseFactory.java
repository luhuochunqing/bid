package com.xiyu.bid.exception;

import com.xiyu.bid.dto.ApiResponse;

/**
 * 限流响应工厂（纯核心）。
 *
 * <p>负责把限流元数据映射为项目统一的 {@link ApiResponse} 格式，
 * 不依赖 Spring、Servlet 或任何 HTTP 框架，便于单元测试和复用。
 *
 * <p>包位置说明：虽然此类放在 {@code exception} 包中，但它并不表示一个异常类型，
 * 而是一个生成 {@link ApiResponse} DTO 的纯核心工厂。放在此包是因为它与限流错误场景
 * 紧密关联，且项目中目前没有独立的 {@code response} 工具包。若后续项目中新增统一响应
 * 工具包，可考虑迁移。
 *
 * <p>映射规则：
 * <ul>
 *   <li>HTTP 状态码统一由外层 Filter 设置为 429</li>
 *   <li>业务 code 为 429</li>
 *   <li>msg 为中文友好文案；当剩余等待秒数大于 0 时，文案包含具体秒数</li>
 *   <li>data 为剩余等待秒数，便于前端消费</li>
 * </ul>
 */
public final class RateLimitResponseFactory {

    private static final int RATE_LIMIT_CODE = 429;
    private static final String DEFAULT_MESSAGE = "操作太快了，请稍等几秒再试";
    private static final String MESSAGE_WITH_SECONDS = "操作太快了，请等待 %d 秒后再试";

    private RateLimitResponseFactory() {
        // 工具类，禁止实例化
    }

    /**
     * 根据剩余等待秒数构造限流响应。
     *
     * @param retryAfterSeconds 建议客户端等待的秒数；小于等于 0 时使用默认文案且不携带 data
     * @return 统一格式的限流响应
     */
    public static ApiResponse<Integer> build(final int retryAfterSeconds) {
        if (retryAfterSeconds <= 0) {
            return ApiResponse.<Integer>error(RATE_LIMIT_CODE, DEFAULT_MESSAGE, null);
        }
        String message = String.format(MESSAGE_WITH_SECONDS, retryAfterSeconds);
        return ApiResponse.<Integer>error(RATE_LIMIT_CODE, message, retryAfterSeconds);
    }
}
