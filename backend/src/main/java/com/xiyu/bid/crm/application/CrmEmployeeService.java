package com.xiyu.bid.crm.application;

import com.xiyu.bid.crm.config.CrmProperties;
import com.xiyu.bid.crm.infrastructure.CrmHttpClient;
import com.xiyu.bid.crm.infrastructure.CrmResponseHandler;
import org.springframework.stereotype.Service;

@Service
public class CrmEmployeeService {

    private final CrmHttpClient httpClient;
    private final CrmProperties properties;

    public CrmEmployeeService(CrmHttpClient httpClient, CrmProperties properties) {
        this.httpClient = httpClient;
        this.properties = properties;
    }

    /**
     * 根据用户 token 获取员工信息。
     * 调用 OSS 接口 GET /oauth/getUserInfo。
     *
     * @param userAccessToken 用户的 OSS access token
     * @return 员工信息
     */
    public CrmResponseHandler.CrmApiResponse getEmployeeByToken(String userAccessToken) {
        String baseUrl = properties.getEffectiveAuthBaseUrl();
        String path = properties.getAuth().getEmployeePath();  // /oauth/getUserInfo
        // GET 请求，Bearer token 在 Header 中
        return httpClient.get(baseUrl, path, userAccessToken);
    }
}
