package com.xiyu.bid.integration.organization.infrastructure.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.integration.organization.application.OrganizationDirectoryGateway;
import com.xiyu.bid.integration.organization.application.OrganizationIntegrationProperties;
import com.xiyu.bid.integration.organization.domain.OrganizationDepartmentSnapshot;
import com.xiyu.bid.integration.organization.domain.OrganizationDirectoryLookupContext;
import com.xiyu.bid.integration.organization.domain.OrganizationUserSnapshot;
import com.xiyu.bid.integration.organization.domain.OrganizationJobSnapshot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Optional;

@Component
@Conditional(OrganizationDirectoryBaseUrlConfiguredCondition.class)
public class OrganizationDirectoryHttpGateway implements OrganizationDirectoryGateway {

    private static final Logger log = LoggerFactory.getLogger(OrganizationDirectoryHttpGateway.class);
    private static final DateTimeFormatter ORG_API_DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final OrganizationIntegrationProperties.Directory directory;
    private final OrganizationDirectoryJsonMapper mapper = new OrganizationDirectoryJsonMapper();
    private final OrganizationDirectoryAuthHeaders authHeaders;

    @Autowired
    public OrganizationDirectoryHttpGateway(
            RestTemplateBuilder restTemplateBuilder,
            ObjectMapper objectMapper,
            OrganizationIntegrationProperties properties) {
        this(restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(properties.getDirectory().getConnectTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(properties.getDirectory().getReadTimeoutMs()))
                .build(), objectMapper, properties);
    }

    OrganizationDirectoryHttpGateway(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            OrganizationIntegrationProperties properties) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.directory = properties.getDirectory();
        this.authHeaders = new OrganizationDirectoryAuthHeaders(directory);
    }

    @Override
    public Optional<OrganizationDepartmentSnapshot> fetchDepartmentByDeptId(String deptId) {
        return fetchDepartmentByDeptId(deptId, OrganizationDirectoryLookupContext.empty());
    }

    @Override
    public Optional<OrganizationDepartmentSnapshot> fetchDepartmentByDeptId(
            String deptId,
            OrganizationDirectoryLookupContext context
    ) {
        String url = buildUrl(directory.getDepartmentDetailPath());
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        log.info("组织架构回查 Dept: url={}, deptId={}, traceId={}",
            url, deptId,
            context != null ? context.traceId() : "null");
        form.add("deptId", deptId);
        form.add("del", "0");
        form.add("state", "0");
        return postForm(url, form, context).map(mapper::department);
    }

    @Override
    public Optional<OrganizationUserSnapshot> fetchUserByUserId(String userId) {
        return fetchUserByUserId(userId, OrganizationDirectoryLookupContext.empty());
    }

    @Override
    public Optional<OrganizationJobSnapshot> fetchJobByJobId(String jobId) {
        return fetchJobByJobId(jobId, OrganizationDirectoryLookupContext.empty());
    }

    @Override
    public Optional<OrganizationJobSnapshot> fetchJobByJobId(String jobId, OrganizationDirectoryLookupContext context) {
        String url = buildUrl(directory.getJobDetailPath());
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        log.info("组织架构回查 Job: url={}, jobId={}, traceId={}",
            url, jobId,
            context != null ? context.traceId() : "null");
        form.add("jobId", jobId);
        form.add("del", "0");
        form.add("state", "0");
        return postForm(url, form, context).map(mapper::job);
    }

    @Override
    public Optional<OrganizationUserSnapshot> fetchUserByUserId(String userId, OrganizationDirectoryLookupContext context) {
        String url = buildUrl(directory.getUserDetailPath());
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        log.info("组织架构回查 User: url={}, userId={}, traceId={}",
            url, userId,
            context != null ? context.traceId() : "null");
        form.add("userId", userId);
        form.add("del", "0");
        form.add("state", "0");
        return postForm(url, form, context).map(mapper::user);
    }

    @Override
    public List<OrganizationDepartmentSnapshot> listDepartmentsByWindow(LocalDateTime startAt, LocalDateTime endAt) {
        return listDepartmentsByWindow(startAt, endAt, OrganizationDirectoryLookupContext.empty());
    }

    @Override
    public List<OrganizationDepartmentSnapshot> listDepartmentsByWindow(
            LocalDateTime startAt,
            LocalDateTime endAt,
            OrganizationDirectoryLookupContext context
    ) {
        String url = buildUrl(directory.getDepartmentWindowPath());
        return fetchWindow(url, startAt, endAt, context, mapper::departments);
    }

    @Override
    public List<OrganizationUserSnapshot> listUsersByWindow(LocalDateTime startAt, LocalDateTime endAt) {
        return listUsersByWindow(startAt, endAt, OrganizationDirectoryLookupContext.empty());
    }

    @Override
    public List<OrganizationUserSnapshot> listUsersByWindow(
            LocalDateTime startAt,
            LocalDateTime endAt,
            OrganizationDirectoryLookupContext context
    ) {
        String url = buildUrl(directory.getUserWindowPath());
        return fetchWindow(url, startAt, endAt, context, mapper::users);
    }

    private <T> List<T> fetchWindow(
            String url,
            LocalDateTime startAt,
            LocalDateTime endAt,
            OrganizationDirectoryLookupContext context,
            java.util.function.Function<JsonNode, List<T>> extractor
    ) {
        if (url.isBlank()) {
            return List.of();
        }
        List<T> allResults = new ArrayList<>();
        int index = 0;
        int maxPages = 100; // safety limit to prevent infinite loops
        int initialTotal = Integer.MAX_VALUE;
        for (int page = 0; page < maxPages; page++) {
            Map<String, Object> body = Map.of(
                    "startTime", startAt.format(ORG_API_DTF),
                    "endTime", endAt.format(ORG_API_DTF),
                    "index", index
            );
            Optional<JsonNode> response = postJson(url, body, context);
            if (response.isEmpty()) {
                break;
            }
            JsonNode root = response.get();
            List<T> pageResults = extractor.apply(root);
            if (pageResults.isEmpty()) {
                break;
            }
            allResults.addAll(pageResults);
            // Use total field from response to determine stop condition
            JsonNode totalNode = root.path("total");
            if (totalNode.isInt()) {
                if (initialTotal == Integer.MAX_VALUE) {
                    initialTotal = totalNode.asInt();
                }
                if (initialTotal != Integer.MAX_VALUE && allResults.size() >= initialTotal) {
                    break;
                }
            }
            // Calculate next page index: try object format first, then fallback to offset-based
            JsonNode data = root.path("data");
            JsonNode nextIndex = data.path("index");
            if (nextIndex.isInt() && nextIndex.asInt() > index) {
                index = nextIndex.asInt();
            } else {
                // Fallback: use last record's internal ID from the data array as next cursor
                JsonNode dataArray = root.path("data");
                if (dataArray.isArray() && dataArray.size() > 0) {
                    JsonNode lastElement = dataArray.get(dataArray.size() - 1);
                    int lastId = lastElement.path("id").asInt();
                    if (lastId == 0) {
                        lastId = lastElement.path("userId").asInt();
                    }
                    if (lastId > index) {
                        index = lastId;
                    } else {
                        break; /* no progress */
                    }
                } else {
                    break; /* no data array found */
                }
            }
        }
        return allResults;
    }

    private Optional<JsonNode> postForm(
            String url,
            MultiValueMap<String, String> form,
            OrganizationDirectoryLookupContext context
    ) {
        return executePost(url, new HttpEntity<>(form, formHeaders(context)), String.class);
    }

    private Optional<JsonNode> postJson(
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

    private String buildUrl(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        if (path.startsWith("http")) {
            return path;
        }
        String base = trimRight(directory.getBaseUrl());
        String cleanPath = trimLeft(path);
        return base + "/" + cleanPath;
    }

    private String trimLeft(String value) {
        return value == null ? "" : value.replaceFirst("^/+", "");
    }

    private String trimRight(String value) {
        return value == null ? "" : value.replaceFirst("/+$", "");
    }
}
