package com.xiyu.bid.crm.application;

import com.xiyu.bid.crm.config.CrmProperties;
import com.xiyu.bid.crm.infrastructure.CrmHttpClient;
import com.xiyu.bid.crm.infrastructure.CrmResponseHandler;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class CrmEmployeeService {

    private final CrmHttpClient httpClient;
    private final CrmAuthService authService;
    private final CrmProperties properties;

    public CrmEmployeeService(CrmHttpClient httpClient, CrmAuthService authService,
                              CrmProperties properties) {
        this.httpClient = httpClient;
        this.authService = authService;
        this.properties = properties;
    }

    public CrmResponseHandler.CrmApiResponse getEmployeeByToken(String employeeToken) {
        String token = authService.getValidToken();
        String baseUrl = properties.getEffectiveAuthBaseUrl();
        String path = properties.getAuth().getEmployeePath();
        return httpClient.post(baseUrl, path, token,
                Map.of("token", employeeToken));
    }
}
