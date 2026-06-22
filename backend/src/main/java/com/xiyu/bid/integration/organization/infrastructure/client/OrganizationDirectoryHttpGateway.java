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
import com.xiyu.bid.integration.organization.dto.OssMenuTreeNode;
import com.xiyu.bid.integration.organization.dto.OssUserJobAndRoleDto;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@Conditional(OrganizationDirectoryBaseUrlConfiguredCondition.class)
public class OrganizationDirectoryHttpGateway implements OrganizationDirectoryGateway {

    private static final Logger log = LoggerFactory.getLogger(OrganizationDirectoryHttpGateway.class);
    private static final DateTimeFormatter ORG_API_DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final OrganizationIntegrationProperties.Directory directory;
    private final OrganizationDirectoryJsonMapper mapper = new OrganizationDirectoryJsonMapper();
    private final OrganizationDirectoryRestClient restClient;
    private final OrganizationDirectoryBatchHttpClient batchClient;

    @Autowired
    public OrganizationDirectoryHttpGateway(
            RestTemplateBuilder restTemplateBuilder,
            ObjectMapper objectMapper,
            OrganizationIntegrationProperties properties) {
        this(
                restTemplateBuilder
                        .setConnectTimeout(Duration.ofMillis(properties.getDirectory().getConnectTimeoutMs()))
                        .setReadTimeout(Duration.ofMillis(properties.getDirectory().getReadTimeoutMs()))
                        .build(),
                restTemplateBuilder
                        .setConnectTimeout(Duration.ofMillis(properties.getDirectory().getBatchConnectTimeoutMs()))
                        .setReadTimeout(Duration.ofMillis(properties.getDirectory().getBatchReadTimeoutMs()))
                        .build(),
                objectMapper,
                properties);
    }

    OrganizationDirectoryHttpGateway(
            RestTemplate restTemplate,
            RestTemplate batchRestTemplate,
            ObjectMapper objectMapper,
            OrganizationIntegrationProperties properties) {
        this.directory = properties.getDirectory();
        OrganizationDirectoryAuthHeaders authHeaders = new OrganizationDirectoryAuthHeaders(directory);
        this.restClient = new OrganizationDirectoryRestClient(restTemplate, objectMapper, directory, authHeaders);
        this.batchClient = new OrganizationDirectoryBatchHttpClient(
                batchRestTemplate, objectMapper, directory, authHeaders);
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
        return restClient.postForm(url, form, context).map(mapper::department);
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
        return restClient.postForm(url, form, context).map(mapper::job);
    }

    @Override
    public Map<String, OssUserJobAndRoleDto> getUserJobAndRoleListByJobNumbers(
            List<String> jobNumbers,
            OrganizationDirectoryLookupContext context
    ) {
        return batchClient.getUserJobAndRoleListByJobNumbers(jobNumbers, context);
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
        return restClient.postForm(url, form, context).map(mapper::user);
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

    @Override
    public Optional<List<OssMenuTreeNode>> fetchUserMenuTree(
            String jobNumber,
            OrganizationDirectoryLookupContext context
    ) {
        String url = buildUrl(directory.getUserMenuTreePath());
        log.info("组织架构回查 UserMenuTree: url={}, jobNumber={}, systemName={}, retrievalType={}, traceId={}",
            url, jobNumber, directory.getUserMenuTreeSystemName(),
            directory.getUserMenuTreeRetrievalType(),
            context != null ? context.traceId() : "null");
        Map<String, String> params = new LinkedHashMap<>();
        String jobNumberParamName = directory.getUserMenuTreeJobNumberParamName();
        if (jobNumberParamName != null && !jobNumberParamName.isBlank()
                && jobNumber != null && !jobNumber.isBlank()) {
            params.put(jobNumberParamName.trim(), jobNumber.trim());
        }
        params.put("systemName", directory.getUserMenuTreeSystemName());
        params.put("menuRetrievalType", String.valueOf(directory.getUserMenuTreeRetrievalType()));
        return restClient.get(url, params, context).map(mapper::menuTree);
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
        int maxPages = 100;
        int initialTotal = Integer.MAX_VALUE;
        for (int page = 0; page < maxPages; page++) {
            Map<String, Object> body = Map.of(
                    "startTime", startAt.format(ORG_API_DTF),
                    "endTime", endAt.format(ORG_API_DTF),
                    "index", index
            );
            Optional<JsonNode> response = restClient.postJson(url, body, context);
            if (response.isEmpty()) {
                break;
            }
            JsonNode root = response.get();
            List<T> pageResults = extractor.apply(root);
            if (pageResults.isEmpty()) {
                break;
            }
            allResults.addAll(pageResults);
            JsonNode totalNode = root.path("total");
            if (totalNode.isInt()) {
                if (initialTotal == Integer.MAX_VALUE) {
                    initialTotal = totalNode.asInt();
                }
                if (initialTotal != Integer.MAX_VALUE && allResults.size() >= initialTotal) {
                    break;
                }
            }
            JsonNode data = root.path("data");
            JsonNode nextIndex = data.path("index");
            if (nextIndex.isInt() && nextIndex.asInt() > index) {
                index = nextIndex.asInt();
            } else {
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
                        break;
                    }
                } else {
                    break;
                }
            }
        }
        return allResults;
    }

    private String buildUrl(String path) {
        return OrganizationDirectoryUrlBuilder.buildUrl(directory.getBaseUrl(), path);
    }
}
