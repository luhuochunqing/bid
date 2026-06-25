package com.xiyu.bid.crm.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.xiyu.bid.crm.config.CrmProperties;
import com.xiyu.bid.crm.infrastructure.CrmHttpClient;
import com.xiyu.bid.crm.infrastructure.CrmResponseHandler;
import com.xiyu.bid.crm.infrastructure.dto.ContactPersonInfoVO;
import com.xiyu.bid.crm.infrastructure.dto.ContactPersonListDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * CRM 对接人查询应用服务。
 * <p>职责：按商机 ID 查询 CRM 对接联系人。
 * <p>副作用层：负责取 Token、调用 HTTP、解析响应、异常处理。
 */
@Service
public class CrmContactPersonService {

    private static final Logger log = LoggerFactory.getLogger(CrmContactPersonService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CrmHttpClient httpClient;
    private final CrmAuthService authService;
    private final CrmProperties properties;

    public CrmContactPersonService(CrmHttpClient httpClient, CrmAuthService authService,
                                   CrmProperties properties) {
        this.httpClient = httpClient;
        this.authService = authService;
        this.properties = properties;
    }

    /**
     * 按商机 ID 查询对接人列表。
     *
     * @param ccId 商机 ID
     * @return 对接人列表；查询失败返回空列表
     */
    public List<ContactPersonInfoVO> pageList(Long ccId) {
        String token = authService.getValidToken();
        String baseUrl = properties.getEffectiveContactPersonBaseUrl();
        String path = properties.getContactPerson().getPageListPath();
        ContactPersonListDTO body = new ContactPersonListDTO(ccId);
        CrmResponseHandler.CrmApiResponse response = httpClient.post(baseUrl, path, token, body);

        if (response.isUnauthorized()) {
            authService.handleUnauthorized();
            token = authService.getValidToken();
            response = httpClient.post(baseUrl, path, token, body);
        }

        if (!response.success() || response.data() == null) {
            log.warn("CRM contact-person page-list failed: code={}, msg={}", response.code(), response.msg());
            return Collections.emptyList();
        }

        return parseListResponse(response.data());
    }

    private List<ContactPersonInfoVO> parseListResponse(JsonNode data) {
        try {
            // CRM 对接人 page-list 响应与商机 page-list 一致：扁平结构 {code, totalCount, dataList:[...]}
            // （无外层 data 字段，CrmResponseHandler.data() 回退到根节点）。对接人数组在 dataList 字段里，
            // 并非直接数组——此前仅判 data.isArray() 会把 CRM 已返回的对接人解析成空列表。
            // 此处优先取 dataList，同时兼容直接数组形态（防御）。
            JsonNode arrNode = data.isArray() ? data : data.path("dataList");
            if (arrNode.isArray() && arrNode.size() > 0) {
                String jsonArray = MAPPER.writeValueAsString(arrNode);
                CollectionType collectionType = MAPPER.getTypeFactory()
                        .constructCollectionType(List.class, ContactPersonInfoVO.class);
                return MAPPER.readValue(jsonArray, collectionType);
            }
            return Collections.emptyList();
        } catch (JsonProcessingException | RuntimeException e) {
            log.error("Failed to parse CRM contact-person response", e);
            return Collections.emptyList();
        }
    }
}
