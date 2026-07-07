// Input: Throwable 异常实例 + HTTP 状态码
// Output: ApiResponse（安全响应体）+ 错误码
// Pos: Exception/异常处理层 - 纯核心，无副作用
// 维护声明: 此类做"异常 → 响应"的规则映射，不做日志/Sentry/IO.
package com.xiyu.bid.exception;

import com.xiyu.bid.dto.ApiResponse;
import org.springframework.http.HttpStatus;

/**
 * 异常响应策略（纯核心，FP-Java Profile）.
 *
 * <p>职责：把异常 + HTTP 状态码映射为 {@link ApiResponse} 响应体。
 * 集中管理"哪些异常需要透传 message、哪些必须硬编码"的规则。
 *
 * <p>纯核心约束：
 * <ul>
 *   <li>无 Spring 依赖（除 HttpStatus 这类不可变枚举）</li>
 *   <li>无 IO、无日志、无 Sentry</li>
 *   <li>所有方法为纯函数：相同输入产生相同输出</li>
 *   <li>不修改输入参数</li>
 * </ul>
 *
 * <p>响应规则：
 * <ol>
 *   <li><b>受控异常</b>（AppFailureException / ExternalServiceException）：使用安全消息</li>
 *   <li><b>认证类异常</b>：硬编码友好消息，不透传</li>
 *   <li><b>未受控异常</b>：硬编码通用消息，不透传</li>
 *   <li><b>参数校验</b>：透传校验错误（受控、有限、用户可操作）</li>
 * </ol>
 */
public final class ExceptionResponseStrategy {

    private ExceptionResponseStrategy() {
        // 工具类，禁止实例化
    }

    /**
     * 构建错误响应（默认使用安全消息）.
     *
     * @param ex 异常实例
     * @param httpStatus HTTP 状态码
     * @return ApiResponse 响应体
     */
    public static ApiResponse<Void> buildResponse(Throwable ex, HttpStatus httpStatus) {
        int code = httpStatus.value();
        String safeMessage = ExceptionMessageSanitizer.sanitize(ex);
        return ApiResponse.error(code, safeMessage);
    }

    /**
     * 构建错误响应（带自定义错误码，用于 AppFailureException）.
     *
     * @param ex 异常实例
     * @param httpStatus HTTP 状态码
     * @param customCode 业务错误码
     * @return ApiResponse 响应体
     */
    public static ApiResponse<Void> buildResponse(Throwable ex, HttpStatus httpStatus, int customCode) {
        String safeMessage = ExceptionMessageSanitizer.sanitize(ex);
        return ApiResponse.error(customCode, safeMessage);
    }

    /**
     * 构建硬编码错误响应（用于已知固定文案的场景，如认证失败/权限不足）.
     *
     * @param httpStatus HTTP 状态码
     * @param fixedMessage 固定文案
     * @return ApiResponse 响应体
     */
    public static ApiResponse<Void> buildFixedResponse(HttpStatus httpStatus, String fixedMessage) {
        return ApiResponse.error(httpStatus.value(), fixedMessage);
    }

    /**
     * 构建带前缀的响应（如 "ROLE_NOT_AUTHORIZED: xxx"）.
     * 仅当原始 message 来自受控异常时才透传，否则仅返回前缀.
     *
     * @param ex 异常实例
     * @param httpStatus HTTP 状态码
     * @param prefix 前缀（如 "ROLE_NOT_AUTHORIZED"）
     * @return ApiResponse 响应体
     */
    public static ApiResponse<Void> buildWithPrefix(Throwable ex, HttpStatus httpStatus, String prefix) {
        int code = httpStatus.value();
        if (ExceptionMessageSanitizer.isMessageSafe(ex)) {
            // 受控异常：可以使用其 message
            String safeMessage = ExceptionMessageSanitizer.sanitize(ex);
            return ApiResponse.error(code, prefix + ": " + safeMessage);
        }
        // 未受控异常：仅返回前缀，不透传 message
        return ApiResponse.error(code, prefix);
    }
}
