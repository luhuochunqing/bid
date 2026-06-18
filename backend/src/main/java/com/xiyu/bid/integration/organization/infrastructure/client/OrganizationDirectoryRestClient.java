package com.xiyu.bid.integration.organization.infrastructure.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.integration.organization.application.OrganizationIntegrationProperties;
import com.xiyu.bid.integration.organization.domain.OrganizationDirectoryLookupContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class OrganizationDirectoryRestClient {

    private static final Logger log = LoggerFactory.getLogger(OrganizationDirectoryRestClient.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final OrganizationIntegrationProperties.Directory directory;
    private final OrganizationDirectoryAuthHeaders authHeaders;

    OrganizationDirectoryRestClient(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            OrganizationIntegrationProperties.Directory directory,
            OrganizationDirectoryAuthHeaders authHeaders) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.directory = directory;
        this.authHeaders = authHeaders;
    }

    Optional<JsonNode> postForm(
            String url,
            MultiValueMap<String, String> form,
            OrganizationDirectoryLookupContext context
    ) {
        return executePost(url, new HttpEntity<>(form, formHeaders(context)), String.class);
    }

    Optional<JsonNode> postJson(
            String url,
            Map<String, Object> body,
            OrganizationDirectoryLookupContext context
    ) {
        try {
            String jsonBody = objectMapper.writeValueAsString(body);
            return executePost(url, new HttpEntity<>(jsonBody, jsonHeaders(context)), String.class);
        } catch (JsonProcessingException ex) {
            throw OrganizationDirectoryHttpGatewayException.retryable("请求序列化失败", ex);
        }
    }

    Optional<JsonNode> get(
            String url,
            Map<String, String> queryParams,
            OrganizationDirectoryLookupContext context
    ) {
        if (url.isBlank()) {
            return Optional.empty();
        }
        String fullUrl = buildUrlWithQueryParams(url, queryParams);
        HttpEntity<Void> entity = new HttpEntity<>(authHeaders.headers(context == null ? OrganizationDirectoryLookupContext.empty() : context));
        try {
            ResponseEntity<String> response = restTemplate.exchange(fullUrl, HttpMethod.GET, entity, String.class);
            String preview = response.getBody();
            if (preview != null && preview.length() > 200) {
                preview = preview.substring(0, 200);
            }
            log.info("组织架构回查成功: url={}, status={}, response={}",
                fullUrl, response.getStatusCode(), preview);
            return OrganizationDirectoryHttpResponseHandler.parseResponse(objectMapper, response.getBody());
        } catch (HttpClientErrorException.NotFound ex) {
            return Optional.empty();
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().is4xxClientError()) {
                throw OrganizationDirectoryHttpGatewayException.nonRetryable("组织架构主数据接口拒绝请求", ex);
            }
            throw OrganizationDirectoryHttpGatewayException.retryable("组织架构主数据接口调用失败", ex);
        } catch (JsonProcessingException | RestClientException ex) {
            throw OrganizationDirectoryHttpGatewayException.retryable("组织架构主数据接口调用失败", ex);
        }
    }

    private String buildUrlWithQueryParams(String url, Map<String, String> queryParams) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url);
        if (queryParams != null) {
            queryParams.forEach((key, value) -> {
                if (key != null && value != null) {
                    builder.queryParam(key, value);
                }
            });
        }
        return builder.toUriString();
    }

    private HttpHeaders formHeaders(OrganizationDirectoryLookupContext context) {
        HttpHeaders headers = authHeaders.headers(context == null ? OrganizationDirectoryLookupContext.empty() : context);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        return headers;
    }

    private HttpHeaders jsonHeaders(OrganizationDirectoryLookupContext context) {
        HttpHeaders headers = authHeaders.headers(context == null ? OrganizationDirectoryLookupContext.empty() : context);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private <T> Optional<JsonNode> executePost(String url, HttpEntity<T> entity, Class<String> responseType) {
        if (url.isBlank()) {
            return Optional.empty();
        }
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, responseType);
            String preview = response.getBody();
            if (preview != null && preview.length() > 200) {
                preview = preview.substring(0, 200);
            }
            log.info("组织架构回查成功: url={}, status={}, response={}",
                url, response.getStatusCode(), preview);
            return OrganizationDirectoryHttpResponseHandler.parseResponse(objectMapper, response.getBody());
        } catch (HttpClientErrorException.NotFound ex) {
            return Optional.empty();
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().is4xxClientError()) {
                throw OrganizationDirectoryHttpGatewayException.nonRetryable("组织架构主数据接口拒绝请求", ex);
            }
            throw OrganizationDirectoryHttpGatewayException.retryable("组织架构主数据接口调用失败", ex);
        } catch (JsonProcessingException | RestClientException ex) {
            throw OrganizationDirectoryHttpGatewayException.retryable("组织架构主数据接口调用失败", ex);
        }
    }

    String buildUrl(String path) {
        return OrganizationDirectoryUrlBuilder.buildUrl(directory.getBaseUrl(), path);
    }
}
