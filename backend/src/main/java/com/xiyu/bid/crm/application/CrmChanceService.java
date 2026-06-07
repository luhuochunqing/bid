package com.xiyu.bid.crm.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.xiyu.bid.crm.config.CrmProperties;
import com.xiyu.bid.crm.infrastructure.CrmHttpClient;
import com.xiyu.bid.crm.infrastructure.CrmResponseHandler;
import com.xiyu.bid.crm.infrastructure.dto.BidInfoSyncDTO;
import com.xiyu.bid.crm.infrastructure.dto.CustomerChancePageRequest;
import com.xiyu.bid.crm.infrastructure.dto.CustomerChanceVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * CRM 商机查询与标讯回传应用服务。
 * <p>职责：
 * <ul>
 *   <li>商机列表查询（代理客户 POST /customer-chance/page-list）</li>
 *   <li>标讯回传（代理客户 POST /customer-chance/bidInfoSync）</li>
 * </ul>
 * <p>副作用层：负责取 Token、调用 HTTP、解析响应、异常处理。
 */
@Service
public class CrmChanceService {

    private static final Logger log = LoggerFactory.getLogger(CrmChanceService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CrmHttpClient httpClient;
    private final CrmAuthService authService;
    private final CrmProperties properties;

    public CrmChanceService(CrmHttpClient httpClient, CrmAuthService authService,
                            CrmProperties properties) {
        this.httpClient = httpClient;
        this.authService = authService;
        this.properties = properties;
    }

    /**
     * 查询 CRM 商机列表（分页）。
     *
     * @param request 分页查询条件
     * @return 分页结果，含商机列表和分页信息；查询失败返回空列表
     */
    public CrmChancePageResult pageList(CustomerChancePageRequest request) {
        String token = authService.getValidToken();
        String baseUrl = properties.getEffectiveChanceBaseUrl();
        String path = properties.getChance().getPageListPath();
        CrmResponseHandler.CrmApiResponse response = httpClient.post(baseUrl, path, token, request);

        if (response.isUnauthorized()) {
            authService.handleUnauthorized();
            token = authService.getValidToken();
            response = httpClient.post(baseUrl, path, token, request);
        }

        if (!response.success() || response.data() == null) {
            log.warn("CRM chance page-list failed: code={}, msg={}", response.code(), response.msg());
            return new CrmChancePageResult(Collections.emptyList(), 0, 0, 0);
        }

        return parsePageResponse(response.data());
    }

    /**
     * 回传标讯状态到 CRM。
     *
     * @param bidInfoSync 标讯回传请求
     * @return true 回传成功，false 回传失败
     */
    public boolean bidInfoSync(BidInfoSyncDTO bidInfoSync) {
        String token = authService.getValidToken();
        String baseUrl = properties.getEffectiveChanceBaseUrl();
        String path = properties.getChance().getBidInfoSyncPath();
        CrmResponseHandler.CrmApiResponse response = httpClient.post(baseUrl, path, token, bidInfoSync);

        if (response.isUnauthorized()) {
            authService.handleUnauthorized();
            token = authService.getValidToken();
            response = httpClient.post(baseUrl, path, token, bidInfoSync);
        }

        if (!response.success()) {
            log.warn("CRM bidInfoSync failed: code={}, msg={}", response.code(), response.msg());
            return false;
        }
        return true;
    }

    @SuppressWarnings("unchecked")
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

    /**
     * CRM 商机列表分页查询结果。
     *
     * @param list       商机列表
     * @param totalCount 总记录数
     * @param pageSize   每页大小
     * @param pageIndex  当前页码
     */
    public record CrmChancePageResult(
            List<CustomerChanceVO> list,
            int totalCount,
            int pageSize,
            int pageIndex
    ) {}
}
