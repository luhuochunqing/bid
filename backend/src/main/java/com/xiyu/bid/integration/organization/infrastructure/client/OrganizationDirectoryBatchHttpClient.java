package com.xiyu.bid.integration.organization.infrastructure.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.integration.organization.application.OrganizationIntegrationProperties;
import com.xiyu.bid.integration.organization.domain.OrganizationDirectoryLookupContext;
import com.xiyu.bid.integration.organization.dto.OssUserJobAndRoleDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * OSS 批量岗位/角色回查 HTTP 客户端。
 * <p>
 * 独立于单条查询网关，使用单独的 RestTemplate 与超时配置，负责按工号列表批量获取岗位名称与系统角色列表。
 */
class OrganizationDirectoryBatchHttpClient {

    private static final Logger log = LoggerFactory.getLogger(OrganizationDirectoryBatchHttpClient.class);
    private final RestTemplate batchRestTemplate;
    private final ObjectMapper objectMapper;
    private final OrganizationIntegrationProperties.Directory directory;
    private final OrganizationDirectoryAuthHeaders authHeaders;
    private final OrganizationDirectoryJsonMapper mapper = new OrganizationDirectoryJsonMapper();
    private final OrganizationDirectoryStrictBatchLookup strictBatchLookup;

    OrganizationDirectoryBatchHttpClient(
            RestTemplate batchRestTemplate,
            ObjectMapper objectMapper,
            OrganizationIntegrationProperties.Directory directory,
            OrganizationDirectoryAuthHeaders authHeaders) {
        this.batchRestTemplate = batchRestTemplate;
        this.objectMapper = objectMapper;
        this.directory = directory;
        this.authHeaders = authHeaders;
        this.strictBatchLookup = new OrganizationDirectoryStrictBatchLookup(
                batchRestTemplate, objectMapper, authHeaders);
    }

    Map<String, OssUserJobAndRoleDto> getUserJobAndRoleListByJobNumbers(
            List<String> jobNumbers,
            OrganizationDirectoryLookupContext context
    ) {
        return queryJobAndRoleList(jobNumbers, context);
    }

    Map<String, OssUserJobAndRoleDto> getUserJobAndRoleListByJobNumbersStrict(
            List<String> jobNumbers,
            OrganizationDirectoryLookupContext context
    ) {
        return strictBatchLookup.lookup(
                jobNumbers,
                buildUrl(directory.getBatchJobRoleLookupPath()),
                directory.getBatchQuerySize(),
                context);
    }

    private Map<String, OssUserJobAndRoleDto> queryJobAndRoleList(
            List<String> jobNumbers,
            OrganizationDirectoryLookupContext context
    ) {
        String url = buildUrl(directory.getBatchJobRoleLookupPath());
        if (jobNumbers == null || jobNumbers.isEmpty()) {
            return Map.of();
        }
        if (url.isBlank()) {
            return Map.of();
        }
        int batchSize = Math.max(1, directory.getBatchQuerySize());
        Map<String, OssUserJobAndRoleDto> allResults = new HashMap<>();
        long startNs = System.nanoTime();
        int requestCount = 0;
        int responseCount = 0;
        for (int i = 0; i < jobNumbers.size(); i += batchSize) {
            List<String> batch = jobNumbers.subList(i, Math.min(i + batchSize, jobNumbers.size()));
            requestCount += batch.size();
            try {
                Optional<JsonNode> response = postJsonBatch(url, Map.of("data", batch), context);
                if (response.isEmpty()) {
                    continue;
                }
                List<OssUserJobAndRoleDto> batchResults = mapper.jobAndRoleList(response.get());
                for (OssUserJobAndRoleDto dto : batchResults) {
                    if (dto.jobNumber() != null && !dto.jobNumber().isBlank()) {
                        allResults.merge(dto.jobNumber(), dto, (existing, incoming) -> {
                            log.warn("批量岗位/角色回查结果中工号重复，保留第一条: jobNumber={}", dto.jobNumber());
                            return existing;
                        });
                        responseCount++;
                    } else {
                        log.warn("批量岗位/角色回查结果中存在空工号记录，已忽略");
                    }
                }
            } catch (RuntimeException ex) {
                log.error("批量岗位/角色回查失败: url={}, batchSize={}, error={}", url, batch.size(), ex.getMessage(), ex);
                // 普通组织同步保持既有容错语义，返回已获取的部分结果
            }
        }
        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("批量岗位/角色回查完成: url={}, requested={}, returned={}, durationMs={}",
                url, requestCount, responseCount, durationMs);
        return Map.copyOf(allResults);
    }

    private Optional<JsonNode> postJsonBatch(
            String url,
            Map<String, Object> body,
            OrganizationDirectoryLookupContext context
    ) {
        try {
            String jsonBody = objectMapper.writeValueAsString(body);
            return executeBatchPost(url, new HttpEntity<>(jsonBody, jsonHeaders(context)), String.class);
        } catch (JsonProcessingException ex) {
            log.error("批量岗位/角色回查请求序列化失败: error={}", ex.getMessage(), ex);
            return Optional.empty();
        }
    }

    private <T> Optional<JsonNode> executeBatchPost(String url, HttpEntity<T> entity, Class<String> responseType) {
        if (url.isBlank()) {
            return Optional.empty();
        }
        try {
            ResponseEntity<String> response = batchRestTemplate.postForEntity(url, entity, responseType);
            String preview = response.getBody();
            if (preview != null && preview.length() > 200) {
                preview = preview.substring(0, 200);
            }
            log.info("批量岗位/角色回查成功: url={}, status={}, response={}",
                    url, response.getStatusCode(), preview);
            return OrganizationDirectoryHttpResponseHandler.parseResponse(objectMapper, response.getBody());
        } catch (HttpClientErrorException.NotFound ex) {
            return Optional.empty();
        } catch (HttpStatusCodeException ex) {
            log.error("批量岗位/角色回查接口返回错误状态: url={}, status={}, body={}",
                    url, ex.getStatusCode(), ex.getResponseBodyAsString(), ex);
            return Optional.empty();
        } catch (JsonProcessingException | RestClientException ex) {
            log.error("批量岗位/角色回查接口调用失败: url={}, error={}", url, ex.getMessage(), ex);
            return Optional.empty();
        }
    }

    private HttpHeaders jsonHeaders(OrganizationDirectoryLookupContext context) {
        HttpHeaders headers = authHeaders.headers(context == null ? OrganizationDirectoryLookupContext.empty() : context);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String buildUrl(String path) {
        return OrganizationDirectoryUrlBuilder.buildUrl(directory.getBaseUrl(), path);
    }
}
