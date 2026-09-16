package com.xiyu.bid.integration.organization.infrastructure.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.integration.organization.domain.OrganizationDirectoryLookupContext;
import com.xiyu.bid.integration.organization.domain.OrganizationDirectoryResponseDecision;
import com.xiyu.bid.integration.organization.domain.OrganizationDirectoryResponseOutcome;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 提醒场景使用的完整性优先批量回查。
 * <p>
 * 单批瞬时失败先重试一次，再递归二分隔离组合型异常；成功空结果是完整响应，不继续拆分。
 */
final class OrganizationDirectoryStrictBatchLookup {

    private static final Logger log = LoggerFactory.getLogger(OrganizationDirectoryStrictBatchLookup.class);
    private static final int EXTRA_ATTEMPTS = 64;
    private static final long TIME_BUDGET_NS = 55_000_000_000L;
    private static final long RETRY_DELAY_MS = 100L;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final OrganizationDirectoryAuthHeaders authHeaders;
    private final OrganizationDirectoryJsonMapper mapper = new OrganizationDirectoryJsonMapper();

    OrganizationDirectoryStrictBatchLookup(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            OrganizationDirectoryAuthHeaders authHeaders
    ) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.authHeaders = authHeaders;
    }

    Map<String, OssUserJobAndRoleDto> lookup(
            List<String> jobNumbers,
            String url,
            int batchSize,
            OrganizationDirectoryLookupContext context
    ) {
        if (jobNumbers == null || jobNumbers.isEmpty()) {
            return Map.of();
        }
        if (url.isBlank()) {
            throw new IllegalStateException("OSS batch job/role lookup URL is not configured");
        }

        long startNs = System.nanoTime();
        int normalizedBatchSize = Math.max(1, batchSize);
        int baseBatchCount = (jobNumbers.size() + normalizedBatchSize - 1) / normalizedBatchSize;
        LookupBudget budget = new LookupBudget(baseBatchCount + EXTRA_ATTEMPTS, startNs + TIME_BUDGET_NS);
        Map<String, OssUserJobAndRoleDto> results = new HashMap<>();
        int responseCount = 0;

        for (int i = 0; i < jobNumbers.size(); i += normalizedBatchSize) {
            List<String> batch = jobNumbers.subList(i, Math.min(i + normalizedBatchSize, jobNumbers.size()));
            int batchOrdinal = i / normalizedBatchSize + 1;
            List<OssUserJobAndRoleDto> batchResults = fetchBatch(
                    url, batch, context, batchOrdinal, 0, budget);
            for (OssUserJobAndRoleDto dto : batchResults) {
                if (dto.jobNumber() == null || dto.jobNumber().isBlank()) {
                    log.warn("严格批量岗位/角色回查结果中存在空工号记录，已忽略");
                    continue;
                }
                results.merge(dto.jobNumber(), dto, (existing, incoming) -> {
                    log.warn("严格批量岗位/角色回查结果中工号重复，保留第一条");
                    return existing;
                });
                responseCount++;
            }
        }

        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("严格批量岗位/角色回查完成: requested={}, returned={}, durationMs={}",
                jobNumbers.size(), responseCount, durationMs);
        return Map.copyOf(results);
    }

    private List<OssUserJobAndRoleDto> fetchBatch(
            String url,
            List<String> batch,
            OrganizationDirectoryLookupContext context,
            int batchOrdinal,
            int depth,
            LookupBudget budget
    ) {
        BatchAttempt attempt = attempt(url, batch, context, budget);
        if (attempt.success()) {
            return mapper.jobAndRoleList(attempt.root());
        }
        if (!attempt.retryable()) {
            throw failure(attempt);
        }

        log.warn("严格批量岗位/角色回查重试: batchOrdinal={}, batchSize={}, depth={}",
                batchOrdinal, batch.size(), depth);
        pauseBeforeRetry();
        attempt = attempt(url, batch, context, budget);
        if (attempt.success()) {
            return mapper.jobAndRoleList(attempt.root());
        }
        if (!attempt.retryable()) {
            throw failure(attempt);
        }
        if (batch.size() == 1) {
            throw new IllegalStateException(
                    "OSS strict batch lookup failed for a single job number: " + attempt.failureReason());
        }

        int midpoint = batch.size() / 2;
        log.warn("严格批量岗位/角色回查二分: batchOrdinal={}, batchSize={}, depth={}",
                batchOrdinal, batch.size(), depth);
        List<OssUserJobAndRoleDto> recovered = new ArrayList<>();
        recovered.addAll(fetchBatch(url, batch.subList(0, midpoint), context,
                batchOrdinal, depth + 1, budget));
        recovered.addAll(fetchBatch(url, batch.subList(midpoint, batch.size()), context,
                batchOrdinal, depth + 1, budget));
        return recovered;
    }

    private BatchAttempt attempt(
            String url,
            List<String> batch,
            OrganizationDirectoryLookupContext context,
            LookupBudget budget
    ) {
        budget.beforeAttempt();
        try {
            String body = objectMapper.writeValueAsString(Map.of("data", batch));
            return execute(url, new HttpEntity<>(body, jsonHeaders(context)));
        } catch (JsonProcessingException ex) {
            log.error("严格批量岗位/角色回查请求序列化失败: error={}", ex.getMessage(), ex);
            return BatchAttempt.failure(false, "request serialization failed");
        }
    }

    private BatchAttempt execute(String url, HttpEntity<String> entity) {
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            String body = response.getBody();
            if (body == null || body.isBlank()) {
                return BatchAttempt.failure(false, "empty response body");
            }
            JsonNode root = objectMapper.readTree(body);
            OrganizationDirectoryResponseDecision decision;
            try {
                decision = OrganizationDirectoryHttpResponseHandler.classify(root);
            } catch (OrganizationDirectoryHttpGatewayException ex) {
                return BatchAttempt.failure(ex.retryable(), ex.getMessage());
            }
            if (decision.outcome() == OrganizationDirectoryResponseOutcome.SUCCESS
                    || isExplicitSuccessCode(root.path("code"))) {
                return BatchAttempt.success(root);
            }
            return BatchAttempt.failure(false, decision.message());
        } catch (HttpClientErrorException.NotFound ex) {
            return BatchAttempt.failure(false, "HTTP 404");
        } catch (HttpStatusCodeException ex) {
            int status = ex.getStatusCode().value();
            boolean retryable = ex.getStatusCode().is5xxServerError() || status == 408 || status == 429;
            log.warn("严格批量岗位/角色回查接口返回错误状态: status={}, retryable={}",
                    ex.getStatusCode(), retryable);
            return BatchAttempt.failure(retryable, "HTTP " + status);
        } catch (JsonProcessingException ex) {
            log.error("严格批量岗位/角色回查响应解析失败: error={}", ex.getMessage(), ex);
            return BatchAttempt.failure(false, "invalid response JSON");
        } catch (RestClientException ex) {
            log.warn("严格批量岗位/角色回查接口调用失败: error={}", ex.getMessage());
            return BatchAttempt.failure(true, "transport failure");
        }
    }

    private HttpHeaders jsonHeaders(OrganizationDirectoryLookupContext context) {
        HttpHeaders headers = authHeaders.headers(
                context == null ? OrganizationDirectoryLookupContext.empty() : context);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private boolean isExplicitSuccessCode(JsonNode code) {
        if (!code.isValueNode() || code.isNull()) {
            return false;
        }
        return switch (code.asText().trim().toUpperCase(Locale.ROOT)) {
            case "0", "200", "OK", "SUCCESS" -> true;
            default -> false;
        };
    }

    private IllegalStateException failure(BatchAttempt attempt) {
        return new IllegalStateException("OSS strict batch lookup failed: " + attempt.failureReason());
    }

    private void pauseBeforeRetry() {
        try {
            Thread.sleep(RETRY_DELAY_MS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OSS strict batch lookup interrupted", ex);
        }
    }

    private record BatchAttempt(JsonNode root, boolean retryable, String failureReason) {
        private static BatchAttempt success(JsonNode root) {
            return new BatchAttempt(root, false, "");
        }

        private static BatchAttempt failure(boolean retryable, String reason) {
            return new BatchAttempt(null, retryable, reason);
        }

        private boolean success() {
            return root != null;
        }
    }

    private static final class LookupBudget {
        private final int maxAttempts;
        private final long deadlineNs;
        private int attempts;

        private LookupBudget(int maxAttempts, long deadlineNs) {
            this.maxAttempts = maxAttempts;
            this.deadlineNs = deadlineNs;
        }

        private void beforeAttempt() {
            attempts++;
            if (attempts > maxAttempts || System.nanoTime() > deadlineNs) {
                throw new IllegalStateException("OSS strict batch lookup exceeded recovery budget");
            }
        }
    }
}
