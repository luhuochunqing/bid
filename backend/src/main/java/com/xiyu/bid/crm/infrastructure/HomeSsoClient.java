package com.xiyu.bid.crm.infrastructure;

import com.xiyu.bid.crm.config.CrmProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

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

    /**
     * 调用 Home 平台 /oauth/getUserInfo 接口校验 token 并获取用户名。
     * 按接口文档 oss-integration-api.md §单点登录（SSO）实现方案：方式 B（直接获取用户信息）。
     * token 通过 Authorization: Bearer header 传递，返回 data.username 即用户工号。
     */
    public Optional<String> validateTokenAndGetUsername(String token) {
        try {
            CrmResponseHandler.CrmApiResponse response = crmHttpClient.get(
                    properties.getEffectiveAuthBaseUrl(),
                    "/oauth/getUserInfo",
                    token
            );

            if (!response.success() || response.code() != 0) {
                log.warn("Home SSO token validation failed: code={}, msg={}", response.code(), response.msg());
                return Optional.empty();
            }

            JsonNode data = response.data();
            if (data == null || data.isNull() || !data.has("username")) {
                log.warn("Home SSO token validation response missing username");
                return Optional.empty();
            }

            String username = data.path("username").asText(null);
            if (username == null || username.isBlank()) {
                log.warn("Home SSO token validation returned blank username");
                return Optional.empty();
            }

            log.info("Home SSO token validation success: username={}, nickName={}",
                    username, data.path("nickName").asText(""));
            return Optional.of(username);
        } catch (RuntimeException e) {
            log.error("Home SSO token validation error: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
