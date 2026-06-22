// Input: 业务失败、资源缺失和参数校验异常
// Output: 业务异常类型与标准化错误映射
// Pos: Exception/异常处理层
// 维护声明: 仅维护异常语义与映射；错误码改动请同步前后端契约.
package com.xiyu.bid.exception;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.docinsight.application.exception.DocumentNotFoundException;
import com.xiyu.bid.docinsight.application.exception.UnsupportedProfileException;
import com.openai.errors.UnauthorizedException;
import com.openai.errors.BadRequestException;
import com.xiyu.bid.integration.application.WeComApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * 统一处理所有异常，返回标准格式的错误响应
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理参数校验异常 (@Valid)
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

        log.warn("参数校验失败 - Errors: {}", errors);

        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(400, "参数校验失败: " + errors));
    }

    /**
     * 处理约束违反异常 (@Validated)
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(
            ConstraintViolationException ex,
            HttpServletRequest request) {
        String errors = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));

        log.warn("约束校验失败 - URI: {}, Errors: {}", request.getRequestURI(), errors);

        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(400, "参数校验失败: " + errors));
    }

    /**
     * 处理非法参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(
            IllegalArgumentException ex,
            HttpServletRequest request) {
        log.warn("非法参数 - URI: {}, Message: {}", request.getRequestURI(), ex.getMessage());

        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(400, ex.getMessage()));
    }

    /**
     * 处理非法状态异常
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalStateException(
            IllegalStateException ex,
            HttpServletRequest request) {
        log.warn("非法状态 - URI: {}, Message: {}", request.getRequestURI(), ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(409, ex.getMessage()));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLockingFailureException(
            OptimisticLockingFailureException ex,
            HttpServletRequest request) {
        log.warn("并发更新冲突 - URI: {}, Message: {}", request.getRequestURI(), ex.getMessage());

        String message = "数据已被其他用户更新，请刷新后重试";
        if (request.getRequestURI() != null && request.getRequestURI().contains("/evaluation")) {
            message = "评估表已被更新，请刷新后重试";
        }

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(409, message));
    }

    /**
     * 处理认证异常
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(
            AuthenticationException ex,
            HttpServletRequest request) {
        log.warn("认证失败 - URI: {}, IP: {}", request.getRequestURI(), getClientIp(request));

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(401, "认证失败，请重新登录"));
    }

    /**
     * 处理授权异常
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(
            AccessDeniedException ex,
            HttpServletRequest request) {
        log.warn("权限不足 - URI: {}, User: {}", request.getRequestURI(),
                request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : "anonymous");

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(403, "权限不足，无法访问该资源"));
    }

    /**
     * 处理错误凭据异常
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentialsException(
            BadCredentialsException ex,
            HttpServletRequest request) {
        log.warn("登录失败 - URI: {}, IP: {}", request.getRequestURI(), getClientIp(request));

        // 使用通用错误消息，防止用户枚举攻击
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(401, "用户名或密码错误"));
    }

    /**
     * 处理标讯重复异常
     */
    @ExceptionHandler(TenderDuplicateException.class)
    public ResponseEntity<ApiResponse<Void>> handleTenderDuplicate(
            TenderDuplicateException ex,
            HttpServletRequest request) {
        log.warn("标讯重复 - URI: {}, Duplicates: {}", request.getRequestURI(), ex.getDuplicates().size());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(400, ex.getMessage()));
    }

    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException ex,
            HttpServletRequest request) {
        log.warn("业务异常 - URI: {}, Code: {}, Message: {}",
            request.getRequestURI(), ex.getCode(), ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getCode(), ex.getMessage()));
    }

    /**
     * 处理应用层失败异常（如证书编号重复、等级不能为空等）
     */
    @ExceptionHandler(AppFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleAppFailureException(
            AppFailureException ex,
            HttpServletRequest request) {
        log.warn("应用层异常 - URI: {}, Message: {}",
            request.getRequestURI(), ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(400, ex.getMessage()));
    }

    /**
     * 处理资源不存在异常
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFoundException(
            ResourceNotFoundException ex,
            HttpServletRequest request) {
        String resource = ex.getResource() != null && !ex.getResource().isBlank() ? ex.getResource() : resolveMissingResource(ex);
        String resourceId = ex.getResourceId();
        if (resourceId != null && !resourceId.isBlank()) {
            log.warn("资源不存在 - URI: {}, Resource: {}, ResourceId: {}",
                    request.getRequestURI(), resource, resourceId);
        } else {
            log.warn("资源不存在 - URI: {}, Resource: {}",
                    request.getRequestURI(), resource);
        }

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(404, "请求的资源不存在"));
    }


    /**
     * 处理 ResponseStatusException（Controller 中主动抛出的带 HTTP 状态码的异常）
     */
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatusException(
            org.springframework.web.server.ResponseStatusException ex,
            HttpServletRequest request) {
        log.warn("HTTP {} - URI: {}, Reason: {}",
            ex.getStatusCode(), request.getRequestURI(), ex.getReason());

        int code = switch (ex.getStatusCode().value()) {
            case 400 -> 400;
            case 401 -> 401;
            case 403 -> 403;
            case 404 -> 404;
            case 409 -> 409;
            case 423 -> 423;
            default -> ex.getStatusCode().value();
        };

        return ResponseEntity
                .status(ex.getStatusCode())
                .body(ApiResponse.error(code,
                    ex.getReason() != null ? ex.getReason() : "请求无法处理"));
    }

    /**
     * 处理 DocInsight 文档不存在异常 → HTTP 404
     */
    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleDocumentNotFoundException(
            DocumentNotFoundException ex,
            HttpServletRequest request) {
        log.warn("DocInsight 文档不存在 - URI: {}, Path: {}", request.getRequestURI(), ex.getStoragePath());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(404, ex.getMessage()));
    }

    /**
     * 处理 DocInsight 不支持的分析配置异常 → HTTP 400
     */
    @ExceptionHandler(UnsupportedProfileException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedProfileException(
            UnsupportedProfileException ex,
            HttpServletRequest request) {
        log.warn("DocInsight 不支持的分析配置 - URI: {}, Profile: {}", request.getRequestURI(), ex.getProfileCode());
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(400, ex.getMessage()));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleOpenAiUnauthorizedException(
            UnauthorizedException ex,
            HttpServletRequest request) {
        log.warn("AI provider 认证失败 - URI: {}", request.getRequestURI());

        return ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.error(502, "AI provider API Key 无效或已失效，请检查系统设置中的对应 provider key 或服务端环境变量后重启。"));
    }

    /**
     * 处理 OpenAI SDK 返回的 400 级错误（BadRequest / InvalidRequest / RateLimit 等）。
     * 上游明确拒绝了请求，不是我们的内部异常 —— 把上游返回的错误信息直接透传给前端，给用户可操作的指引。
     */
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleOpenAiBadRequestException(
            BadRequestException ex,
            HttpServletRequest request) {
        String message = ex.getMessage();
        log.warn("AI provider 返回 4xx 错误 - URI: {}, message: {}", request.getRequestURI(), message);

        String lower = message == null ? "" : message.toLowerCase();
        if (lower.contains("rate limit")) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiResponse.error(429, "AI provider 请求过于频繁，请稍后重试。"));
        }
        if (lower.contains("insufficient") || lower.contains("balance")) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                    .body(ApiResponse.error(402, "AI provider 余额不足，请充值或更换 API Key。"));
        }
        if (lower.contains("invalid") && lower.contains("key")) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.error(502, "AI provider API Key 无效，请检查配置。"));
        }
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.error(502, "AI provider 返回错误: " + message));
    }

    /**
     * 处理所有外部/上游服务调用失败 —— 包括 AI API、企微、泛微 OA 等。
     * 关键：根据 {@link ExternalServiceException#getUpstreamStatusCode()} 返回给前端正确的 HTTP 状态码，
     * 而不是统一的 500 "系统繁忙"。
     */
    @ExceptionHandler(ExternalServiceException.class)
    public ResponseEntity<ApiResponse<Void>> handleExternalServiceException(
            ExternalServiceException ex,
            HttpServletRequest request) {
        log.warn("外部服务调用失败 - URI: {}, service: {}, upstreamStatus: {}, message: {}, raw: {}",
                request.getRequestURI(),
                ex.getServiceName(),
                ex.getUpstreamStatusCode(),
                ex.getUserFriendlyMessage(),
                ex.getUpstreamRawMessage());

        HttpStatus httpStatus = ex.resolveHttpStatus();
        return ResponseEntity
                .status(httpStatus)
                .body(ApiResponse.error(httpStatus.value(), ex.getUserFriendlyMessage()));
    }

    /**
     * 处理企微 API 异常（WeComApiException）。
     * 企微异常可能是 HTTP 级别的错误，也可能是 HTTP 200 但业务 errcode != 0 的情况。
     * 把这些信息以友好方式返回给前端，而不是 500。
     */
    @ExceptionHandler(WeComApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleWeComApiException(
            WeComApiException ex,
            HttpServletRequest request) {
        log.warn("企微 API 调用失败 - URI: {}, errcode: {}, message: {}",
                request.getRequestURI(), ex.errcode(), ex.getMessage());

        HttpStatus status = HttpStatus.BAD_GATEWAY;
        int errcode = ex.errcode();
        // 企微常见错误码的有意义映射
        if (errcode == 42001 || errcode == 42007 || errcode == 40014 || errcode == 40001) {
            // access_token 过期/无效/不存在 → 需要重新获取 token
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.error(502, "企微 access_token 无效或已过期，请稍后重试或联系管理员刷新配置。"));
        }
        if (errcode == 45009 || errcode == 45047) {
            // 接口调用超出频率限制
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiResponse.error(429, "企微接口调用过于频繁，请稍后重试。"));
        }
        if (errcode == 60011) {
            // 无权限访问
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.error(502, "企微应用权限不足，请联系管理员检查应用权限配置。"));
        }
        // 默认 502，附带企微返回的原始业务错误信息，便于用户了解具体问题
        String message = ex.getMessage();
        return ResponseEntity.status(status)
                .body(ApiResponse.error(status.value(), "企微服务调用失败: " + message));
    }

    /**
     * 处理所有未捕获的异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGlobalException(
            Exception ex,
            HttpServletRequest request) {
        log.error("系统异常 - URI: {}, IP: {}, Message: {}",
            request.getRequestURI(), getClientIp(request), ex.getMessage(), ex);

        // 不暴露敏感信息给前端
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "系统繁忙，请稍后重试"));
    }

    /**
     * 获取客户端IP地址
     *
     * SECURITY: 使用 getRemoteAddr() 获取客户端IP。
     * 当配置了 server.forward-headers-strategy=NATIVE 时，
     * Spring 会自动从可信转发头中提取正确的客户端IP。
     *
     * 不要直接读取 X-Forwarded-For 或 X-Real-IP，因为客户端可以伪造这些头部。
     */
    private String getClientIp(HttpServletRequest request) {
        // 直接使用 getRemoteAddr() - 最安全的方式
        // 当配置了 forward-headers-strategy=NATIVE 时
        // 会自动返回正确的客户端 IP
        return request.getRemoteAddr();
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        String message = resolveReadableMessage(ex);
        String uri = request.getDescription(false).replace("uri=", "");
        log.warn("请求体不可读 - URI: {}, Message: {}", uri, message, ex);
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(400, message));
    }

    private String resolveReadableMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof IllegalArgumentException illegalArgumentException
                    && illegalArgumentException.getMessage() != null
                    && !illegalArgumentException.getMessage().isBlank()) {
                return illegalArgumentException.getMessage();
            }
            current = current.getCause();
        }
        // 尝试从根因提取更具体的信息
        Throwable root = throwable;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String rootMsg = root.getMessage();
        if (rootMsg != null && !rootMsg.isBlank()) {
            return "请求体格式错误: " + rootMsg;
        }
        return "请求体格式错误";
    }

    private String resolveMissingResource(ResourceNotFoundException ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return "unknown";
        }
        int separatorIndex = message.indexOf(" not found:");
        if (separatorIndex > 0) {
            return message.substring(0, separatorIndex).trim();
        }
        return "unknown";
    }
}
