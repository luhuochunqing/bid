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
                log.debug("CRM POST {} → {} {}", path, response.getStatusCode(),
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
