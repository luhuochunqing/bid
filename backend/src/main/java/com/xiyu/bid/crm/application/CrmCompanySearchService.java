package com.xiyu.bid.crm.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.xiyu.bid.crm.config.CrmProperties;
import com.xiyu.bid.crm.infrastructure.CrmHttpClient;
import com.xiyu.bid.crm.infrastructure.CrmResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * CRM 公司模糊查询服务（CO-302 反查路径第一步）.
 *
 * <p>对应接口 25338 {@code POST /company/getCompanyNameByLikeName}：
 * 按公司名称模糊查询前 20 条，按名称长度升序返回。
 *
 * <p><b>Host 注意</b>：该接口部署在 CAC 服务（{@code cacBaseUrl}）上，
 * 不是 {@code customerBaseUrl}。详见 {@link CrmProperties#getEffectiveCacBaseUrl()}。
 *
 * <p>CO-302 issue 5.3 要求"精确匹配优先"：取返回列表中第一条与输入名称
 * <strong>完全相等</strong>的结果；若无精确匹配则视为"未查到"。
 *
 * <p>降级策略：查询失败、未找到、无精确匹配均返回 {@link Optional#empty()}，
 * 不抛异常，由调用方决定后续行为。
 */
@Service
public class CrmCompanySearchService {

    private static final Logger log = LoggerFactory.getLogger(CrmCompanySearchService.class);

    private final CrmHttpClient httpClient;
    private final CrmAuthService authService;
    private final CrmProperties properties;

    public CrmCompanySearchService(
            CrmHttpClient httpClient,
            CrmAuthService authService,
            CrmProperties properties) {
        this.httpClient = httpClient;
        this.authService = authService;
        this.properties = properties;
    }

    /**
     * 按公司名称查询 CRM 公司，精确匹配优先.
     *
     * @param name     公司名称（对应标讯的 purchaserName）
     * @param username 当前操作用户（后台任务可为 null → empty）
     * @return 公司信息；empty 表示未查到或无精确匹配
     */
    public Optional<CompanySearchResult> searchByName(String name, String username) {
        if (name == null || name.isBlank()) {
            log.debug("searchByName skipped: name is null/blank");
            return Optional.empty();
        }

        String trimmedName = name.trim();
        String path = properties.getCustomer().getCompanySearchPath();
        Object request = buildRequest(trimmedName);

        try {
            String token = authService.getValidTokenForCaller(username);
            CrmResponseHandler.CrmApiResponse response = httpClient.post(
                    properties.getEffectiveCacBaseUrl(),
                    path,
                    token,
                    request
            );

            if (response.code() != 0) {
                log.warn("searchByName: CRM returned code={}, msg={}", response.code(), response.msg());
                return Optional.empty();
            }

            JsonNode first = findExactMatch(response.data(), trimmedName);
            if (first == null) {
                log.debug("searchByName: no exact match for '{}'", trimmedName);
                return Optional.empty();
            }

            long id = first.path("id").asLong(0L);
            String companyName = first.path("name").asText("");
            String groupName = first.path("groupName").asText("");

            log.info("searchByName: exact match found, name={}, id={}", trimmedName, id);
            return Optional.of(new CompanySearchResult(id, companyName, groupName));
        } catch (TokenUnavailableException e) {
            log.warn("searchByName skipped: token unavailable for '{}', username={}: {}",
                    trimmedName, username, e.getMessage());
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("searchByName failed for '{}': {}", trimmedName, e.getMessage());
            return Optional.empty();
        }
    }

    private Object buildRequest(String name) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("pageIndex", 0);
        wrapper.put("pageSize", 20);
        wrapper.put("body", body);
        return wrapper;
    }

    /**
     * 在 dataList 中找第一条与输入名称精确匹配的记录.
     *
     * @param data         CRM 响应的 data 节点（含 dataList）
     * @param expectedName 期望的公司名称
     * @return 匹配的 JsonNode；null 表示无匹配或 dataList 为空
     */
    private JsonNode findExactMatch(JsonNode data, String expectedName) {
        if (data == null) {
            return null;
        }
        JsonNode dataList = data.path("dataList");
        if (!dataList.isArray()) {
            return null;
        }
        for (JsonNode node : dataList) {
            String name = node.path("name").asText("");
            if (expectedName.equals(name)) {
                return node;
            }
        }
        return null;
    }
}
