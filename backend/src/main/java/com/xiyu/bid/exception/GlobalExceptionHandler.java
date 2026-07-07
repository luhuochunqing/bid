// Input: 业务失败、资源缺失和参数校验异常
// Output: 业务异常类型与标准化错误映射
// Pos: Exception/异常处理层
// 维护声明: 仅维护异常路由分发；日志走 ExceptionLogger、消息走 ExceptionMessageSanitizer/ExceptionResponseStrategy.
package com.xiyu.bid.exception;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.docinsight.application.exception.DocumentNotFoundException;
import com.xiyu.bid.docinsight.application.exception.UnsupportedProfileException;
import com.openai.errors.UnauthorizedException;
import com.openai.errors.BadRequestException;
import com.xiyu.bid.integration.application.WeComApiException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;

/**
 * 全局异常处理器（仅做异常路由分发）.
 *
 * <p>M-03 安全修复：所有 handler 不再直接透传 ex.getMessage()；
 * 日志和 Sentry 上报统一走 {@link ExceptionLogger}；
 * 响应消息构建统一走 {@link ExceptionResponseStrategy} 和 {@link ExceptionMessageSanitizer}。
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * 处理参数校验异常 (@Valid)。
     * 校验错误来自 Bean Validation，受控、有限、可透传给用户。
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        ExceptionLogger.warn(ex, currentHttpRequest(request), 400);
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(400, "参数校验失败: " + errors));
    }

    /**
     * 处理约束违反异常 (@Validated)。受控校验消息可透传。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(
            ConstraintViolationException ex,
            HttpServletRequest request) {
        String errors = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        ExceptionLogger.warn(ex, request, 400);
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(400, "参数校验失败: " + errors));
    }

    /**
     * 处理非法参数异常。
     * M-03: message 可能含 SQL/字段名等敏感信息，禁止透传，统一返回硬编码消息。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(
            IllegalArgumentException ex,
            HttpServletRequest request) {
        ExceptionLogger.warn(ex, request, 400);
        // 通过 ExceptionResponseStrategy.buildResponse 调用 ExceptionMessageSanitizer.sanitize(ex)
        // 安全的业务校验消息（如"产品类型不能为空"）会透传，含 SQL/路径/堆栈的敏感消息会被拦截
        return ResponseEntity.badRequest()
                .body(ExceptionResponseStrategy.buildResponse(ex, HttpStatus.BAD_REQUEST));
    }

    /**
     * 处理非法状态异常（5xx 系统级失败）。
     * Constitution v2.0.0 Principle VII §3: 必须完整诊断 + Sentry 上报。
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalStateException(
            IllegalStateException ex,
            HttpServletRequest request) {
        ExceptionLogger.errorWithSentry(ex, request, "非法状态");
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(409, "系统状态冲突，请刷新后重试"));
    }

    /**
     * 处理乐观锁冲突（5xx 系统级失败）。
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLockingFailureException(
            OptimisticLockingFailureException ex,
            HttpServletRequest request) {
        ExceptionLogger.errorWithSentry(ex, request, "并发更新冲突");
        String message = (request.getRequestURI() != null
                && request.getRequestURI().contains("/evaluation"))
                ? "评估表已被更新，请刷新后重试"
                : "数据已被其他用户更新，请刷新后重试";
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(409, message));
    }

    /**
     * 处理认证异常（Spring Security AuthenticationException 体系）。
     * M-03: 不透传 ex.getMessage()，统一硬编码友好消息。
     * RoleNotAuthorizedException 是项目自定义受控异常，单独处理。
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(
            AuthenticationException ex,
            HttpServletRequest request) {
        ExceptionLogger.warnWithIp(ex, request);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ExceptionResponseStrategy.buildFixedResponse(
                        HttpStatus.UNAUTHORIZED, "认证失败，请重新登录"));
    }

    /**
     * 处理授权异常。
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(
            AccessDeniedException ex,
            HttpServletRequest request) {
        ExceptionLogger.warnWithIp(ex, request);
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ExceptionResponseStrategy.buildFixedResponse(
                        HttpStatus.FORBIDDEN, "权限不足，无法访问该资源"));
    }

    /**
     * 处理错误凭据异常。
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentialsException(
            BadCredentialsException ex,
            HttpServletRequest request) {
        ExceptionLogger.warnWithIp(ex, request);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ExceptionResponseStrategy.buildFixedResponse(
                        HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED: 用户名或密码错误"));
    }

    /**
     * 处理账户已停用异常。
     */
    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiResponse<Void>> handleDisabledException(
            DisabledException ex,
            HttpServletRequest request) {
        ExceptionLogger.warnWithIp(ex, request);
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ExceptionResponseStrategy.buildFixedResponse(
                        HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED: 账户已停用"));
    }

    /**
     * 处理角色未授权异常（项目自定义受控异常）。
     * M-03: 走 ExceptionResponseStrategy 安全策略。
     */
    @ExceptionHandler(RoleNotAuthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleRoleNotAuthorizedException(
            RoleNotAuthorizedException ex,
            HttpServletRequest request) {
        ExceptionLogger.warnWithIp(ex, request);
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ExceptionResponseStrategy.buildWithPrefix(
                        ex, HttpStatus.FORBIDDEN, "ROLE_NOT_AUTHORIZED"));
    }

    /**
     * 处理认证不充分异常。
     * M-03: InsufficientAuthenticationException 可能由 AuthService 抛出，
     * message 可能含 "Refresh token"、"JWT expired" 等敏感信息，禁止透传。
     * 统一返回硬编码 "认证失败" 消息。
     */
    @ExceptionHandler(InsufficientAuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleInsufficientAuthenticationException(
            InsufficientAuthenticationException ex,
            HttpServletRequest request) {
        ExceptionLogger.warnWithIp(ex, request);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ExceptionResponseStrategy.buildFixedResponse(
                        HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED: 认证失败"));
    }

    /**
     * 处理标讯重复异常（项目自定义受控异常）。
     */
    @ExceptionHandler(TenderDuplicateException.class)
    public ResponseEntity<ApiResponse<Void>> handleTenderDuplicate(
            TenderDuplicateException ex,
            HttpServletRequest request) {
        ExceptionLogger.warn(ex, request, 400);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ExceptionResponseStrategy.buildResponse(ex, HttpStatus.BAD_REQUEST));
    }

    /**
     * 处理业务异常。
     * Constitution Principle VII §3: 5xx 视为系统级失败需 Sentry 上报。
     * M-03: 走 ExceptionResponseStrategy 安全策略，受控 userMessage 可透传。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException ex,
            HttpServletRequest request) {
        HttpStatus httpStatus = ex.getHttpStatus();
        if (ExceptionLogger.shouldReportToSentry(ex, httpStatus)) {
            ExceptionLogger.errorWithSentry(ex, request, "业务异常(5xx)");
        } else {
            ExceptionLogger.warn(ex, request, ex.getCode());
        }
        return ResponseEntity.status(httpStatus)
                .body(ExceptionResponseStrategy.buildResponse(ex, httpStatus, ex.getCode()));
    }

    /**
     * 处理应用层失败异常（项目自定义受控异常基类）。
     */
    @ExceptionHandler(AppFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleAppFailureException(
            AppFailureException ex,
            HttpServletRequest request) {
        HttpStatus httpStatus = ex.getHttpStatus();
        if (ExceptionLogger.shouldReportToSentry(ex, httpStatus)) {
            ExceptionLogger.errorWithSentry(ex, request, "应用层异常");
        } else {
            ExceptionLogger.warn(ex, request, ex.getCode());
        }
        return ResponseEntity.status(httpStatus)
                .body(ExceptionResponseStrategy.buildResponse(ex, httpStatus, ex.getCode()));
    }

    /**
     * 处理资源不存在异常。M-03: 统一返回硬编码消息，不透传内部细节。
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFoundException(
            ResourceNotFoundException ex,
            HttpServletRequest request) {
        ExceptionLogger.warn(ex, request, 404);
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ExceptionResponseStrategy.buildFixedResponse(
                        HttpStatus.NOT_FOUND, "请求的资源不存在"));
    }

    /**
     * 处理 ResponseStatusException（Controller 中主动抛出的带 HTTP 状态码的异常）。
     * M-03: ex.getReason() 由 Controller 代码设置（受控），可透传；
     * 为空时使用硬编码 fallback。
     */
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatusException(
            org.springframework.web.server.ResponseStatusException ex,
            HttpServletRequest request) {
        ExceptionLogger.warn(ex, request, ex.getStatusCode().value());
        String reason = ex.getReason();
        String safeMessage = ExceptionMessageSanitizer.resolveOrDefault(
                reason, "请求无法处理");
        return ResponseEntity.status(ex.getStatusCode())
                .body(ApiResponse.error(ex.getStatusCode().value(), safeMessage));
    }

    /**
     * 处理 DocInsight 文档不存在异常 → HTTP 404。
     * M-03: message 含 storagePath 内部路径，禁止透传。
     */
    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleDocumentNotFoundException(
            DocumentNotFoundException ex,
            HttpServletRequest request) {
        ExceptionLogger.warnWithIp(ex, request);
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ExceptionResponseStrategy.buildFixedResponse(
                        HttpStatus.NOT_FOUND, "请求的资源不存在"));
    }

    /**
     * 处理 DocInsight 不支持的分析配置异常 → HTTP 400。
     * M-03: message 含 profileCode 内部细节，禁止透传。
     */
    @ExceptionHandler(UnsupportedProfileException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedProfileException(
            UnsupportedProfileException ex,
            HttpServletRequest request) {
        ExceptionLogger.warnWithIp(ex, request);
        return ResponseEntity.badRequest()
                .body(ExceptionResponseStrategy.buildFixedResponse(
                        HttpStatus.BAD_REQUEST, "不支持的文档分析配置"));
    }

    /**
     * 处理 OpenAI SDK 认证失败（5xx 系统级，需 Sentry 上报）。
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleOpenAiUnauthorizedException(
            UnauthorizedException ex,
            HttpServletRequest request) {
        ExceptionLogger.warnWithSentry(ex, request, "AI provider 认证失败",
                "statusCode=401");
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ExceptionResponseStrategy.buildFixedResponse(
                        HttpStatus.BAD_GATEWAY,
                        "AI provider API Key 无效或已失效，请检查系统设置中的对应 provider key 或服务端环境变量后重启。"));
    }

    /**
     * 处理 OpenAI SDK 4xx 错误。M-03: message 可能含 API Key、模型名等敏感信息，禁止透传。
     */
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleOpenAiBadRequestException(
            BadRequestException ex,
            HttpServletRequest request) {
        ExceptionLogger.warnWithSentry(ex, request, "AI provider 返回 4xx 错误",
                "message=" + ex.getMessage());
        String lower = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        if (lower.contains("rate limit")) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ExceptionResponseStrategy.buildFixedResponse(
                            HttpStatus.TOO_MANY_REQUESTS,
                            "AI provider 请求过于频繁，请稍后重试。"));
        }
        if (lower.contains("insufficient") || lower.contains("balance")) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                    .body(ExceptionResponseStrategy.buildFixedResponse(
                            HttpStatus.PAYMENT_REQUIRED,
                            "AI provider 余额不足，请充值或更换 API Key。"));
        }
        if (lower.contains("invalid") && lower.contains("key")) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ExceptionResponseStrategy.buildFixedResponse(
                            HttpStatus.BAD_GATEWAY,
                            "AI provider API Key 无效，请检查配置。"));
        }
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ExceptionResponseStrategy.buildFixedResponse(
                        HttpStatus.BAD_GATEWAY,
                        ExceptionMessageSanitizer.DEFAULT_AI_PROVIDER_MESSAGE));
    }

    /**
     * 处理外部服务调用失败（AI、企微、泛微 OA 等）。
     * Constitution Principle VII §3: 外部依赖问题需 Sentry 聚合观测。
     * M-03: userFriendlyMessage 受控可透传。
     */
    @ExceptionHandler(ExternalServiceException.class)
    public ResponseEntity<ApiResponse<Void>> handleExternalServiceException(
            ExternalServiceException ex,
            HttpServletRequest request) {
        ExceptionLogger.warnWithSentry(ex, request, "外部服务调用失败",
                "serviceName=" + ex.getServiceName() + ", upstreamStatus=" + ex.getUpstreamStatusCode());
        HttpStatus httpStatus = ex.resolveHttpStatus();
        return ResponseEntity.status(httpStatus)
                .body(ExceptionResponseStrategy.buildResponse(ex, httpStatus));
    }

    /**
     * 处理企微 API 异常。M-03: message 可能含 access_token、企业 ID 等敏感信息，禁止透传。
     */
    @ExceptionHandler(WeComApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleWeComApiException(
            WeComApiException ex,
            HttpServletRequest request) {
        ExceptionLogger.warnWithSentry(ex, request, "企微 API 调用失败",
                "errcode=" + ex.errcode());
        int errcode = ex.errcode();
        if (errcode == 42001 || errcode == 42007 || errcode == 40014 || errcode == 40001) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ExceptionResponseStrategy.buildFixedResponse(
                            HttpStatus.BAD_GATEWAY,
                            "企微 access_token 无效或已过期，请稍后重试或联系管理员刷新配置。"));
        }
        if (errcode == 45009 || errcode == 45047) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ExceptionResponseStrategy.buildFixedResponse(
                            HttpStatus.TOO_MANY_REQUESTS,
                            "企微接口调用过于频繁，请稍后重试。"));
        }
        if (errcode == 60011) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ExceptionResponseStrategy.buildFixedResponse(
                            HttpStatus.BAD_GATEWAY,
                            "企微应用权限不足，请联系管理员检查应用权限配置。"));
        }
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ExceptionResponseStrategy.buildFixedResponse(
                        HttpStatus.BAD_GATEWAY,
                        ExceptionMessageSanitizer.DEFAULT_WECOM_MESSAGE));
    }

    /**
     * 处理所有未捕获的异常（5xx 系统级失败）。
     * 走到这里的是真正的系统缺陷（NPE、SQL 异常等），需完整上报 Sentry。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGlobalException(
            Exception ex,
            HttpServletRequest request) {
        ExceptionLogger.errorWithSentry(ex, request, "系统异常");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ExceptionResponseStrategy.buildFixedResponse(
                        HttpStatus.INTERNAL_SERVER_ERROR, "系统繁忙，请稍后重试"));
    }

    /**
     * 处理请求体不可读异常。
     * M-03: resolveReadableMessage 从 cause 链提取 IAE message，可能含 Jackson 字段名等，禁止透传。
     */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ExceptionLogger.warn(ex, currentHttpRequest(request), 400);
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(400,
                        ExceptionMessageSanitizer.DEFAULT_MALFORMED_REQUEST_MESSAGE));
    }

    /**
     * CO-529: 重写父类的 handleMissingServletRequestPart（父类的 handleException 是 public final，
     * 子类用 @ExceptionHandler 声明会导致 Ambiguous 冲突，只能重写 protected 方法）。
     */
    @Override
    protected ResponseEntity<Object> handleMissingServletRequestPart(
            MissingServletRequestPartException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ExceptionLogger.warn(ex, currentHttpRequest(request), 400);
        String partName = ex.getRequestPartName();
        String message = (partName != null && !partName.isBlank())
                ? "未接收到必填的「" + partName + "」字段，请检查文件是否已正确选择"
                : "请上传文件";
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(400, message));
    }

    /**
     * CO-529: 同上，重写父类的 handleMissingServletRequestParameter 补齐日志和友好提示。
     */
    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ExceptionLogger.warn(ex, currentHttpRequest(request), 400);
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(400, "缺少必填参数: " + ex.getParameterName()));
    }

    /**
     * 从 WebRequest 提取 HttpServletRequest（用于统一传给 ExceptionLogger）。
     * 如果不是 ServletWebRequest 则返回 null（ExceptionLogger 内部会处理 null）。
     */
    private HttpServletRequest currentHttpRequest(WebRequest request) {
        if (request instanceof org.springframework.web.context.request.ServletWebRequest servletWebRequest) {
            return servletWebRequest.getRequest();
        }
        return null;
    }
}
