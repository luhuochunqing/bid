package com.xiyu.bid.ai.client;

import com.xiyu.bid.config.TraceHeaderInjector;
import com.xiyu.bid.exception.ExternalServiceException;
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
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class OpenAiCompatibleClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static final int MAX_ATTEMPTS = 3;
    private static final int MAX_BID_PREVIEW_CONTENT = 3000;
    private static final String SERVICE_NAME = "API";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final RetryTemplate retryTemplate;
    private final AiPromptBuilder promptBuilder;
    private final AiResponseParser responseParser;
    private final AtomicBoolean responseFormatUnsupported = new AtomicBoolean(false);
    private final AtomicBoolean temperatureFixed = new AtomicBoolean(false);

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
                .maxAttempts(MAX_ATTEMPTS)
                .exponentialBackoff(1000, 2.0, 10000)
                .retryOn(ResourceAccessException.class)
                .retryOn(RetryableAiProviderException.class)
                .traversingCauses()
                .build();
    }

    /** 标书/项目分析 max_tokens：放宽至 4096，容纳多维度深度分析与 thinking 系模型内部推理（lessons #201）. */
    static final int ANALYSIS_MAX_TOKENS = 4096;

    public AiAnalysisResponse analyzeTender(
            AiProviderRuntimeConfig config,
            String content,
            Map<String, Object> context) {
        String prompt = promptBuilder.buildTenderAnalysisPrompt(content, context);
        return responseParser.parseAnalysisResponse(chatCompletionJsonObject(config, prompt, ANALYSIS_MAX_TOKENS));
    }

    public AiAnalysisResponse analyzeProject(
            AiProviderRuntimeConfig config,
            Long projectId,
            Map<String, Object> context) {
        String prompt = promptBuilder.buildProjectAnalysisPrompt(projectId, context);
        return responseParser.parseAnalysisResponse(chatCompletionJsonObject(config, prompt, ANALYSIS_MAX_TOKENS));
    }

    /** 连接测试 max_tokens：需容纳 thinking 系模型的内部推理消耗（lessons #201）. */
    static final int TEST_CONNECTION_MAX_TOKENS = 512;

    public void testConnection(AiProviderRuntimeConfig config) {
        String content = callChatCompletion(config, "Return only the word OK.", TEST_CONNECTION_MAX_TOKENS, false);
        if (content == null || content.isBlank()) {
            // thinking 系模型可能把过小的预算耗在内部推理上，正文为空——不能当连通成功（lessons #201）
            throw new RuntimeException(
                    "AI 返回空内容：max_tokens 预算可能被 thinking 系模型的内部推理消耗（见 lessons #201），"
                            + "请增大 max_tokens 或在模型侧关闭 thinking 后重试");
        }
    }

    /**
     * 通用 chat completion：返回模型原始文本（结构化 JSON 提取场景，spec 049 US2）.
     */
    public String chatCompletion(AiProviderRuntimeConfig config, String prompt, int maxTokens) {
        return callChatCompletion(config, prompt, maxTokens, false);
    }

    /**
     * 结构化 JSON 补全：注入 response_format: {"type": "json_object"}。
     * 若网关返回 400 不支持该参数，将记录单向降级标记并透明重试普通 chatCompletion。
     */
    public String chatCompletionJsonObject(AiProviderRuntimeConfig config, String prompt, int maxTokens) {
        if (responseFormatUnsupported.get()) {
            return callChatCompletion(config, prompt, maxTokens, false);
        }
        try {
            return callChatCompletion(config, prompt, maxTokens, true);
        } catch (ExternalServiceException e) {
            if (isResponseFormatUnsupportedError(e)) {
                log.warn("AI 网关 ({}) 不支持 response_format=json_object 参数，后续请求将直接降级为普通 chatCompletion。错误: {}",
                        config.providerCode(), e.getMessage());
                responseFormatUnsupported.set(true);
                return callChatCompletion(config, prompt, maxTokens, false);
            }
            throw e;
        }
    }

    /** 质量预览 max_tokens：与裁决/提取对齐，容纳 thinking 系模型的内部推理消耗（lessons #201）. */
    static final int PREVIEW_MAX_TOKENS = 4096;

    public BidDocumentQualityAiPreviewDTO previewBidDocumentQuality(
            AiProviderRuntimeConfig config, String documentContent, String tenderText) {
        String doc = truncate(documentContent, MAX_BID_PREVIEW_CONTENT);
        String tender = truncate(tenderText, MAX_BID_PREVIEW_CONTENT);
        String prompt = AiPromptTemplates.BID_PREVIEW_SYSTEM_INSTRUCTION
                + "\n投标文件：" + doc + "\n招标要求：" + tender + "\n"
                + AiPromptTemplates.BID_PREVIEW_OUTPUT_FORMAT;
        return responseParser.parseBidPreview(callChatCompletion(config, prompt, PREVIEW_MAX_TOKENS, false));
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return "";
        return value.length() <= maxLength ? value
                : value.substring(0, maxLength) + "...(已截断)";
    }

    private String callChatCompletion(
            AiProviderRuntimeConfig config, String prompt, int maxTokens, boolean jsonObjectFormat) {
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
                            config.providerCode(), context.getRetryCount(), MAX_ATTEMPTS);
                }
                return doCallChatCompletion(config, prompt, maxTokens, jsonObjectFormat);
            });
        } catch (RetryableAiProviderException e) {
            throw e.getWrapped();
        }
    }

    private String doCallChatCompletion(
            AiProviderRuntimeConfig config, String prompt, int maxTokens, boolean jsonObjectFormat) {
        try {
            return executeChatPost(config, prompt, maxTokens, jsonObjectFormat, !temperatureFixed.get());
        } catch (ExternalServiceException e) {
            if (isTemperatureFixedError(e)) {
                log.warn("AI 网关 ({}) 不支持自定义 temperature（推理/思考系模型），后续请求将不再传入 temperature 参数。错误: {}",
                        config.providerCode(), e.getMessage());
                temperatureFixed.set(true);
                return executeChatPost(config, prompt, maxTokens, jsonObjectFormat, false);
            }
            if (OpenAiCompatibleErrorHandler.isRetryable(e.getUpstreamStatusCode())) {
                throw new RetryableAiProviderException(e);
            }
            throw e;
        }
    }

    private String executeChatPost(
            AiProviderRuntimeConfig config, String prompt, int maxTokens, boolean jsonObjectFormat, boolean includeTemp) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.model());
        requestBody.put("messages", java.util.List.of(
                Map.of("role", "system", "content", "You are an expert bidding consultant analyzing tender opportunities and projects."),
                Map.of("role", "user", "content", prompt)
        ));
        if (includeTemp) requestBody.put("temperature", 0.2);
        requestBody.put("max_tokens", maxTokens);
        if (jsonObjectFormat) requestBody.put("response_format", Map.of("type", "json_object"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(config.apiKey());
        TraceHeaderInjector.inject(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    config.baseUrl(), HttpMethod.POST, new HttpEntity<>(requestBody, headers), String.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return responseParser.extractContentFromResponse(response.getBody());
            }
            throw new RuntimeException("AI API request failed with status: " + response.getStatusCode());
        } catch (HttpStatusCodeException exception) {
            throw OpenAiCompatibleErrorHandler.translateHttpStatusCodeException(
                    config.providerCode(), SERVICE_NAME, exception);
        } catch (RuntimeException exception) {
            throw OpenAiCompatibleErrorHandler.translateNetworkException(config.providerCode(), SERVICE_NAME, exception);
        }
    }

    private boolean isResponseFormatUnsupportedError(ExternalServiceException exception) {
        if (exception.getUpstreamStatusCode() != HttpStatus.BAD_REQUEST.value()) {
            return false;
        }
        String raw = exception.getUpstreamRawMessage() != null ? exception.getUpstreamRawMessage().toLowerCase() : "";
        String msg = exception.getMessage() != null ? exception.getMessage().toLowerCase() : "";
        String combined = raw + " " + msg;
        return combined.contains("response_format")
                || combined.contains("json_object")
                || combined.contains("unsupported parameter")
                || combined.contains("invalid parameter")
                || combined.contains("extra_forbidden")
                || combined.contains("unknown parameter")
                || combined.contains("unrecognized field");
    }

    /**
     * 检测是否为推理模型的 temperature 锁定错误（Kimi K3、OpenAI o1 系列等）。
     *
     * <p>这类模型固定 temperature=1.0，任何外部传入的 temperature 值都会被拒绝，
     * 返回 400 + "temperature is fixed" 或 "temperature is not supported" 等提示。</p>
     */
    private boolean isTemperatureFixedError(ExternalServiceException exception) {
        if (exception.getUpstreamStatusCode() != HttpStatus.BAD_REQUEST.value()) {
            return false;
        }
        String raw = exception.getUpstreamRawMessage() != null ? exception.getUpstreamRawMessage().toLowerCase() : "";
        String msg = exception.getMessage() != null ? exception.getMessage().toLowerCase() : "";
        String combined = raw + " " + msg;
        return combined.contains("temperature is fixed")
                || combined.contains("temperature is not supported")
                || combined.contains("temperature cannot be modified")
                || combined.contains("temperature is not allowed")
                || combined.contains("do not support temperature")
                || combined.contains("does not support temperature");
    }

    boolean isResponseFormatUnsupported() {
        return responseFormatUnsupported.get();
    }

    void resetResponseFormatUnsupported() {
        responseFormatUnsupported.set(false);
    }

    boolean isTemperatureFixed() {
        return temperatureFixed.get();
    }

    void resetTemperatureFixed() {
        temperatureFixed.set(false);
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
}
