package com.xiyu.bid.crm.application;

import com.xiyu.bid.crm.config.CrmProperties;
import com.xiyu.bid.crm.infrastructure.CrmHttpClient;
import com.xiyu.bid.crm.infrastructure.CrmResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class CrmMenuService {

    private static final Logger LOG = LoggerFactory.getLogger(CrmMenuService.class);

    private final CrmHttpClient httpClient;
    private final CrmAuthService authService;
    private final CrmProperties properties;

    public CrmMenuService(CrmHttpClient httpClient, CrmAuthService authService,
                          CrmProperties properties) {
        this.httpClient = httpClient;
        this.authService = authService;
        this.properties = properties;
    }

    public CrmResponseHandler.CrmApiResponse getMenuTree(String systemType, String username) {
        String token;
        try {
            token = authService.getValidOssTokenForUser(username);
        } catch (TokenUnavailableException e) {
            LOG.warn("getMenuTree skipped: token unavailable for username={}: {}", username, e.getMessage());
            return new CrmResponseHandler.CrmApiResponse(401, "token unavailable", null, false);
        }
        String baseUrl = properties.getEffectiveAuthBaseUrl();
        String path = properties.getAuth().getMenuTreePath();
        return httpClient.post(baseUrl, path, token,
                Map.of("systemType", systemType));
    }
}
