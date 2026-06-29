package com.xiyu.bid.ai.client;

import com.xiyu.bid.config.TraceHeaderInjector;
import com.xiyu.bid.exception.ExternalServiceException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.ai.dto.AiAnalysisResponse;
import com.xiyu.bid.ai.dto.BidDocumentQualityAiPreviewDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class OpenAiCompatibleClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final RetryTemplate retryTemplate;
    private final AiPromptBuilder promptBuilder;
    private final AiResponseParser responseParser;

    public OpenAiCompatibleClient(
            RestTemplateBuilder restTemplateBuilder,
            ObjectMapper pObjectMapper,
            AiPromptBuilder promptBuilder,
            AiResponseParser responseParser) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(TIMEOUT)
                .setReadTimeout(TIMEOUT)
                .build();
        this.objectMapper = pObjectMapper;
        this.promptBuilder = promptBuilder;
        this.responseParser = responseParser;
        this.retryTemplate = RetryTemplate.builder()
                .maxAttempts(3)
                .exponentialBackoff(1000, 2.0, 10000)
                .retryOn(ResourceAccessException.class)
                .retryOn(RetryableAiProviderException.class)
                .traversingCauses()
                .build();
    }

    public AiAnalysisResponse analyzeTender(
            AiProviderRuntimeConfig config,
            String content,
            Map<String, Object> context) {
        String prompt = promptBuilder.buildTenderAnalysisPrompt(content, context);
        return responseParser.parseAnalysisResponse(callChatCompletion(config, prompt, 2000));
    }

    public AiAnalysisResponse analyzeProject(
            AiProviderRuntimeConfig config,
            Long projectId,
            Map<String, Object> context) {
        String prompt = promptBuilder.buildProjectAnalysisPrompt(projectId, context);
        return responseParser.parseAnalysisResponse(callChatCompletion(config, prompt, 2000));
    }

    public void testConnection(AiProviderRuntimeConfig config) {
        callChatCompletion(config, "Return only the word OK.", 16);
    }

    private static final int MAX_BID_PREVIEW_CONTENT = 3000;

    public BidDocumentQualityAiPreviewDTO previewBidDocumentQuality(
            AiProviderRuntimeConfig config, String documentContent, String tenderText) {
        String doc = truncate(documentContent, MAX_BID_PREVIEW_CONTENT);
        String tender = truncate(tenderText, MAX_BID_PREVIEW_CONTENT);
        String prompt = AiPromptTemplates.BID_PREVIEW_SYSTEM_INSTRUCTION
                + "\n投标文件：" + doc + "\n招标要求：" + tender + "\n"
                + AiPromptTemplates.BID_PREVIEW_OUTPUT_FORMAT;
        return responseParser.parseBidPreview(callChatCompletion(config, prompt, 1500));
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return "";
        return value.length() <= maxLength ? value
                : value.substring(0, maxLength) + "...(已截断)";
    }

    private String callChatCompletion(AiProviderRuntimeConfig config, String prompt, int maxTokens) {
        if (config.apiKey() == null || config.apiKey().isBlank())
            throw new IllegalStateException("AI API key is not configured");
        if (config.baseUrl() == null || config.baseUrl().isBlank())
            throw new IllegalStateException("AI base URL is not configured");
        if (config.model() == null || config.model().isBlank())
            throw new IllegalStateException("AI model is not configured");

        try {
            return retryTemplate.execute(context -> {
                if (context.getRetryCount() > 0) {
                    log.info("AI provider {} retry attempt {}/{}",
                            config.providerCode(), context.getRetryCount(), 3);
                }
                return doCallChatCompletion(config, prompt, maxTokens);
            });
        } catch (RetryableAiProviderException e) {
            throw e.getWrapped();
        }
    }

    private String doCallChatCompletion(AiProviderRuntimeConfig config, String prompt, int maxTokens) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.model());
        requestBody.put("messages", java.util.List.of(
                Map.of("role", "system", "content", "You are an expert bidding consultant analyzing tender opportunities and projects."),
                Map.of("role", "user", "content", prompt)
        ));
        requestBody.put("temperature", 0.2);
        requestBody.put("max_tokens", maxTokens);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(config.apiKey());
        TraceHeaderInjector.inject(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    config.baseUrl(), HttpMethod.POST,
                    new HttpEntity<>(requestBody, headers), String.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null)
                return responseParser.extractContentFromResponse(response.getBody());
            throw new RuntimeException("AI API request failed with status: " + response.getStatusCode());
        } catch (HttpStatusCodeException exception) {
            int status = exception.getStatusCode().value();
            String message = buildProviderErrorMessage(config.providerCode(), exception);
            log.warn("AI provider {} request failed: status={}, message={}",
                    config.providerCode(), exception.getStatusCode(), message);

            ExternalServiceException wrapped = ExternalServiceException.forService(
                    providerDisplayName(config.providerCode()) + " API",
                    status, message,
                    exception.getResponseBodyAsString(), exception);

            if (status == 429 || status >= 500) {
                throw new RetryableAiProviderException(wrapped);
            }
            throw wrapped;
        } catch (RuntimeException exception) {
            String providerName = providerDisplayName(config.providerCode());
            String message = "调用 " + providerName + " 失败："
                    + (exception.getMessage() == null || exception.getMessage().isBlank() ? "未知错误" : exception.getMessage());
            log.warn("AI provider {} request failed: {}", config.providerCode(), exception.getMessage());
            throw ExternalServiceException.networkError(providerName + " API", message, exception);
        }
    }

    static class RetryableAiProviderException extends RuntimeException {
        private final ExternalServiceException cause;

        RetryableAiProviderException(ExternalServiceException cause) {
            super(cause.getMessage(), cause);
            this.cause = cause;
        }

        public ExternalServiceException getWrapped() {
            return cause;
        }
    }

    private String buildProviderErrorMessage(String providerCode, HttpStatusCodeException exception) {
        String providerName = providerDisplayName(providerCode);
        String providerMessage = extractProviderErrorMessage(exception.getResponseBodyAsString());
        int status = exception.getStatusCode().value();
        String statusText = status + " " + exception.getStatusText();

        if (status == 402 && providerMessage != null && providerMessage.toLowerCase().contains("insufficient balance"))
            return providerName + " API 余额不足，请在 " + providerName + " 控制台充值，或更换有余额的 API Key 后再测试。";
        if (status == 401 || status == 403) {
            String detail = (providerMessage != null && !providerMessage.isBlank()) ? "（" + providerMessage + "）" : "";
            return providerName + " API Key 无效或无权限，请检查后台配置的 API Key。" + detail;
        }
        if (status == 429)
            return providerName + " API 请求过于频繁或额度受限，请稍后重试或检查厂商限额。";
        if (providerMessage != null && !providerMessage.isBlank())
            return providerName + " API 请求失败（" + statusText + "）：" + providerMessage;
        return providerName + " API 请求失败（" + statusText + "）。";
    }

    private String extractProviderErrorMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) return "";
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode error = root.path("error");
            if (error.isObject()) {
                String message = error.path("message").asText("");
                if (message.isBlank()) return "";
                String code = error.path("code").asText("");
                return code.isBlank() ? message : message + " (" + code + ")";
            }
            return root.path("message").asText("");
        } catch (JsonProcessingException ignored) {
            return responseBody.length() > 300 ? responseBody.substring(0, 300) : responseBody;
        }
    }

    private String providerDisplayName(String providerCode) {
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
