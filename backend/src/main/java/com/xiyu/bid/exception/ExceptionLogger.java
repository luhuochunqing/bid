// Input: 异常实例 + 请求上下文（URI/IP/Payload）
// Output: 无（副作用：写日志 + Sentry 上报）
// Pos: Exception/异常处理层 - 基础设施，纯副作用
// 维护声明: 集中异常日志/Sentry 上报逻辑，避免 GlobalExceptionHandler 承担多职责.
package com.xiyu.bid.exception;

import io.sentry.Sentry;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;

import com.xiyu.bid.integration.application.WeComApiException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 异常日志器（基础设施层）.
 *
 * <p>职责：统一异常日志记录与 Sentry 上报，从 {@link GlobalExceptionHandler} 中分离出来。
 * GlobalExceptionHandler 只做异常路由分发，本类负责日志格式化与可观测性上报。
 *
 * <p>分级策略：
 * <ul>
 *   <li><b>warn</b>：4xx 业务错误、参数校验失败、资源不存在（受控失败）</li>
 *   <li><b>error</b>：5xx 系统异常、IllegalStateException、外部服务失败（系统级缺陷，需 Sentry 上报）</li>
 * </ul>
 *
 * <p>设计原则：
 * <ul>
 *   <li>所有方法均接收上下文参数，不持有状态</li>
 *   <li>不修改输入参数</li>
 *   <li>日志格式统一，便于 Sentry 聚合与检索</li>
 * </ul>
 */
public final class ExceptionLogger {

    private static final Logger log = LoggerFactory.getLogger(ExceptionLogger.class);

    /** 请求体日志最大长度（避免日志过长）. */
    private static final int MAX_PAYLOAD_LENGTH = 2048;

    /** 需要脱敏的敏感字段关键字（小写匹配）. */
    private static final String[] SENSITIVE_FIELDS = {
            "password", "passwd", "pwd",
            "token", "accesstoken", "refreshtoken",
            "secret", "clientsecret",
            "authorization",
            "apikey", "api_key"
    };

    /** 敏感字段替换后的占位符. */
    private static final String MASKED_VALUE = "***";

    private ExceptionLogger() {
        // 工具类，禁止实例化
    }

    /**
     * 记录 warn 级别异常（4xx 业务错误，受控失败）.
     *
     * @param ex 异常实例
     * @param request HTTP 请求
     * @param code 错误码
     */
    public static void warn(Throwable ex, HttpServletRequest request, int code) {
        String payload = getRequestPayload(request);
        log.warn("业务异常 - URI: {}, Code: {}, Message: {} \nPayload: {}",
                getRequestUri(request), code, ex.getMessage(), payload);
    }

    /**
     * 记录 warn 级别异常（带 IP，用于认证类异常）.
     *
     * @param ex 异常实例
     * @param request HTTP 请求
     */
    public static void warnWithIp(Throwable ex, HttpServletRequest request) {
        log.warn("认证/授权异常 - URI: {}, IP: {}, Message: {}",
                getRequestUri(request), getClientIp(request), ex.getMessage());
    }

    /**
     * 记录 error 级别异常并上报 Sentry（5xx 系统级失败）.
     *
     * @param ex 异常实例
     * @param request HTTP 请求
     * @param errorLabel 错误标签（用于日志分类）
     */
    public static void errorWithSentry(Throwable ex, HttpServletRequest request, String errorLabel) {
        String payload = getRequestPayload(request);
        log.error("{} - URI: {}, IP: {}, Message: {}\nPayload: {}",
                errorLabel, getRequestUri(request), getClientIp(request), ex.getMessage(), payload, ex);
        Sentry.captureException(ex);
    }

    /**
     * 记录 error 级别异常并上报 Sentry（带额外上下文）.
     *
     * @param ex 异常实例
     * @param request HTTP 请求
     * @param errorLabel 错误标签
     * @param extraContext 额外上下文（如 serviceName、upstreamStatusCode 等）
     */
    public static void errorWithSentry(Throwable ex, HttpServletRequest request, String errorLabel, String extraContext) {
        String payload = getRequestPayload(request);
        log.error("{} - URI: {}, IP: {}, Context: {}, Message: {}\nPayload: {}",
                errorLabel, getRequestUri(request), getClientIp(request), extraContext, ex.getMessage(), payload, ex);
        Sentry.captureException(ex);
    }

    /**
     * 记录 warn 日志并上报 Sentry（按 Constitution Principle VII §3：外部服务失败需聚合观测）.
     *
     * @param ex 异常实例
     * @param request HTTP 请求
     * @param label 日志标签
     * @param extraContext 额外上下文
     */
    public static void warnWithSentry(Throwable ex, HttpServletRequest request, String label, String extraContext) {
        log.warn("{} - URI: {}, Context: {}, Message: {}",
                label, getRequestUri(request), extraContext, ex.getMessage());
        Sentry.captureException(ex);
    }

    /**
     * 判断异常是否需要 Sentry 上报.
     *
     * <p>规则：
     * <ul>
     *   <li>BusinessException 5xx → 上报（系统级失败）</li>
     *   <li>IllegalStateException、OptimisticLockingFailureException → 上报</li>
     *   <li>OpenAI UnauthorizedException、BadRequestException → 上报（依赖配置问题）</li>
     *   <li>ExternalServiceException、WeComApiException → 上报（外部依赖问题）</li>
     *   <li>4xx 业务异常、AccessDeniedException、BadCredentialsException → 不上报</li>
     * </ul>
     *
     * @param ex 异常实例
     * @param httpStatus HTTP 状态码
     * @return true 表示需要 Sentry 上报
     */
    public static boolean shouldReportToSentry(Throwable ex, HttpStatus httpStatus) {
        if (ex == null || httpStatus == null) {
            return false;
        }
        // 5xx 一律上报
        if (httpStatus.is5xxServerError()) {
            return true;
        }
        // 特定异常类型即使 4xx 也上报（外部依赖问题需聚合）
        return ex instanceof IllegalStateException
                || ex instanceof OptimisticLockingFailureException
                || ex instanceof com.openai.errors.UnauthorizedException
                || ex instanceof com.openai.errors.BadRequestException
                || ex instanceof ExternalServiceException
                || ex instanceof WeComApiException;
    }

    // ============== 私有辅助方法 ==============

    private static String getRequestUri(HttpServletRequest request) {
        return request != null ? request.getRequestURI() : "unknown";
    }

    /**
     * 获取客户端 IP 地址.
     *
     * <p>SECURITY: 使用 getRemoteAddr()，依赖 server.forward-headers-strategy=NATIVE
     * 时会自动从可信转发头提取真实 IP，避免客户端伪造 X-Forwarded-For.
     */
    private static String getClientIp(HttpServletRequest request) {
        return request != null ? request.getRemoteAddr() : "unknown";
    }

    /**
     * 获取请求 payload（Query + Body）用于日志排查.
     *
     * <p>SECURITY: 自动脱敏 password/token/secret/apikey 等敏感字段，
     * 避免在日志中泄露用户凭证。
     */
    private static String getRequestPayload(HttpServletRequest request) {
        if (request == null) {
            return "Payload: [null request]";
        }
        StringBuilder payload = new StringBuilder();

        // 1. URL Query 参数（已脱敏）
        String queryString = request.getQueryString();
        if (queryString != null && !queryString.isEmpty()) {
            payload.append("Query: ").append(maskSensitiveValues(queryString)).append(" | ");
        }

        // 2. Body（依赖 AccessLogFilter 中包装的 ContentCachingRequestWrapper，已脱敏）
        if (request instanceof org.springframework.web.util.ContentCachingRequestWrapper wrapper) {
            byte[] buf = wrapper.getContentAsByteArray();
            if (buf.length > 0) {
                try {
                    int length = Math.min(buf.length, MAX_PAYLOAD_LENGTH);
                    String body = new String(buf, 0, length, wrapper.getCharacterEncoding());
                    payload.append("Body: ").append(maskSensitiveValues(body))
                            .append(buf.length > MAX_PAYLOAD_LENGTH ? "..." : "");
                } catch (java.io.UnsupportedEncodingException e) {
                    payload.append("Body: [Error reading payload: ").append(e.getMessage()).append("]");
                }
            } else {
                payload.append("Body: [Empty]");
            }
        } else {
            payload.append("Body: [Request not wrapped in ContentCachingRequestWrapper]");
        }

        return payload.toString();
    }

    /**
     * 脱敏字符串中的敏感字段值.
     *
     * <p>匹配 password/token/secret/apikey 等关键字的字段值（key=value 形式），
     * 将值替换为 {@value #MASKED_VALUE}。支持 JSON 和 Query String 两种常见格式。
     */
    private static String maskSensitiveValues(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String result = input;
        for (String field : SENSITIVE_FIELDS) {
            // 匹配 "field":"value" 或 field=value 形式（不区分大小写）
            // JSON 格式: "password":"xxx" → "password":"***"
            result = result.replaceAll(
                    "(?i)(\"(" + field + ")\"\\s*:\\s*\")([^\"]*)(\")",
                    "$1" + Matcher.quoteReplacement(MASKED_VALUE) + "$4");
            // Query 格式: password=xxx& → password=***&
            result = result.replaceAll(
                    "(?i)(\\b" + Pattern.quote(field) + "=)([^&\\s]*)",
                    "$1" + Matcher.quoteReplacement(MASKED_VALUE));
        }
        return result;
    }
}
