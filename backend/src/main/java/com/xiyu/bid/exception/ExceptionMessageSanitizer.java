// Input: Throwable 异常实例
// Output: 可安全返回前端的消息字符串
// Pos: Exception/异常处理层 - 纯核心，无副作用
// 维护声明: 此类只做"异常 → 安全消息"映射，不做日志/Sentry/HTTP 状态映射.
package com.xiyu.bid.exception;

/**
 * 异常消息脱敏器（纯核心，FP-Java Profile）.
 *
 * <p>职责：把异常的 {@code message} 转换为可安全返回前端的消息字符串。
 * 区分两类异常：
 * <ol>
 *   <li><b>受控异常</b>：项目自定义的 {@link AppFailureException} 子类，已有 {@code userMessage} 字段，
 *       优先使用 userMessage；为空时 fallback 到硬编码默认值。</li>
 *   <li><b>外部异常</b>：{@link ExternalServiceException} 已有 {@code userFriendlyMessage} 字段，
 *       优先使用；为空时 fallback 到通用外部服务失败消息。</li>
 *   <li><b>未受控异常</b>：NPE、JPA 转换异常等，message 可能含 SQL/字段名/敏感信息，
 *       统一返回硬编码默认值，不透传。</li>
 *   <li><b>半受控异常</b>：{@link IllegalArgumentException} 在项目内既用于业务校验
 *       （如"产品类型不能为空"）也可能携带系统细节（如 SQL 错误），
 *       通过 {@link #isSafeForPassthrough(String)} 判定 message 是否可安全透传。</li>
 * </ol>
 *
 * <p>纯核心约束：
 * <ul>
 *   <li>无 Spring 依赖、无 IO、无日志、无 Sentry</li>
 *   <li>所有方法为纯函数：相同输入产生相同输出</li>
 *   <li>不修改输入参数</li>
 * </ul>
 */
public final class ExceptionMessageSanitizer {

    /** 受控异常 fallback 默认消息（业务异常未提供 userMessage 时使用）. */
    static final String DEFAULT_BUSINESS_MESSAGE = "业务处理失败";

    /** 外部服务异常 fallback 默认消息. */
    static final String DEFAULT_EXTERNAL_SERVICE_MESSAGE = "外部服务调用失败，请稍后重试";

    /** 未受控异常 fallback 默认消息（IllegalArgumentException 等）. */
    static final String DEFAULT_UNCONTROLLED_MESSAGE = "请求处理失败";

    /** OpenAI BadRequest 透传场景的默认消息. */
    static final String DEFAULT_AI_PROVIDER_MESSAGE = "AI provider 返回错误，请稍后重试";

    /** 企微 API 异常透传场景的默认消息. */
    static final String DEFAULT_WECOM_MESSAGE = "企微服务调用失败";

    /** HttpMessageNotReadable 场景的默认消息（Jackson 解析失败）. */
    static final String DEFAULT_MALFORMED_REQUEST_MESSAGE = "请求体格式错误";

    private ExceptionMessageSanitizer() {
        // 工具类，禁止实例化
    }

    /**
     * 从异常中提取可安全返回前端的消息.
     *
     * @param ex 异常实例，不能为 null
     * @return 安全的对外消息字符串（永不为 null，已过滤 CRLF 注入）
     */
    public static String sanitize(Throwable ex) {
        if (ex == null) {
            return DEFAULT_UNCONTROLLED_MESSAGE;
        }

        // 1. AppFailureException 子类：优先使用 userMessage（受控外部消息）
        if (ex instanceof AppFailureException appFailure) {
            String userMessage = appFailure.getUserMessage();
            if (userMessage != null && !userMessage.isBlank()) {
                return stripCrlf(userMessage);
            }
            return DEFAULT_BUSINESS_MESSAGE;
        }

        // 2. ExternalServiceException：优先使用 userFriendlyMessage
        if (ex instanceof ExternalServiceException externalService) {
            String friendly = externalService.getUserFriendlyMessage();
            if (friendly != null && !friendly.isBlank()) {
                return stripCrlf(friendly);
            }
            return DEFAULT_EXTERNAL_SERVICE_MESSAGE;
        }

        // 3. Spring Security 认证异常：项目内已封装为固定文案，但保险起见再脱敏
        if (ex instanceof org.springframework.security.core.AuthenticationException) {
            // RoleNotAuthorizedException 是项目自定义受控异常，message 来自业务代码
            if (ex instanceof RoleNotAuthorizedException) {
                String msg = ex.getMessage();
                if (msg != null && !msg.isBlank()) {
                    return stripCrlf(msg);
                }
            }
            return "认证失败，请重新登录";
        }
        if (ex instanceof org.springframework.security.access.AccessDeniedException) {
            return "权限不足，无法访问该资源";
        }

        // 4. IllegalArgumentException：项目内既用于业务校验也可能携带系统细节，
        //    通过 isSafeForPassthrough 判定 message 是否为简单业务文案
        if (ex instanceof IllegalArgumentException illegalArg) {
            String msg = illegalArg.getMessage();
            if (msg != null && !msg.isBlank() && isSafeForPassthrough(msg)) {
                return stripCrlf(msg);
            }
            return DEFAULT_UNCONTROLLED_MESSAGE;
        }

        // 5. 其他未受控异常（NPE、JPA 转换异常等）：永不透传 message
        return DEFAULT_UNCONTROLLED_MESSAGE;
    }

    /**
     * 针对特定外部依赖异常场景，提取安全消息.
     *
     * <p>用于 {@link GlobalExceptionHandler} 中处理 OpenAI / 企微 等场景时，
     * 根据 errcode / 关键字匹配后传入预定义的友好消息；如果传入为空则用默认值。
     *
     * @param customMessage 自定义消息（可能为 null/空）
     * @param defaultMessage 默认消息（可能为 null/空，此时用内置默认）
     * @return 安全的消息字符串（已过滤 CRLF 注入）
     */
    public static String resolveOrDefault(String customMessage, String defaultMessage) {
        if (customMessage != null && !customMessage.isBlank()) {
            return stripCrlf(customMessage);
        }
        if (defaultMessage != null && !defaultMessage.isBlank()) {
            return stripCrlf(defaultMessage);
        }
        return DEFAULT_UNCONTROLLED_MESSAGE;
    }

    /**
     * 判断异常 message 是否可以安全透传给前端.
     *
     * <p>仅用于已知安全的异常类型（项目内自定义异常），其他情况一律返回 false.
     *
     * @param ex 异常实例
     * @return true 表示该异常的 message 来自项目受控代码，可透传
     */
    public static boolean isMessageSafe(Throwable ex) {
        if (ex == null) {
            return false;
        }
        // 仅项目内自定义异常的 userMessage / userFriendlyMessage 可视为安全
        return ex instanceof AppFailureException
                || ex instanceof ExternalServiceException
                // RoleNotAuthorizedException: 项目自定义，message 来自业务代码（如 OSS 角色）
                || ex instanceof RoleNotAuthorizedException;
    }

    /**
     * 过滤 CRLF 注入风险：将 \r、\n 替换为空格.
     *
     * <p>防御场景：攻击者在输入中注入 \r\n 可能伪造日志行或 HTTP 响应头。
     * 异常 message 来自外部输入时（如 controller 抛出的 ResponseStatusException），
     * 必须在写入日志或返回前端前过滤。
     *
     * @param value 待过滤的字符串
     * @return 已过滤 \r\n 的字符串（null 输入返回 null）
     */
    private static String stripCrlf(String value) {
        if (value == null) {
            return null;
        }
        return value.replace('\r', ' ').replace('\n', ' ');
    }

    /** 安全消息最大长度，超过则视为可能含堆栈信息，不透传. */
    private static final int SAFE_MESSAGE_MAX_LENGTH = 200;

    /** SQL 关键字模式（小写匹配，前后需为非字母边界）. */
    private static final String[] SQL_PATTERNS = {
            "select ", "insert ", "update ", "delete from", "drop ", "alter ",
            "union select", "' or '", "1=1", "where 1=1",
            "sql ", "sqlerror", "sqlstate"
    };

    /** 包路径模式（Java 类名/堆栈痕迹）. */
    private static final String[] PACKAGE_PATTERNS = {
            "com.xiyu", "org.springframework", "java.lang", "java.util",
            "sun.reflect", "jdk.internal"
    };

    /**
     * 判断消息是否可安全透传给前端.
     *
     * <p>用于 {@link IllegalArgumentException} 这类"半受控"异常——项目内既用于业务校验
     * （如"产品类型不能为空"）也可能携带系统细节（如 SQL 错误）。
     * 仅当消息通过以下检查时才视为安全：
     * <ol>
     *   <li>非空且长度 ≤ {@value #SAFE_MESSAGE_MAX_LENGTH}（防止堆栈信息透传）</li>
     *   <li>不含 SQL 关键字（select/insert/update/delete from/drop/alter 等）</li>
     *   <li>不含 Java 包路径（com.xiyu/org.springframework/java.lang 等）</li>
     *   <li>不含堆栈痕迹模式（"at xxx.xxx(xxx:NN)"）</li>
     *   <li>不含 .java/.class 文件后缀</li>
     * </ol>
     *
     * @param message 待检查的消息（可能为 null）
     * @return true 表示消息是简单的业务文案，可透传
     */
    static boolean isSafeForPassthrough(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        // 1. 长度检查：过长的 message 可能包含堆栈信息
        if (message.length() > SAFE_MESSAGE_MAX_LENGTH) {
            return false;
        }
        String lower = message.toLowerCase();
        // 2. SQL 关键字检查
        for (String pattern : SQL_PATTERNS) {
            if (lower.contains(pattern)) {
                return false;
            }
        }
        // 3. 包路径/类名检查
        for (String pattern : PACKAGE_PATTERNS) {
            if (lower.contains(pattern)) {
                return false;
            }
        }
        // 4. 堆栈痕迹模式检查（"at xxx.xxx(xxx:NN)"）
        if (message.contains(" at ") && message.contains("(") && message.contains(":")) {
            return false;
        }
        // 5. .java/.class 文件后缀检查
        if (message.contains(".java") || message.contains(".class")) {
            return false;
        }
        return true;
    }
}
