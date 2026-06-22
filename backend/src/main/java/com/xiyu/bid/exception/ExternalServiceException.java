package com.xiyu.bid.exception;

import org.springframework.http.HttpStatus;

/**
 * 上游/外部系统调用失败时抛出的异常。
 *
 * <p>关键特性：
 * <ol>
 *   <li>保留上游的 HTTP 状态码，用于区分"上游余额不足 (402)"、"调用过于频繁 (429)"、"API Key 无效 (401/403)" 等具体问题。</li>
 *   <li>{@link GlobalExceptionHandler} 根据 upstreamStatusCode 决定我们向前端返回的 HTTP 状态，不再笼统打 500。</li>
 *   <li>{@code userFriendlyMessage} 是给前端用户看的中文说明，不暴露内部细节；upstreamRawMessage 只用于日志调试。</li>
 *   <li>继承 {@link RuntimeException}，不侵入现有 try-catch。</li>
 * </ol>
 *
 * <p>典型使用场景：
 * <pre>
 *   try {
 *       restTemplate.post(...);
 *   } catch (HttpStatusCodeException ex) {
 *       throw ExternalServiceException.forService("AI API", ex.getStatusCode().value(),
 *           "余额不足，请充值后重试", ex.getResponseBodyAsString(), ex);
 *   }
 * </pre>
 */
public class ExternalServiceException extends RuntimeException {

    /** 上游/外部系统名称（如 "AI 厂商 API"、"泛微 OA"、"企微"）。 */
    private final String serviceName;

    /** 上游返回的 HTTP 状态码；-1 表示无法获取（纯网络异常等）。 */
    private final int upstreamStatusCode;

    /** 给前端用户看的友好错误消息（中文，不暴露内部实现细节）。 */
    private final String userFriendlyMessage;

    /** 上游原始错误内容（仅用于日志/排查，不暴露给前端）。 */
    private final String upstreamRawMessage;

    /** 我们向用户暴露的 HTTP 状态；若为 null，GlobalExceptionHandler 会根据 upstreamStatusCode 推断。 */
    private final Integer suggestedHttpStatus;

    public static ExternalServiceException forService(String serviceName, int upstreamStatusCode,
                                                    String userFriendlyMessage, String upstreamRawMessage,
                                                    Throwable cause) {
        return new ExternalServiceException(serviceName, upstreamStatusCode, userFriendlyMessage, upstreamRawMessage, null, cause);
    }

    public static ExternalServiceException networkError(String serviceName, String userFriendlyMessage, Throwable cause) {
        return new ExternalServiceException(serviceName, -1, userFriendlyMessage, cause.getMessage(), null, cause);
    }

    public static ExternalServiceException withStatus(String serviceName, int upstreamStatusCode,
                                                     String userFriendlyMessage, Throwable cause) {
        return new ExternalServiceException(serviceName, upstreamStatusCode, userFriendlyMessage, null, null, cause);
    }

    private ExternalServiceException(String serviceName, int upstreamStatusCode, String userFriendlyMessage,
                                      String upstreamRawMessage, Integer suggestedHttpStatus, Throwable cause) {
        super(userFriendlyMessage, cause);
        this.serviceName = serviceName;
        this.upstreamStatusCode = upstreamStatusCode;
        this.userFriendlyMessage = userFriendlyMessage;
        this.upstreamRawMessage = upstreamRawMessage;
        this.suggestedHttpStatus = suggestedHttpStatus;
    }

    public String getServiceName() { return serviceName; }

    public int getUpstreamStatusCode() { return upstreamStatusCode; }

    public String getUserFriendlyMessage() { return userFriendlyMessage; }

    public String getUpstreamRawMessage() { return upstreamRawMessage; }

    /**
     * 根据上游状态码，映射到我们要返回给用户的 HTTP 状态。
     *
     * <p>映射规则：
     * <ul>
     *   <li>402 → 402 Payment Required</li>
     *   <li>401/403 → 502 Bad Gateway（是配置问题，需要管理员调整</li>
     *   <li>400 → 400 Bad Request（是我们的请求格式有问题）</li>
     *   <li>429 → 429 Too Many Requests</li>
     *   <li>500/502/503/504 → 503 Service Unavailable</li>
     *   <li>其他 → 502 Bad Gateway</li>
     * </ul>
     */
    public HttpStatus resolveHttpStatus() {
        if (suggestedHttpStatus != null) return HttpStatus.valueOf(suggestedHttpStatus);
        int s = upstreamStatusCode;
        if (s == 402) return HttpStatus.PAYMENT_REQUIRED;
        if (s == 400) return HttpStatus.BAD_REQUEST;
        if (s == 401 || s == 403) return HttpStatus.BAD_GATEWAY;
        if (s == 429) return HttpStatus.TOO_MANY_REQUESTS;
        if (s >= 500 && s <= 504) return HttpStatus.SERVICE_UNAVAILABLE;
        if (s == -1) return HttpStatus.BAD_GATEWAY; // 网络异常：上游无法到达
        return HttpStatus.BAD_GATEWAY;
    }
}
