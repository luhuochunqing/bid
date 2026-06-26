package com.xiyu.bid.crm.infrastructure;

import com.xiyu.bid.crm.config.CrmProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Optional;

@Component
public class HomeSsoClient {

    private static final Logger log = LoggerFactory.getLogger(HomeSsoClient.class);

    private final CrmHttpClient crmHttpClient;
    private final CrmProperties properties;

    public HomeSsoClient(CrmHttpClient crmHttpClient, CrmProperties properties) {
        this.crmHttpClient = crmHttpClient;
        this.properties = properties;
    }

    public Optional<String> validateTokenAndGetUsername(String token) {
        try {
            MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
            queryParams.add("token", token);

            CrmResponseHandler.CrmApiResponse response = crmHttpClient.getWithQueryParams(
                    properties.getEffectiveAuthBaseUrl(),
                    "/oauth/getCheckToken",
                    queryParams
            );

            if (!response.success() || response.code() != 0) {
                log.warn("Home SSO token validation failed: code={}, msg={}", response.code(), response.msg());
                return Optional.empty();
            }

            JsonNode data = response.data();
            if (data == null || data.isNull() || !data.has("user_name")) {
                log.warn("Home SSO token validation response missing user_name");
                return Optional.empty();
            }

            String username = data.path("user_name").asText(null);
            if (username == null || username.isBlank()) {
                log.warn("Home SSO token validation returned blank user_name");
                return Optional.empty();
            }

            return Optional.of(username);
        } catch (RuntimeException e) {
            log.error("Home SSO token validation error: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
