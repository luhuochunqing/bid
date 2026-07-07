package com.xiyu.bid.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.sentry.Sentry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * {@link ExceptionLogger} 单元测试.
 *
 * <p>验证点：
 * <ul>
 *   <li>warn / warnWithIp：4xx 路径，WARN 级别，不上报 Sentry</li>
 *   <li>errorWithSentry：5xx 路径，ERROR 级别，上报 Sentry</li>
 *   <li>warnWithSentry：外部服务失败需聚合观测</li>
 *   <li>shouldReportToSentry：分级上报策略</li>
 * </ul>
 *
 * <p>测试风格参考 {@link GlobalExceptionHandlerTest}：ListAppender 监听日志 + mockStatic Sentry.
 */
class ExceptionLoggerTest {

    /** 被测类的 SLF4J logger 名称（用于绑定 ListAppender）. */
    private static final String LOGGER_NAME = "com.xiyu.bid.exception.ExceptionLogger";

    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void attachLogAppender() {
        logger = (Logger) LoggerFactory.getLogger(LOGGER_NAME);
        logger.setLevel(Level.DEBUG);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachLogAppender() {
        logger.detachAppender(appender);
        appender.stop();
    }

    // ============ warn(Throwable, HttpServletRequest, int) ============

    @Test
    void warn_应输出URI_Code_Message_且级别为WARN_且不上报Sentry() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/projects/1");
        request.setQueryString("version=2");
        BusinessException ex = new BusinessException(409, "投标文件已结项，不可修改");

        try (MockedStatic<Sentry> sentry = mockStatic(Sentry.class)) {
            ExceptionLogger.warn(ex, request, 409);
            sentry.verifyNoInteractions();
        }

        // 日志级别为 WARN
        boolean hasWarnLevel = appender.list.stream()
                .anyMatch(event -> event.getLevel().equals(Level.WARN));
        assertThat(hasWarnLevel).as("warn 应使用 WARN 级别").isTrue();

        // 不应有 ERROR 级别日志
        boolean hasErrorLevel = appender.list.stream()
                .anyMatch(event -> event.getLevel().equals(Level.ERROR));
        assertThat(hasErrorLevel).as("warn 不应使用 ERROR 级别").isFalse();

        // 日志内容包含 URI、Code、Message
        String formattedLog = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (a, b) -> a + "\n" + b);
        assertThat(formattedLog).contains("/api/projects/1");
        assertThat(formattedLog).contains("Code: 409");
        assertThat(formattedLog).contains("投标文件已结项");
    }

    // ============ warnWithIp(Throwable, HttpServletRequest) ============

    @Test
    void warnWithIp_应包含IP信息() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/login");
        request.setRemoteAddr("192.168.1.100");
        AccessDeniedException ex = new AccessDeniedException("权限不足");

        ExceptionLogger.warnWithIp(ex, request);

        String formattedLog = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (a, b) -> a + "\n" + b);
        assertThat(formattedLog).contains("192.168.1.100");
        assertThat(formattedLog).contains("/api/auth/login");
        assertThat(formattedLog).contains("权限不足");
    }

    // ============ errorWithSentry(Throwable, HttpServletRequest, String) ============

    @Test
    void errorWithSentry_应使用ERROR级别_上报Sentry_且包含label() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/tenders");
        request.setRemoteAddr("10.0.0.1");
        RuntimeException ex = new RuntimeException("DB 连接失败");

        try (MockedStatic<Sentry> sentry = mockStatic(Sentry.class)) {
            ExceptionLogger.errorWithSentry(ex, request, "SYSTEM_ERROR");
            sentry.verify(() -> Sentry.captureException(ex));
        }

        // 日志级别为 ERROR
        boolean hasErrorLevel = appender.list.stream()
                .anyMatch(event -> event.getLevel().equals(Level.ERROR));
        assertThat(hasErrorLevel).as("errorWithSentry 应使用 ERROR 级别").isTrue();

        // 日志包含 label
        String formattedLog = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (a, b) -> a + "\n" + b);
        assertThat(formattedLog).contains("SYSTEM_ERROR");
        assertThat(formattedLog).contains("DB 连接失败");
    }

    // ============ errorWithSentry(Throwable, HttpServletRequest, String, String) ============

    @Test
    void errorWithSentry_带extraContext_应包含Context并上报Sentry() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/ai/chat");
        request.setRemoteAddr("172.16.0.5");
        ExternalServiceException ex = ExternalServiceException.forService(
                "AI 厂商 API", 503, "上游不可用", "upstream timeout", null);

        try (MockedStatic<Sentry> sentry = mockStatic(Sentry.class)) {
            ExceptionLogger.errorWithSentry(ex, request, "AI_UPSTREAM_FAILURE",
                    "serviceName=DeepSeek, upstreamStatus=503");
            sentry.verify(() -> Sentry.captureException(ex));
        }

        boolean hasErrorLevel = appender.list.stream()
                .anyMatch(event -> event.getLevel().equals(Level.ERROR));
        assertThat(hasErrorLevel).as("带 extraContext 的 errorWithSentry 应使用 ERROR 级别").isTrue();

        String formattedLog = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (a, b) -> a + "\n" + b);
        assertThat(formattedLog).contains("AI_UPSTREAM_FAILURE");
        assertThat(formattedLog).contains("Context: serviceName=DeepSeek, upstreamStatus=503");
        assertThat(formattedLog).contains("上游不可用");
    }

    // ============ warnWithSentry(Throwable, HttpServletRequest, String, String) ============

    @Test
    void warnWithSentry_应上报Sentry_且日志包含Context() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/integration/wecom");
        ExternalServiceException ex = ExternalServiceException.forService(
                "WeCom API", 401, "企微凭证失效", "invalid corp secret", null);

        try (MockedStatic<Sentry> sentry = mockStatic(Sentry.class)) {
            ExceptionLogger.warnWithSentry(ex, request, "WECOM_API_ERROR",
                    "corpId=ww12345, api=/cgi-bin/user/get");
            sentry.verify(() -> Sentry.captureException(ex));
        }

        // WARN 级别（按 Constitution Principle VII §3：外部服务失败聚合观测，但仍属于业务可恢复级别）
        boolean hasWarnLevel = appender.list.stream()
                .anyMatch(event -> event.getLevel().equals(Level.WARN));
        assertThat(hasWarnLevel).as("warnWithSentry 应使用 WARN 级别").isTrue();

        String formattedLog = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (a, b) -> a + "\n" + b);
        assertThat(formattedLog).contains("WECOM_API_ERROR");
        assertThat(formattedLog).contains("Context: corpId=ww12345, api=/cgi-bin/user/get");
        assertThat(formattedLog).contains("企微凭证失效");
    }

    // ============ shouldReportToSentry(Throwable, HttpStatus) ============

    @Test
    void shouldReportToSentry_5xx_应上报() {
        IllegalStateException ex = new IllegalStateException("DB state conflict");
        boolean result = ExceptionLogger.shouldReportToSentry(ex, HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(result).as("5xx 一律上报 Sentry").isTrue();
    }

    @Test
    void shouldReportToSentry_4xx_IllegalStateException_应上报() {
        IllegalStateException ex = new IllegalStateException("Duplicate key");
        boolean result = ExceptionLogger.shouldReportToSentry(ex, HttpStatus.CONFLICT);
        assertThat(result).as("4xx + IllegalStateException 应上报 Sentry（外部依赖问题）").isTrue();
    }

    @Test
    void shouldReportToSentry_4xx_BusinessException_不上报() {
        BusinessException ex = new BusinessException(409, "业务冲突");
        boolean result = ExceptionLogger.shouldReportToSentry(ex, HttpStatus.CONFLICT);
        assertThat(result).as("4xx + BusinessException 属于业务可恢复错误，不上报 Sentry").isFalse();
    }

    @Test
    void shouldReportToSentry_异常为null_不上报() {
        boolean result = ExceptionLogger.shouldReportToSentry(null, HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(result).as("异常为 null 时不上报").isFalse();
    }

    @Test
    void shouldReportToSentry_httpStatus为null_不上报() {
        IllegalStateException ex = new IllegalStateException("state error");
        boolean result = ExceptionLogger.shouldReportToSentry(ex, null);
        assertThat(result).as("httpStatus 为 null 时不上报").isFalse();
    }

    @Test
    void shouldReportToSentry_4xx_AccessDeniedException_不上报() {
        AccessDeniedException ex = new AccessDeniedException("权限不足");
        boolean result = ExceptionLogger.shouldReportToSentry(ex, HttpStatus.FORBIDDEN);
        assertThat(result).as("4xx + AccessDeniedException 属于业务权限校验失败，不上报 Sentry").isFalse();
    }

    @Test
    void shouldReportToSentry_4xx_ExternalServiceException_应上报() {
        ExternalServiceException ex = ExternalServiceException.forService(
                "AI 厂商", 401, "凭证失效", "invalid api key", null);
        boolean result = ExceptionLogger.shouldReportToSentry(ex, HttpStatus.BAD_GATEWAY);
        assertThat(result).as("4xx + ExternalServiceException 属于外部依赖问题，应上报 Sentry").isTrue();
    }

    // ============ 敏感字段脱敏（getRequestPayload 内部调用 maskSensitiveValues） ============

    /**
     * 验证 password 字段在 Query 参数中被脱敏.
     * 通过 warn 方法触发 getRequestPayload 调用，从日志中验证脱敏效果。
     */
    @Test
    void warn_queryString含password_应脱敏为星号() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setQueryString("username=admin&password=Secret123!");
        BusinessException ex = new BusinessException(400, "登录失败");

        ExceptionLogger.warn(ex, request, 400);

        String formattedLog = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (a, b) -> a + "\n" + b);
        assertThat(formattedLog).contains("password=***");
        assertThat(formattedLog).doesNotContain("Secret123!");
    }

    @Test
    void warn_queryString含token_应脱敏为星号() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/data");
        request.setQueryString("accessToken=abc.def.ghi&page=1");
        BusinessException ex = new BusinessException(400, "请求失败");

        ExceptionLogger.warn(ex, request, 400);

        String formattedLog = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (a, b) -> a + "\n" + b);
        assertThat(formattedLog).contains("accessToken=***");
        assertThat(formattedLog).doesNotContain("abc.def.ghi");
    }

    @Test
    void warn_queryString含apiKey_应脱敏为星号() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/ai/chat");
        request.setQueryString("apiKey=sk-1234567890abcdef");
        BusinessException ex = new BusinessException(400, "AI 调用失败");

        ExceptionLogger.warn(ex, request, 400);

        String formattedLog = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (a, b) -> a + "\n" + b);
        assertThat(formattedLog).contains("apiKey=***");
        assertThat(formattedLog).doesNotContain("sk-1234567890abcdef");
    }
}
