package com.xiyu.bid.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ExternalServiceException HTTP状态码映射测试。
 *
 * <p>覆盖 resolveHttpStatus() 方法从上游状态码到前端返回状态码的映射逻辑。
 * <p>这是 AI API 402/429 等细粒度异常处理的核心逻辑，直接影响前端用户体验。
 *
 * <p>映射规则（来自 ExternalServiceException.resolveHttpStatus() 注释）：
 * <ul>
 *   <li>402 → 402 Payment Required（余额不足）</li>
 *   <li>400 → 400 Bad Request（请求格式问题）</li>
 *   <li>401/403 → 502 Bad Gateway（配置问题，需管理员调整）</li>
 *   <li>429 → 429 Too Many Requests（调用过于频繁）</li>
 *   <li>500/502/503/504 → 503 Service Unavailable（上游服务异常）</li>
 *   <li>-1 → 502 Bad Gateway（网络异常，上游无法到达）</li>
 *   <li>其他 → 502 Bad Gateway（默认兜底）</li>
 * </ul>
 */
@DisplayName("ExternalServiceException HTTP状态码映射")
class ExternalServiceExceptionTest {

    @Test
    @DisplayName("402 Payment Required → 402（AI余额不足，前端应提示充值）")
    void resolveHttpStatus_402_returnsPaymentRequired() {
        ExternalServiceException ex = ExternalServiceException.forService(
                "AI API", 402, "余额不足，请充值后重试", "Insufficient balance", null);

        assertThat(ex.resolveHttpStatus()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
        assertThat(ex.getUpstreamStatusCode()).isEqualTo(402);
        assertThat(ex.getUserFriendlyMessage()).isEqualTo("余额不足，请充值后重试");
    }

    @Test
    @DisplayName("400 Bad Request → 400（请求格式问题）")
    void resolveHttpStatus_400_returnsBadRequest() {
        ExternalServiceException ex = ExternalServiceException.forService(
                "AI API", 400, "请求参数格式错误", "Invalid request body", null);

        assertThat(ex.resolveHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("401 Unauthorized → 502 Bad Gateway（配置问题，不暴露给用户）")
    void resolveHttpStatus_401_returnsBadGateway() {
        ExternalServiceException ex = ExternalServiceException.forService(
                "AI API", 401, "API配置错误，请联系管理员", "Unauthorized", null);

        assertThat(ex.resolveHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    @DisplayName("403 Forbidden → 502 Bad Gateway（权限问题，不暴露给用户）")
    void resolveHttpStatus_403_returnsBadGateway() {
        ExternalServiceException ex = ExternalServiceException.forService(
                "AI API", 403, "API配置错误，请联系管理员", "Forbidden", null);

        assertThat(ex.resolveHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    @DisplayName("429 Too Many Requests → 429（调用过于频繁）")
    void resolveHttpStatus_429_returnsTooManyRequests() {
        ExternalServiceException ex = ExternalServiceException.forService(
                "AI API", 429, "调用过于频繁，请稍后重试", "Rate limit exceeded", null);

        assertThat(ex.resolveHttpStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("500 Internal Server Error → 503 Service Unavailable（上游服务异常）")
    void resolveHttpStatus_500_returnsServiceUnavailable() {
        ExternalServiceException ex = ExternalServiceException.forService(
                "AI API", 500, "AI服务暂时不可用，请稍后重试", "Internal server error", null);

        assertThat(ex.resolveHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("502 Bad Gateway → 503 Service Unavailable（上游网关错误）")
    void resolveHttpStatus_502_returnsServiceUnavailable() {
        ExternalServiceException ex = ExternalServiceException.forService(
                "AI API", 502, "AI服务暂时不可用，请稍后重试", "Bad gateway", null);

        assertThat(ex.resolveHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("503 Service Unavailable → 503（上游服务不可用）")
    void resolveHttpStatus_503_returnsServiceUnavailable() {
        ExternalServiceException ex = ExternalServiceException.forService(
                "AI API", 503, "AI服务暂时不可用，请稍后重试", "Service unavailable", null);

        assertThat(ex.resolveHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("504 Gateway Timeout → 503 Service Unavailable（上游超时）")
    void resolveHttpStatus_504_returnsServiceUnavailable() {
        ExternalServiceException ex = ExternalServiceException.forService(
                "AI API", 504, "AI服务暂时不可用，请稍后重试", "Gateway timeout", null);

        assertThat(ex.resolveHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("-1 网络异常 → 502 Bad Gateway（上游无法到达）")
    void resolveHttpStatus_networkError_returnsBadGateway() {
        ExternalServiceException ex = ExternalServiceException.networkError(
                "AI API", "网络连接失败，请检查网络设置", new RuntimeException("Connection refused"));

        assertThat(ex.resolveHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(ex.getUpstreamStatusCode()).isEqualTo(-1);
    }

    @Test
    @DisplayName("其他状态码（如 200）→ 502 Bad Gateway（默认兜底）")
    void resolveHttpStatus_unknownStatus_returnsBadGateway() {
        ExternalServiceException ex = ExternalServiceException.forService(
                "AI API", 200, "未知错误", "Unexpected success response", null);

        assertThat(ex.resolveHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    @DisplayName("forService 工厂方法正确设置所有字段")
    void forService_setsAllFieldsCorrectly() {
        RuntimeException cause = new RuntimeException("original cause");
        ExternalServiceException ex = ExternalServiceException.forService(
                "WeCom API", 403, "企微接口调用失败", "Permission denied", cause);

        assertThat(ex.getServiceName()).isEqualTo("WeCom API");
        assertThat(ex.getUpstreamStatusCode()).isEqualTo(403);
        assertThat(ex.getUserFriendlyMessage()).isEqualTo("企微接口调用失败");
        assertThat(ex.getUpstreamRawMessage()).isEqualTo("Permission denied");
        assertThat(ex.getCause()).isEqualTo(cause);
    }

    @Test
    @DisplayName("withStatus 工厂方法简化构造（无原始消息）")
    void withStatus_simplifiedConstruction() {
        RuntimeException cause = new RuntimeException("timeout");
        ExternalServiceException ex = ExternalServiceException.withStatus(
                "CRM API", 504, "CRM服务超时", cause);

        assertThat(ex.getServiceName()).isEqualTo("CRM API");
        assertThat(ex.getUpstreamStatusCode()).isEqualTo(504);
        assertThat(ex.getUserFriendlyMessage()).isEqualTo("CRM服务超时");
        assertThat(ex.getUpstreamRawMessage()).isNull();
        assertThat(ex.getCause()).isEqualTo(cause);
    }
}