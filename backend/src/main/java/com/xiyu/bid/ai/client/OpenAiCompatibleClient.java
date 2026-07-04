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

@Service
@Slf4j
public class OpenAiCompatibleClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_ATTEMPTS = 3;
    private static final int MAX_BID_PREVIEW_CONTENT = 3000;
    private static final String SERVICE_NAME = "API";

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
                .maxAttempts(MAX_ATTEMPTS)
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
                            config.providerCode(), context.getRetryCount(), MAX_ATTEMPTS);
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
            ExternalServiceException wrapped = OpenAiCompatibleErrorHandler.translateHttpStatusCodeException(
                    config.providerCode(), SERVICE_NAME, exception);
            if (OpenAiCompatibleErrorHandler.isRetryable(wrapped.getUpstreamStatusCode())) {
                throw new RetryableAiProviderException(wrapped);
            }
            throw wrapped;
        } catch (RuntimeException exception) {
            throw OpenAiCompatibleErrorHandler.translateNetworkException(config.providerCode(), SERVICE_NAME, exception);
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
}
