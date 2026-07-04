package com.xiyu.bid.ai.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.exception.ExternalServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.HttpStatusCodeException;

/**
 * Shared error translation helpers for OpenAI-compatible providers.
 *
 * <p>Package-private utility class used by both chat and embedding clients to keep
 * error handling consistent and avoid duplication.</p>
 */
@Slf4j
final class OpenAiCompatibleErrorHandler {

    private OpenAiCompatibleErrorHandler() {
    }

    static ExternalServiceException translateHttpStatusCodeException(
            String providerCode, String serviceName, HttpStatusCodeException exception) {
        int status = exception.getStatusCode().value();
        String message = buildProviderErrorMessage(providerCode, exception);
        log.warn("AI provider {} request failed: status={}, message={}",
                providerCode, exception.getStatusCode(), message);

        return ExternalServiceException.forService(
                displayName(providerCode) + " " + serviceName,
                status, message,
                exception.getResponseBodyAsString(), exception);
    }

    static ExternalServiceException translateNetworkException(
            String providerCode, String serviceName, RuntimeException exception) {
        String providerName = displayName(providerCode);
        String message = "调用 " + providerName + " " + serviceName + " 失败："
                + (exception.getMessage() == null || exception.getMessage().isBlank()
                ? "未知错误" : exception.getMessage());
        log.warn("AI provider {} request failed: {}", providerCode, exception.getMessage());
        return ExternalServiceException.networkError(providerName + " " + serviceName, message, exception);
    }

    static boolean isRetryable(int status) {
        return status == 429 || status >= 500;
    }

    private static String buildProviderErrorMessage(String providerCode, HttpStatusCodeException exception) {
        String providerName = displayName(providerCode);
        String providerMessage = extractProviderErrorMessage(exception.getResponseBodyAsString());
        int status = exception.getStatusCode().value();
        String statusText = status + " " + exception.getStatusText();

        if (status == 402 && providerMessage != null
                && providerMessage.toLowerCase().contains("insufficient balance")) {
            return providerName + " API 余额不足，请在 " + providerName + " 控制台充值，或更换有余额的 API Key 后再测试。";
        }
        if (status == 401 || status == 403) {
            String detail = (providerMessage != null && !providerMessage.isBlank()) ? "（" + providerMessage + "）" : "";
            return providerName + " API Key 无效或无权限，请检查后台配置的 API Key。" + detail;
        }
        if (status == 429) {
            return providerName + " API 请求过于频繁或额度受限，请稍后重试或检查厂商限额。";
        }
        if (providerMessage != null && !providerMessage.isBlank()) {
            return providerName + " API 请求失败（" + statusText + "）：" + providerMessage;
        }
        return providerName + " API 请求失败（" + statusText + "）。";
    }

    private static String extractProviderErrorMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "";
        }
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode error = root.path("error");
            if (error.isObject()) {
                String message = error.path("message").asText("");
                if (message.isBlank()) {
                    return "";
                }
                String code = error.path("code").asText("");
                return code.isBlank() ? message : message + " (" + code + ")";
            }
            return root.path("message").asText("");
        } catch (JsonProcessingException ignored) {
            return responseBody.length() > 300 ? responseBody.substring(0, 300) : responseBody;
        }
    }

    static String displayName(String providerCode) {
        String code = providerCode == null ? "" : providerCode.trim().toLowerCase();
        return switch (code) {
            case "openai" -> "OpenAI";
            case "deepseek" -> "DeepSeek";
            case "qwen" -> "通义千问";
            case "doubao" -> "豆包";
            default -> providerCode == null || providerCode.isBlank() ? "AI" : providerCode;
        };
    }
}
