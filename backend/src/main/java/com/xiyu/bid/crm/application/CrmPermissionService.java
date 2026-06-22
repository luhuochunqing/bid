package com.xiyu.bid.crm.application;

import com.xiyu.bid.crm.config.CrmProperties;
import com.xiyu.bid.crm.infrastructure.CrmHttpClient;
import com.xiyu.bid.crm.infrastructure.CrmResponseHandler;
import com.xiyu.bid.integration.organization.application.OrganizationIntegrationProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OSS 用户权限服务。
 * <p>
 * 调用 GET /oauth/getUserPermission 获取用户系统权限。
 * <p>
 * 响应格式（泊冉标准）：
 * <pre>
 * {
 *   "code": 0,
 *   "message": "success",
 *   "data": {
 *     "OPC": ["sale_log", "sale_order"],
 *     "SMS": ["sys_org", "sys_user"]
 *   }
 * }
 * </pre>
 */
@Service
public class CrmPermissionService {

    private static final Logger log = LoggerFactory.getLogger(CrmPermissionService.class);

    private final CrmHttpClient httpClient;
    private final CrmProperties properties;
    private final OrganizationIntegrationProperties orgProperties;
    private final OssPermissionCache permissionCache;

    public CrmPermissionService(CrmHttpClient httpClient, CrmProperties properties,
                                OrganizationIntegrationProperties orgProperties,
                                OssPermissionCache permissionCache) {
        this.httpClient = httpClient;
        this.properties = properties;
        this.orgProperties = orgProperties;
        this.permissionCache = permissionCache;
    }

    public CrmUserPermission getUserPermission(String userAccessToken, String systemName) {
        // 缓存 key 必须基于完整 token 的 hash，不能用 token 前缀
        // 因为 JWT token 前缀（header 部分）对所有用户都相同，会导致缓存串号
        String cacheKey = buildCacheKey(userAccessToken, systemName);
        java.util.Optional<CrmUserPermission> cached = permissionCache.get(cacheKey);
        if (cached.isPresent()) {
            log.debug("OSS permission cache hit for key={}", cacheKey);
            return cached.get();
        }
        // 使用 OSS 的 base URL（而非 CRM 的 auth base URL）
        String baseUrl = orgProperties.getDirectory().getBaseUrl();
        String path = orgProperties.getDirectory().getUserMenuTreePath();
        if (systemName != null && !systemName.isBlank()) {
            path = path + "?systemName=" + systemName;
        }
        CrmResponseHandler.CrmApiResponse response = httpClient.get(baseUrl, path, userAccessToken);
        if (response == null || response.data() == null) {
            log.warn("OSS user permission response empty");
            return new CrmUserPermission(Collections.emptyMap());
        }
        CrmUserPermission permission = parsePermission(response.data());
        permissionCache.put(cacheKey, permission);
        log.info("OSS user permission cached for key={}, size={}", cacheKey,
                permission.systemPermissions().size());
        return permission;
    }

    public CrmUserPermission getUserPermission(String userAccessToken) {
        return getUserPermission(userAccessToken, orgProperties.getDirectory().getUserMenuTreeSystemName());
    }

    private CrmUserPermission parsePermission(JsonNode data) {
        Map<String, List<String>> systemPermissions = new LinkedHashMap<>();
        data.fields().forEachRemaining(entry -> {
            String systemName = entry.getKey();
            JsonNode menuList = entry.getValue();
            if (menuList != null && menuList.isArray()) {
                List<String> menus = new java.util.ArrayList<>();
                for (JsonNode item : menuList) {
                    if (item.isTextual()) {
                        menus.add(item.asText());
                    }
                }
                systemPermissions.put(systemName, menus);
            }
        });
        return new CrmUserPermission(systemPermissions);
    }

    /**
     * 构建权限缓存 key。
     * <p>
     * 使用完整 access_token 的 SHA-256 hash 作为用户标识，避免 JWT token 前缀
     * （header 部分）对所有用户相同导致的缓存串号问题。
     */
    private String buildCacheKey(String userAccessToken, String systemName) {
        String tokenHash;
        if (userAccessToken == null || userAccessToken.isBlank()) {
            tokenHash = "unknown";
        } else {
            try {
                java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
                byte[] hashBytes = md.digest(userAccessToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                for (byte b : hashBytes) {
                    sb.append(String.format("%02x", b));
                }
                tokenHash = sb.toString();
            } catch (java.security.NoSuchAlgorithmException e) {
                // SHA-256 是 JDK 内置算法，理论上不会缺失；降级为完整 token 的 hashCode
                tokenHash = String.valueOf(userAccessToken.hashCode());
            }
        }
        return tokenHash + "::" + (systemName != null ? systemName : "default");
    }
}
