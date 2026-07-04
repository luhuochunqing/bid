package com.xiyu.bid.ai.client;

import com.xiyu.bid.config.TraceHeaderInjector;
import com.xiyu.bid.exception.ExternalServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/**
 * OpenAI-compatible embedding API client.
 *
 * <p>Handles embedding endpoint resolution, request retry, and error translation.
 * Kept separate from {@link OpenAiCompatibleClient} to keep each client focused
 * on a single responsibility and to respect the 300-line file budget.</p>
 */
@Slf4j
@Service
public class OpenAiCompatibleEmbeddingClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_ATTEMPTS = 3;
    private static final String SERVICE_NAME = "Embedding API";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final RetryTemplate retryTemplate;

    public OpenAiCompatibleEmbeddingClient(
            RestTemplateBuilder restTemplateBuilder,
            ObjectMapper objectMapper) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(TIMEOUT)
                .setReadTimeout(TIMEOUT)
                .build();
        this.objectMapper = objectMapper;
        this.retryTemplate = RetryTemplate.builder()
                .maxAttempts(MAX_ATTEMPTS)
                .exponentialBackoff(1000, 2.0, 10000)
                .retryOn(ResourceAccessException.class)
                .retryOn(OpenAiCompatibleClient.RetryableAiProviderException.class)
                .traversingCauses()
                .build();
    }

    public float[] embed(AiProviderRuntimeConfig config, String text) {
        validateConfig(config);

        try {
            String responseBody = retryTemplate.execute(context -> {
                if (context.getRetryCount() > 0) {
                    log.info("AI embedding provider {} retry attempt {}/{}",
                            config.providerCode(), context.getRetryCount(), MAX_ATTEMPTS);
                }
                return doCall(config, text);
            });
            return OpenAiEmbeddingResponseParser.parse(responseBody, objectMapper);
        } catch (OpenAiCompatibleClient.RetryableAiProviderException e) {
            throw e.getWrapped();
        }
    }

    private void validateConfig(AiProviderRuntimeConfig config) {
        if (config.apiKey() == null || config.apiKey().isBlank()) {
            throw new IllegalStateException("AI API key is not configured");
        }
        if (config.embeddingModel() == null || config.embeddingModel().isBlank()) {
            throw new IllegalStateException("AI embedding model is not configured");
        }
        String url = resolveEmbeddingBaseUrl(config);
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("AI embedding base URL is not configured");
        }
    }

    private String resolveEmbeddingBaseUrl(AiProviderRuntimeConfig config) {
        if (config.embeddingBaseUrl() != null && !config.embeddingBaseUrl().isBlank()) {
            return config.embeddingBaseUrl();
        }
        String baseUrl = config.baseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (normalized.endsWith("/chat/completions")) {
            normalized = normalized.substring(0, normalized.length() - "/chat/completions".length());
        }
        return normalized + "/embeddings";
    }

    private String doCall(AiProviderRuntimeConfig config, String text) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.embeddingModel());
        requestBody.put("input", text);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(config.apiKey());
        TraceHeaderInjector.inject(headers);

        String url = resolveEmbeddingBaseUrl(config);
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST,
                    new HttpEntity<>(requestBody, headers), String.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            }
            throw new RuntimeException("AI embedding API request failed with status: " + response.getStatusCode());
        } catch (HttpStatusCodeException exception) {
            ExternalServiceException wrapped = OpenAiCompatibleErrorHandler.translateHttpStatusCodeException(
                    config.providerCode(), SERVICE_NAME, exception);
            if (OpenAiCompatibleErrorHandler.isRetryable(wrapped.getUpstreamStatusCode())) {
                throw new OpenAiCompatibleClient.RetryableAiProviderException(wrapped);
            }
            throw wrapped;
        } catch (RuntimeException exception) {
            throw OpenAiCompatibleErrorHandler.translateNetworkException(config.providerCode(), SERVICE_NAME, exception);
        }
    }
}
