package com.xiyu.bid.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.openai.core.http.Headers;
import com.openai.errors.UnauthorizedException;
import com.openai.models.ErrorObject;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.exception.BusinessUnavailableException;
import com.xiyu.bid.exception.RetryableOperationException;
import io.sentry.Sentry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private ListAppender<ILoggingEvent> appender;
    private Logger handlerLogger;
    private Logger exceptionLoggerLogger;

    @BeforeEach
    void attachLogAppender() {
        handlerLogger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        handlerLogger.setLevel(Level.DEBUG);
        appender = new ListAppender<>();
        appender.start();
        handlerLogger.addAppender(appender);
        // M-03 重构后 BusinessException 日志由 ExceptionLogger 输出
        exceptionLoggerLogger = (Logger) LoggerFactory.getLogger(ExceptionLogger.class);
        exceptionLoggerLogger.setLevel(Level.DEBUG);
        exceptionLoggerLogger.addAppender(appender);
    }

    @AfterEach
    void detachLogAppender() {
        handlerLogger.detachAppender(appender);
        exceptionLoggerLogger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void handleOpenAiUnauthorizedException_shouldReturnGenericAiCredentialMessage() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/projects/1/tender-breakdown");
        ErrorObject error = ErrorObject.builder()
                .code("invalid_api_key")
                .message("Authentication Fails, Your api key: ****2f99 is invalid")
                .param("api_key")
                .type("invalid_request_error")
                .build();
        UnauthorizedException exception = UnauthorizedException.builder()
                .headers(Headers.builder().build())
                .error(error)
                .build();

        ResponseEntity<ApiResponse<Void>> response = handler.handleOpenAiUnauthorizedException(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(502);
        assertThat(response.getBody().getMessage()).contains("AI provider API Key 无效或已失效");
        assertThat(response.getBody().getMessage()).doesNotContain("DeepSeek");
        assertThat(response.getBody().getMessage()).doesNotContain("2f99");
    }

    @Test
    void handleResourceNotFoundException_shouldReturn404() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/forms/tender.entry");
        ResourceNotFoundException exception = new ResourceNotFoundException("FormDefinition", "tender.entry");

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleResourceNotFoundException(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(404);
        assertThat(response.getBody().getMessage()).isEqualTo("请求的资源不存在");
    }

    @Test
    void handleResourceNotFoundException_messageOnly_shouldReturn404() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/bid-results/fetch-results/confirm-batch");
        ResourceNotFoundException exception = new ResourceNotFoundException("Bid result fetch record not found: 999999");

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleResourceNotFoundException(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(404);
        assertThat(response.getBody().getMessage()).isEqualTo("请求的资源不存在");
    }

    @Test
    void handleResponseStatusException_shouldReturnCorrectStatus() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/resource/999");
        ResponseStatusException exception = new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Resource not found");

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleResponseStatusException(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(404);
        assertThat(response.getBody().getMessage()).isEqualTo("Resource not found");
    }

    @Test
    void handleResponseStatusException_conflictStatus() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/tenders");
        ResponseStatusException exception = new ResponseStatusException(
                HttpStatus.CONFLICT, "Duplicate entry");

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleResponseStatusException(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(409);
    }

    @Test
    void handleResponseStatusException_nullReason_shouldUseFallbackMessage() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/resource/1");
        ResponseStatusException exception = new ResponseStatusException(HttpStatus.NOT_FOUND);

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleResponseStatusException(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(404);
        assertThat(response.getBody().getMessage()).isEqualTo("请求无法处理");
    }

    @Test
    void handleTenderDuplicateException_shouldReturn400WithMessage() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/tenders");
        Tender duplicate = Tender.builder()
                .id(1L)
                .title("已有标讯")
                .purchaserName("测试采购人")
                .registrationDeadline(LocalDateTime.of(2026, 7, 1, 12, 0))
                .bidOpeningTime(LocalDateTime.of(2026, 7, 15, 10, 0))
                .build();
        TenderDuplicateException exception = new TenderDuplicateException(List.of(duplicate));

        ResponseEntity<ApiResponse<Void>> response = handler.handleTenderDuplicate(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(400);
        assertThat(response.getBody().getMessage()).isEqualTo("投标管理系统该标讯已存在");
        assertThat(response.getBody().getData()).isNull();
    }

    // ============ CO-442: BusinessException HttpStatus 透传 ============
    // 修复前：handleBusinessException 硬编码 HttpStatus.BAD_REQUEST，忽略 ex.getHttpStatus()
    // 修复后：使用 ex.getHttpStatus()，确保 409/403/423 等业务码返回正确的 HTTP 状态码

    @Test
    void handleBusinessException_shouldReturnHttpStatusFromException_409() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects/112/documents/401/download");
        BusinessException exception = new BusinessException(409, "投标文件已进入「结项」阶段，文件只读不可下载");

        ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(409);
        assertThat(response.getBody().getMessage()).contains("投标文件");
    }

    @Test
    void handleBusinessException_shouldReturnHttpStatusFromException_400() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/example");
        BusinessException exception = new BusinessException("参数错误");

        ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(400);
    }

    // ============ CO-442: AppFailureException HttpStatus 透传（同类修复） ============
    // 修复前：handleAppFailureException 硬编码 HttpStatus.BAD_REQUEST，忽略 ex.getHttpStatus()
    // 修复后：使用 ex.getHttpStatus()，确保 RetryableOperationException(429) 等子类返回正确状态码

    @Test
    void handleAppFailureException_shouldReturnHttpStatusFromException_429() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/example");
        RetryableOperationException exception = new RetryableOperationException(
                429, HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁，请稍后重试");

        ResponseEntity<ApiResponse<Void>> response = handler.handleAppFailureException(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(429);
        assertThat(response.getBody().getMessage()).contains("请求过于频繁");
    }

    @Test
    void handleAppFailureException_shouldReturnHttpStatusFromException_503() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/example");
        BusinessUnavailableException exception = new BusinessUnavailableException(
                503, HttpStatus.SERVICE_UNAVAILABLE, "业务暂不可用");

        ResponseEntity<ApiResponse<Void>> response = handler.handleAppFailureException(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(503);
    }

    // ============ US3 Phase 5 (T019): 5xx handler 诊断标准验证 ============
    // Constitution v2.0.0 Principle VII §3: 5xx handler 必须
    //   1. log.error 打印完整堆栈（非 log.warn）
    //   2. 打印 Payload/Query（getRequestPayload）
    //   3. Sentry.captureException 上报
    // 项目已切换到 mock-maker-inline（Mockito 5.12 默认），支持 mockStatic Sentry。

    @Test
    void handleOptimisticLockingFailureException_shouldLogErrorLevelWithStackTrace() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/evaluation/1");
        OptimisticLockingFailureException exception =
                new OptimisticLockingFailureException("Row was updated or deleted by another transaction");

        ResponseEntity<ApiResponse<Void>> response;
        try (MockedStatic<Sentry> sentry = mockStatic(Sentry.class)) {
            response = handler.handleOptimisticLockingFailureException(exception, request);
            // 诊断标准 3：必须上报 Sentry
            sentry.verify(() -> Sentry.captureException(exception));
        }

        // API 契约不变：409 + 评估表消息
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(409);
        assertThat(response.getBody().getMessage()).contains("评估表已被更新");

        // 诊断标准 1：必须使用 ERROR 级别日志（非 WARN）
        boolean hasErrorLevelLog = appender.list.stream()
                .anyMatch(event -> event.getLevel().equals(Level.ERROR));
        assertThat(hasErrorLevelLog)
                .as("handleOptimisticLockingFailureException 应使用 log.error 而非 log.warn")
                .isTrue();

        // 诊断标准 2：日志应包含 Payload（Query/Body）
        boolean logContainsPayload = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .anyMatch(msg -> msg.contains("Payload:"));
        assertThat(logContainsPayload)
                .as("handleOptimisticLockingFailureException 应打印 Payload")
                .isTrue();
    }

    @Test
    void handleOptimisticLockingFailureException_nonEvaluationUri_shouldLogErrorAndDefaultMessage() {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/tenders/42");
        OptimisticLockingFailureException exception =
                new OptimisticLockingFailureException("并发冲突");

        ResponseEntity<ApiResponse<Void>> response;
        try (MockedStatic<Sentry> sentry = mockStatic(Sentry.class)) {
            response = handler.handleOptimisticLockingFailureException(exception, request);
            sentry.verify(() -> Sentry.captureException(exception));
        }

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getCode()).isEqualTo(409);
        assertThat(response.getBody().getMessage()).isEqualTo("数据已被其他用户更新，请刷新后重试");

        boolean hasErrorLevelLog = appender.list.stream()
                .anyMatch(event -> event.getLevel().equals(Level.ERROR));
        assertThat(hasErrorLevelLog)
                .as("handleOptimisticLockingFailureException 应使用 log.error")
                .isTrue();
    }

    @Test
    void handleGlobalException_shouldLogErrorLevelWithStackTrace() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/test");
        Exception exception = new RuntimeException("NPE 模拟");

        ResponseEntity<ApiResponse<Void>> response;
        try (MockedStatic<Sentry> sentry = mockStatic(Sentry.class)) {
            response = handler.handleGlobalException(exception, request);
            // 诊断标准 3：必须上报 Sentry
            sentry.verify(() -> Sentry.captureException(exception));
        }

        // API 契约不变：500 + 通用消息
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(500);
        assertThat(response.getBody().getMessage()).isEqualTo("系统繁忙，请稍后重试");

        // 诊断标准 1：必须使用 ERROR 级别日志（非 WARN）
        boolean hasErrorLevelLog = appender.list.stream()
                .anyMatch(event -> event.getLevel().equals(Level.ERROR));
        assertThat(hasErrorLevelLog)
                .as("handleGlobalException 应使用 log.error 而非 log.warn")
                .isTrue();

        // 诊断标准 2：日志应包含 Payload
        boolean logContainsPayload = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .anyMatch(msg -> msg.contains("Payload:"));
        assertThat(logContainsPayload)
                .as("handleGlobalException 应打印 Payload")
                .isTrue();
    }

    // ============ C1 fix: handleIllegalStateException 诊断对齐 ============
    // Constitution v2.0.0 Principle VII §3: IllegalStateException 是本次事件根因异常，
    // 必须完整诊断 + Sentry 上报 + 通用错误信息（不暴露 Duplicate key 内部细节）。

    @Test
    void handleIllegalStateException_shouldLogErrorAndReportToSentryAndReturnGenericMessage() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/tenders");
        IllegalStateException exception =
                new IllegalStateException("Duplicate key 937 (attempted merging values 585 and 7246)");

        ResponseEntity<ApiResponse<Void>> response;
        try (MockedStatic<Sentry> sentry = mockStatic(Sentry.class)) {
            response = handler.handleIllegalStateException(exception, request);
            // 诊断标准 3：必须上报 Sentry
            sentry.verify(() -> Sentry.captureException(exception));
        }

        // 409 状态码保留（contracts/no-new-contracts.md 契约）
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getCode()).isEqualTo(409);
        // 通用错误信息，不暴露 "Duplicate key 937..." 内部细节
        assertThat(response.getBody().getMessage()).isEqualTo("系统状态冲突，请刷新后重试");
        assertThat(response.getBody().getMessage()).doesNotContain("Duplicate key");

        // 诊断标准 1：必须使用 ERROR 级别日志（非 WARN）
        boolean hasErrorLevelLog = appender.list.stream()
                .anyMatch(event -> event.getLevel().equals(Level.ERROR));
        assertThat(hasErrorLevelLog)
                .as("handleIllegalStateException 应使用 log.error 而非 log.warn")
                .isTrue();

        // 诊断标准 2：日志应包含 Payload
        boolean logContainsPayload = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .anyMatch(msg -> msg.contains("Payload:"));
        assertThat(logContainsPayload)
                .as("handleIllegalStateException 应打印 Payload")
                .isTrue();
    }

    // ============ CO-507: handleAccessDeniedException 4xx 路径（业务可恢复错误） ============
    // 对称于 handleIllegalStateException 5xx 诊断标准：业务权限校验失败应走 4xx 路径，
    // WARN 级日志，不上报 Sentry，避免噪声。

    @Test
    void handleAccessDeniedException_shouldReturn403AndNotReportToSentry() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/platform/accounts/8/password");
        AccessDeniedException exception =
                new AccessDeniedException("Only administrators or the account's contact person can view the password");

        ResponseEntity<ApiResponse<Void>> response;
        try (MockedStatic<Sentry> sentry = mockStatic(Sentry.class)) {
            response = handler.handleAccessDeniedException(exception, request);
            // 4xx 业务异常不得上报 Sentry（区别于 5xx 系统缺陷）
            sentry.verifyNoInteractions();
        }

        // 4xx 状态码（403）
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getCode()).isEqualTo(403);
        // 通用错误信息，不暴露具体权限细节
        assertThat(response.getBody().getMessage()).isEqualTo("权限不足，无法访问该资源");
        assertThat(response.getBody().getMessage()).doesNotContain("contact person");

        // 4xx 应使用 WARN 级别日志（非 ERROR）
        boolean hasErrorLevelLog = appender.list.stream()
                .anyMatch(event -> event.getLevel().equals(Level.ERROR));
        assertThat(hasErrorLevelLog)
                .as("handleAccessDeniedException 应使用 log.warn 而非 log.error")
                .isFalse();
        boolean hasWarnLevelLog = appender.list.stream()
                .anyMatch(event -> event.getLevel().equals(Level.WARN));
        assertThat(hasWarnLevelLog)
                .as("handleAccessDeniedException 应使用 log.warn")
                .isTrue();
    }

    @Test
    void handleBusinessException_5xx_shouldReportToSentry() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/example");
        BusinessException exception = new BusinessException(503, "服务不可用");

        try (MockedStatic<Sentry> sentry = mockStatic(Sentry.class)) {
            handler.handleBusinessException(exception, request);
            // 5xx BusinessException 必须上报 Sentry
            sentry.verify(() -> Sentry.captureException(exception));
        }

        boolean hasErrorLevelLog = appender.list.stream()
                .anyMatch(event -> event.getLevel().equals(Level.ERROR));
        assertThat(hasErrorLevelLog)
                .as("5xx BusinessException 应使用 log.error")
                .isTrue();
    }

    @Test
    void handleBusinessException_4xx_shouldNotReportToSentry() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/example");
        BusinessException exception = new BusinessException(409, "业务冲突");

        try (MockedStatic<Sentry> sentry = mockStatic(Sentry.class)) {
            handler.handleBusinessException(exception, request);
            // 4xx 业务错误不应上报 Sentry
            sentry.verifyNoInteractions();
        }

        boolean hasWarnLevelLog = appender.list.stream()
                .anyMatch(event -> event.getLevel().equals(Level.WARN));
        assertThat(hasWarnLevelLog)
                .as("4xx BusinessException 应使用 log.warn 而非 log.error")
                .isTrue();
    }

    // ============ CO-529: MissingServletRequestPartException 处理验证 ============
    // CO-519 的 PR #1779 用 if (ex instanceof MissingServletRequestPartException) 判断在
    // handleGlobalException 中，永远不被执行（父类 ResponseEntityExceptionHandler 的
    // handleException(Exception) 是 public final，已绑定该异常，子类 @ExceptionHandler(Exception.class)
    // 无法覆盖它）。CO-529 通过重写父类 protected handleMissingServletRequestPart 修复。

    @Test
    void handleMissingServletRequestPart_shouldReturn400WithFriendlyMessage() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("POST", "/api/projects/42/documents");
        ServletWebRequest request = new ServletWebRequest(servletRequest);
        MissingServletRequestPartException exception =
                new MissingServletRequestPartException("file");

        ResponseEntity<Object> response = handler.handleMissingServletRequestPart(
                exception, new HttpHeaders(), HttpStatus.BAD_REQUEST, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isInstanceOf(ApiResponse.class);
        @SuppressWarnings("unchecked")
        ApiResponse<Void> body = (ApiResponse<Void>) response.getBody();
        assertThat(body.getCode()).isEqualTo(400);
        // 提示包含缺失的 part 名，便于用户定位
        assertThat(body.getMessage()).contains("file");
        // M-03 重构后日志走 ExceptionLogger（统一格式 "业务异常 - URI: ..., Code: ..., Message: ..."）
        // 验证 ExceptionLogger logger 输出了 WARN 级别日志
        boolean hasWarnLog = appender.list.stream()
                .anyMatch(event -> event.getLevel().equals(Level.WARN)
                        && event.getFormattedMessage().contains("业务异常"));
        assertThat(hasWarnLog)
                .as("handleMissingServletRequestPart 应通过 ExceptionLogger 输出 WARN 级别日志")
                .isTrue();
    }

    @Test
    void handleMissingServletRequestPart_blankPartName_shouldFallbackToGenericMessage() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("POST", "/api/projects/42/documents");
        ServletWebRequest request = new ServletWebRequest(servletRequest);
        // 构造一个 part 名为空的异常（边界场景）
        MissingServletRequestPartException exception =
                new MissingServletRequestPartException("");

        ResponseEntity<Object> response = handler.handleMissingServletRequestPart(
                exception, new HttpHeaders(), HttpStatus.BAD_REQUEST, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        @SuppressWarnings("unchecked")
        ApiResponse<Void> body = (ApiResponse<Void>) response.getBody();
        assertThat(body.getCode()).isEqualTo(400);
        // part 名为空时，回退到通用提示
        assertThat(body.getMessage()).isEqualTo("请上传文件");
    }

    @Test
    void handleMissingServletRequestParameter_shouldReturn400WithParameterName() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/api/projects/42/documents");
        ServletWebRequest request = new ServletWebRequest(servletRequest);
        MissingServletRequestParameterException exception =
                new MissingServletRequestParameterException("projectId", "Long");

        ResponseEntity<Object> response = handler.handleMissingServletRequestParameter(
                exception, new HttpHeaders(), HttpStatus.BAD_REQUEST, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        @SuppressWarnings("unchecked")
        ApiResponse<Void> body = (ApiResponse<Void>) response.getBody();
        assertThat(body.getCode()).isEqualTo(400);
        assertThat(body.getMessage()).contains("projectId");
        // M-03 重构后日志走 ExceptionLogger（统一格式 "业务异常 - URI: ..., Code: ..., Message: ..."）
        boolean hasWarnLog = appender.list.stream()
                .anyMatch(event -> event.getLevel().equals(Level.WARN)
                        && event.getFormattedMessage().contains("业务异常"));
        assertThat(hasWarnLog)
                .as("handleMissingServletRequestParameter 应通过 ExceptionLogger 输出 WARN 级别日志")
                .isTrue();
    }
}
