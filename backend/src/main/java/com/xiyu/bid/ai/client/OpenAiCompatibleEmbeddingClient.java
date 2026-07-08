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
public class OpenAiCompatibleEmbeddingClient implements EmbeddingClient {

    public static final String DEFAULT_EMBEDDING_MODEL = "qwen3-embedding-8b";

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

    /**
     * Derive the embedding API URL from a chat completions URL.
     * Supports common OpenAI-compatible path patterns:
     * <ul>
     *   <li>{@code /chat/completions} → {@code /embeddings}</li>
     *   <li>{@code /v1} → {@code /v1/embeddings}</li>
     *   <li>other → appends {@code /v1/embeddings}</li>
     * </ul>
     */
    public static String deriveEmbeddingUrl(String chatBaseUrl) {
        if (chatBaseUrl == null || chatBaseUrl.isBlank()) {
            return null;
        }
        String normalized = chatBaseUrl.replaceAll("/$", "");
        if (normalized.endsWith("/chat/completions")) {
            return normalized.replace("/chat/completions", "/embeddings");
        }
        if (normalized.endsWith("/v1")) {
            return normalized + "/embeddings";
        }
        return normalized + "/v1/embeddings";
    }

    @Override
    public float[] embed(AiProviderRuntimeConfig config, String text) {
        AiProviderRuntimeConfig resolved = resolveConfig(config);
        validateConfig(resolved);

        try {
            String responseBody = retryTemplate.execute(context -> {
                if (context.getRetryCount() > 0) {
                    log.info("AI embedding provider {} retry attempt {}/{}",
                            resolved.providerCode(), context.getRetryCount(), MAX_ATTEMPTS);
                }
                return doCall(resolved, text);
            });
            return OpenAiEmbeddingResponseParser.parse(responseBody, objectMapper);
        } catch (OpenAiCompatibleClient.RetryableAiProviderException e) {
            throw e.getWrapped();
        }
    }

    private AiProviderRuntimeConfig resolveConfig(AiProviderRuntimeConfig config) {
        String embeddingBaseUrl = config.embeddingBaseUrl() != null && !config.embeddingBaseUrl().isBlank()
                ? config.embeddingBaseUrl()
                : deriveEmbeddingUrl(config.baseUrl());
        String embeddingModel = config.embeddingModel() != null && !config.embeddingModel().isBlank()
                ? config.embeddingModel()
                : null;
        return new AiProviderRuntimeConfig(
                config.providerCode(),
                config.baseUrl(),
                config.model(),
                config.apiKey(),
                embeddingBaseUrl,
                embeddingModel
        );
    }

    private void validateConfig(AiProviderRuntimeConfig config) {
        if (config.apiKey() == null || config.apiKey().isBlank()) {
            throw new IllegalStateException("AI API key is not configured");
        }
        if (config.embeddingModel() == null || config.embeddingModel().isBlank()) {
            throw new IllegalStateException("AI embedding model 未配置，请在系统设置中为 " +
                    config.providerCode() + " 厂商设置 embeddingModel");
        }
        String url = config.embeddingBaseUrl();
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("AI embedding base URL 无法推导，请检查 " +
                    config.providerCode() + " 厂商的 baseUrl 配置");
        }
    }

    private String doCall(AiProviderRuntimeConfig config, String text) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.embeddingModel());
        requestBody.put("input", text);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(config.apiKey());
        TraceHeaderInjector.inject(headers);

        String url = config.embeddingBaseUrl();
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