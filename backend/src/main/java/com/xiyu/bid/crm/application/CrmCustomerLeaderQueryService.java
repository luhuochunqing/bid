package com.xiyu.bid.crm.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.xiyu.bid.crm.application.CrmAuthService;
import com.xiyu.bid.crm.config.CrmProperties;
import com.xiyu.bid.crm.infrastructure.CrmHttpClient;
import com.xiyu.bid.crm.infrastructure.dto.CustomerChanceDTO;
import com.xiyu.bid.crm.infrastructure.dto.CustomerChancePageRequest;
import com.xiyu.bid.crm.infrastructure.dto.CustomerChanceVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * CRM 客户负责人查询服务。
 * <p>按客户名（招标主体）查询 CRM 商机负责人，用于标讯自动分配。
 * <p>降级策略：查询失败或未找到返回 null，由调用方决定后续行为。
 */
@Service
public class CrmCustomerLeaderQueryService {

    private static final Logger log = LoggerFactory.getLogger(CrmCustomerLeaderQueryService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CrmHttpClient httpClient;
    private final CrmAuthService authService;
    private final CrmProperties properties;

    public CrmCustomerLeaderQueryService(
            CrmHttpClient httpClient,
            CrmAuthService authService,
            CrmProperties properties) {
        this.httpClient = httpClient;
        this.authService = authService;
        this.properties = properties;
    }

    /**
     * 按客户名（groupName）查询 CRM 商机项目负责人。
     * <p>用于标讯自动分配：根据标讯的招标主体（purchaserName）作为 groupName 查询 CRM 商机，
     * 取出第一条商机的项目负责人信息。
     *
     * @param groupName 客户名（对应标讯的 purchaserName）
     * @return 项目负责人信息；{@code null} 表示查询失败或未找到
     */
    public CustomerLeaderResult findLeaderByGroupName(String groupName) {
        if (groupName == null || groupName.isBlank()) {
            log.debug("findLeaderByGroupName skipped: groupName is null/blank");
            return null;
        }

        CustomerChancePageRequest request = buildGroupRequest(groupName.trim(), 1, 10);
        CrmChancePageResult result = doPageList(request);

        if (result.list().isEmpty()) {
            log.debug("findLeaderByGroupName: no opportunity found for groupName={}", groupName);
            return null;
        }

        CustomerChanceVO first = result.list().get(0);
        if (first.projectLeaderName() == null || first.projectLeaderName().isBlank()) {
            log.info("findLeaderByGroupName: groupName={} has no projectLeaderName", groupName);
            return null;
        }

        log.info("findLeaderByGroupName: groupName={}, leader={}, leaderNo={}",
                groupName, first.projectLeaderName(), first.projectLeaderNo());
        return new CustomerLeaderResult(
                first.groupName(),
                first.projectLeaderName(),
                first.projectLeaderNo()
        );
    }

    private CustomerChancePageRequest buildGroupRequest(String groupName, int pageIndex, int pageSize) {
        CustomerChanceDTO body = new CustomerChanceDTO(
                List.of(groupName), null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null);
        return new CustomerChancePageRequest(pageIndex, pageSize, body);
    }

    private CrmChancePageResult doPageList(CustomerChancePageRequest request) {
        String token = authService.getValidToken();
        var response = httpClient.post(
                properties.getBaseUrl(),
                "/customer-chance/page-list",
                token,
                request
        );
        if (response.data() == null || response.data().isEmpty()) {
            return new CrmChancePageResult(Collections.emptyList(), 0, 0, 0);
        }
        return parsePageResponse(response.data());
    }

    private CrmChancePageResult parsePageResponse(JsonNode data) {
        try {
            int totalCount = data.path("totalCount").asInt(0);
            int pageSize = data.path("pageSize").asInt(0);
            int pageIndex = data.path("pageIndex").asInt(1);
            JsonNode dataListNode = data.path("dataList");

            List<CustomerChanceVO> list;
            if (dataListNode.isArray() && dataListNode.size() > 0) {
                String jsonArray = MAPPER.writeValueAsString(dataListNode);
                CollectionType collectionType = MAPPER.getTypeFactory()
                        .constructCollectionType(List.class, CustomerChanceVO.class);
                list = MAPPER.readValue(jsonArray, collectionType);
            } else {
                list = Collections.emptyList();
            }
            return new CrmChancePageResult(list, totalCount, pageSize, pageIndex);
        } catch (JsonProcessingException | RuntimeException e) {
            log.error("Failed to parse CRM chance page response", e);
            return new CrmChancePageResult(Collections.emptyList(), 0, 0, 0);
        }
    }
}
