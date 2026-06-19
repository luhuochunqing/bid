package com.xiyu.bid.crm.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.crm.config.CrmProperties;
import com.xiyu.bid.crm.infrastructure.CrmHttpClient;
import com.xiyu.bid.crm.infrastructure.CrmResponseHandler;
import com.xiyu.bid.crm.infrastructure.dto.CustomerChanceVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * CRM 商机详情查询服务。
 * <p>对应客户接口 POST /customer-chance/detail?id={id}，按商机主键 id 查询商机详情。
 * <p>用于外部系统推送标讯时只携带商机主键 id（未携带 code 商机编号）的场景：
 * 通过 id 反查 code，以便后续标讯状态回传（bidInfoSync）能正确匹配商机。
 * <p>降级策略：查询失败或未找到返回 null，不中断主流程。
 */
@Service
public class CrmChanceDetailService {

    private static final Logger log = LoggerFactory.getLogger(CrmChanceDetailService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CrmHttpClient httpClient;
    private final CrmAuthService authService;
    private final CrmProperties properties;

    public CrmChanceDetailService(CrmHttpClient httpClient, CrmAuthService authService,
                                  CrmProperties properties) {
        this.httpClient = httpClient;
        this.authService = authService;
        this.properties = properties;
    }

    /**
     * 按商机主键 id 查询商机详情。
     *
     * @param id CRM 商机主键 id
     * @return 商机详情；id 为 null、查询失败或未找到时返回 null
     */
    public CustomerChanceVO getDetailById(Long id) {
        if (id == null) {
            return null;
        }
        String token = authService.getValidToken();
        String baseUrl = properties.getEffectiveChanceBaseUrl();
        String path = properties.getChance().getDetailPath() + "?id=" + id;
        log.info("CRM chance detail request: baseUrl={}, path={}, id={}", baseUrl, path, id);
        CrmResponseHandler.CrmApiResponse response = httpClient.post(baseUrl, path, token, null);

        if (response.isUnauthorized()) {
            authService.handleUnauthorized();
            token = authService.getValidToken();
            response = httpClient.post(baseUrl, path, token, null);
        }

        if (!response.success() || response.data() == null) {
            log.warn("CRM chance detail failed: code={}, msg={}, id={}", response.code(), response.msg(), id);
            return null;
        }
        try {
            String json = MAPPER.writeValueAsString(response.data());
            return MAPPER.readValue(json, CustomerChanceVO.class);
        } catch (JsonProcessingException | RuntimeException e) {
            log.error("Failed to parse CRM chance detail response for id={}", id, e);
            return null;
        }
    }
}
