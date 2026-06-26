package com.xiyu.bid.crm.infrastructure;

import com.xiyu.bid.config.TraceHeaderInjector;

import com.xiyu.bid.crm.config.CrmProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;


@Component
public class CrmHttpClient {

    private static final Logger log = LoggerFactory.getLogger(CrmHttpClient.class);

    private final RestTemplate restTemplate;
    private final CrmProperties properties;

    public CrmHttpClient(CrmProperties properties) {
        this.properties = properties;
        this.restTemplate = new RestTemplate();
    }

    /**
     * Posts to a full URL (for multi-BaseUrl routing).
     */
    public CrmResponseHandler.CrmApiResponse post(String baseUrl, String path, String accessToken, Object body) {
        String url = baseUrl + path;
        return executePost(url, path, accessToken, body);
    }

    /**
     * Posts using the legacy single baseUrl (backward compatible).
     */
    public CrmResponseHandler.CrmApiResponse post(String path, String accessToken, Object body) {
        String url = properties.getBaseUrl() + path;
        return executePost(url, path, accessToken, body);
    }

    /**
     * Posts form-urlencoded data (for OAuth login).
     */
    public CrmResponseHandler.CrmApiResponse postForm(String baseUrl, String path, org.springframework.util.MultiValueMap<String, String> formData) {
        String url = baseUrl + path;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        TraceHeaderInjector.inject(headers);
        HttpEntity<org.springframework.util.MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
            log.info("CRM POST form {} -> {}", url, response.getStatusCode());
            return CrmResponseHandler.parse(response.getBody());
        } catch (RuntimeException e) {
            log.error("CRM POST form failed: {}", e.getMessage());
            return CrmResponseHandler.CrmApiResponse.parseError(e.getMessage());
        }
    }

    /**
     * Posts form-urlencoded data with a Bearer token (for OAuth logout).
     */
    public CrmResponseHandler.CrmApiResponse postForm(String baseUrl, String path, org.springframework.util.MultiValueMap<String, String> formData, String accessToken) {
        String url = baseUrl + path;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        if (accessToken != null && !accessToken.isBlank()) {
            headers.setBearerAuth(accessToken);
        }
        TraceHeaderInjector.inject(headers);
        HttpEntity<org.springframework.util.MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
            log.info("CRM POST form with auth {} -> {}", url, response.getStatusCode());
            return CrmResponseHandler.parse(response.getBody());
        } catch (RuntimeException e) {
            log.error("CRM POST form with auth failed: {}", e.getMessage());
            return CrmResponseHandler.CrmApiResponse.parseError(e.getMessage());
        }
    }

    /**
     * Posts JSON with a custom Bearer token (for generateToken etc.).
     */
    public CrmResponseHandler.CrmApiResponse postWithAuth(String baseUrl, String path, String accessToken, String jsonBody) {
        String url = baseUrl + path;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        TraceHeaderInjector.inject(headers);
        HttpEntity<String> request = new HttpEntity<>(jsonBody, headers);
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
            log.info("CRM POST with auth {} -> {}", url, response.getStatusCode());
            return CrmResponseHandler.parse(response.getBody());
        } catch (RuntimeException e) {
            log.error("CRM POST with auth failed: {}", e.getMessage());
            return CrmResponseHandler.CrmApiResponse.parseError(e.getMessage());
        }
    }

    /**
     * GET with Bearer token (for /oauth/getUserInfo etc.).
     */
    public CrmResponseHandler.CrmApiResponse get(String baseUrl, String path, String accessToken) {
        String url = baseUrl + path;
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        TraceHeaderInjector.inject(headers);
        HttpEntity<Void> request = new HttpEntity<>(headers);
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);
            log.info("CRM GET {} -> {}", url, response.getStatusCode());
            return CrmResponseHandler.parse(response.getBody());
        } catch (RuntimeException e) {
            log.error("CRM GET failed: {}", e.getMessage());
            return CrmResponseHandler.CrmApiResponse.parseError(e.getMessage());
        }
    }

    /**
     * GET with query params (no Bearer token), for endpoints that take token as query param.
     */
    public CrmResponseHandler.CrmApiResponse getWithQueryParams(String baseUrl, String path,
            org.springframework.util.MultiValueMap<String, String> queryParams) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + path);
        if (queryParams != null && !queryParams.isEmpty()) {
            builder.queryParams(queryParams);
        }
        String url = builder.toUriString();
        HttpHeaders headers = new HttpHeaders();
        TraceHeaderInjector.inject(headers);
        HttpEntity<Void> request = new HttpEntity<>(headers);
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);
            log.info("CRM GET with query params {} -> {}", url, response.getStatusCode());
            return CrmResponseHandler.parse(response.getBody());
        } catch (RuntimeException e) {
            log.error("CRM GET with query params failed: {}", e.getMessage());
            return CrmResponseHandler.CrmApiResponse.parseError(e.getMessage());
        }
    }

    /**
     * POST JSON without Bearer token (for /oss/admin-web/... getUserJobList).
     */
    public CrmResponseHandler.CrmApiResponse postJson(String baseUrl, String path, Object body) {
        String url = baseUrl + path;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        TraceHeaderInjector.inject(headers);
        HttpEntity<Object> request = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
            log.info("CRM POST JSON {} -> {}", url, response.getStatusCode());
            return CrmResponseHandler.parse(response.getBody());
        } catch (RuntimeException e) {
            log.error("CRM POST JSON failed: {}", e.getMessage());
            return CrmResponseHandler.CrmApiResponse.parseError(e.getMessage());
        }
    }

    /**
     * POST JSON without Bearer token, return raw response body string.
     * 适用于需要自行解析响应的场景（如响应中 code 为浮点大数字）。
     */
    public String postJsonRaw(String baseUrl, String path, Object body) {
        String url = baseUrl + path;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        TraceHeaderInjector.inject(headers);
        HttpEntity<Object> request = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
            log.info("CRM POST JSON raw {} -> {}", url, response.getStatusCode());
            return response.getBody();
        } catch (RuntimeException e) {
            log.error("CRM POST JSON raw failed: {}", e.getMessage());
            return null;
        }
    }

    private CrmResponseHandler.CrmApiResponse executePost(String url, String path, String accessToken, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        TraceHeaderInjector.inject(headers);

        HttpEntity<Object> request = new HttpEntity<>(body, headers);

        int attempt = 0;
        while (true) {
            try {
                ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
                log.info("CRM POST {} → {} {}", path, response.getStatusCode(),
                        response.getBody() != null ? response.getBody().substring(0, Math.min(200, response.getBody().length())) : "");
                return CrmResponseHandler.parse(response.getBody());
            } catch (RuntimeException e) {
                if (isRetryable(e) && attempt < properties.getMaxRetries()) {
                    attempt++;
                    long delay = Math.min(properties.getRetryBaseDelayMs() * (1L << (attempt - 1)),
                            properties.getRetryMaxDelayMs());
                    log.warn("CRM request failed (attempt {}/{}), retrying in {}ms: {}",
                            attempt, properties.getMaxRetries(), delay, e.getMessage());
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return CrmResponseHandler.CrmApiResponse.parseError("Interrupted during retry");
                    }
                } else {
                    log.error("CRM request failed after {} attempts: {}", attempt, e.getMessage());
                    return CrmResponseHandler.CrmApiResponse.parseError(e.getMessage());
                }
            }
        }
    }

    private boolean isRetryable(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage() : "";
        return msg.contains("500") || msg.contains("502") || msg.contains("503") || msg.contains("504")
                || msg.contains("timeout") || msg.contains("connect") || msg.contains("Connection refused");
    }
}
