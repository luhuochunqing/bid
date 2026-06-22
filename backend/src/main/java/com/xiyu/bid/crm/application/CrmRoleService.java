package com.xiyu.bid.crm.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.crm.config.CrmProperties;
import com.xiyu.bid.crm.infrastructure.CrmHttpClient;
import com.xiyu.bid.integration.organization.application.OrganizationIntegrationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 泊冉系统角色服务。
 * 调用 /oss/admin-web/v1/output/data/getUserJobListByJobNumberList 获取用户角色。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrmRoleService {

    private final CrmHttpClient httpClient;
    private final CrmProperties properties;
    private final OrganizationIntegrationProperties orgProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 根据工号列表获取用户角色。
     * @param jobNumbers 工号列表
     * @return 工号到角色信息的映射
     */
    public CrmJobListResponse getUserJobList(List<String> jobNumbers) {
        // 使用 OSS 的 base URL（而非 CRM 的 auth base URL）
        String baseUrl = orgProperties.getDirectory().getBaseUrl();
        String path = orgProperties.getDirectory().getBatchJobRoleLookupPath();

        CrmJobListRequest request = new CrmJobListRequest();
        request.setData(jobNumbers);

        log.info("Getting user job list for {} from {}{}", jobNumbers, baseUrl, path);
        // 直接通过 HTTP 调用，避免 CrmResponseHandler 对 code 字段的 int 强转限制
        String body = httpClient.postJsonRaw(baseUrl, path, request);

        if (body == null || body.isBlank()) {
            log.warn("Empty response body from job list API");
            return null;
        }

        try {
            CrmJobListResponse result = objectMapper.readValue(body, CrmJobListResponse.class);
            log.info("Job list response: code={} msg={} dataSize={}",
                    result.getCode(), result.getMsg(),
                    result.getData() != null ? result.getData().size() : 0);
            return result;
        } catch (JsonProcessingException e) {
            log.error("Failed to parse job list response: body={}, error={}", body, e.getMessage());
            return null;
        }
    }

    /**
     * 根据工号获取单用户角色。
     * @param jobNumber 工号
     * @return 角色信息
     */
    public CrmJobListResponse.JobInfo getUserJob(String jobNumber) {
        CrmJobListResponse response = getUserJobList(Collections.singletonList(jobNumber));
        if (response != null && response.getData() != null) {
            return response.getData().get(jobNumber);
        }
        return null;
    }
}
