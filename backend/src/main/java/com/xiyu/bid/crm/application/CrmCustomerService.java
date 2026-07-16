package com.xiyu.bid.crm.application;

import com.xiyu.bid.crm.config.CrmProperties;
import com.xiyu.bid.crm.infrastructure.CrmHttpClient;
import com.xiyu.bid.crm.infrastructure.CrmResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class CrmCustomerService {

    private static final Logger log = LoggerFactory.getLogger(CrmCustomerService.class);

    private final CrmHttpClient httpClient;
    private final CrmProperties properties;
    private final CrmApiTemplate apiTemplate;

    public CrmCustomerService(CrmHttpClient httpClient, CrmProperties properties, CrmApiTemplate apiTemplate) {
        this.httpClient = httpClient;
        this.properties = properties;
        this.apiTemplate = apiTemplate;
    }

    public CrmResponseHandler.CrmApiResponse searchCustomers(String keyword, int pageSize, String username) {
        Map<String, Object> body = Map.of("keyword", keyword, "pageSize", Math.min(pageSize, 20));
        String baseUrl = properties.getEffectiveCustomerBaseUrl();
        String path = properties.getCustomer().getSearchPath();
        // spec 037 Review L1：用 CrmApiTemplate 统一 401 重试样板
        return apiTemplate.executeWithTokenRetry(
                username,
                token -> httpClient.post(baseUrl, path, token, body),
                tokenUnavailableResponse(),
                "customer search");
    }

    public CrmResponseHandler.CrmApiResponse getCustomerContacts(List<String> customerIds, String username) {
        String baseUrl = properties.getEffectiveCustomerBaseUrl();
        String path = properties.getCustomer().getContactsPath();
        // spec 037 Review L1：补齐 401 重试（原实现只取一次 token，无重试，与其他 Service 不一致）
        return apiTemplate.executeWithTokenRetry(
                username,
                token -> httpClient.post(baseUrl, path, token, Map.of("customerIds", customerIds)),
                tokenUnavailableResponse(),
                "customer contacts");
    }

    private static CrmResponseHandler.CrmApiResponse tokenUnavailableResponse() {
        return new CrmResponseHandler.CrmApiResponse(401, "token unavailable", null, false);
    }
}
