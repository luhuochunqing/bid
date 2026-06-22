package com.xiyu.bid.crm.application;

import com.xiyu.bid.crm.config.CrmProperties;
import com.xiyu.bid.crm.infrastructure.CrmHttpClient;
import com.xiyu.bid.crm.infrastructure.CrmResponseHandler;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OSS 登录流程服务。
 * <p>
 * 按泊冉文档要求的顺序调用 OSS 接口：
 * <ol>
 *   <li>POST /oauth/login - 获取 token</li>
 *   <li>GET /oauth/getUserInfo - 获取员工信息</li>
 *   <li>GET /oauth/getUserPermission - 获取系统权限</li>
 *   <li>POST /oss/.../getUserJobListByJobNumberList - 获取用户角色</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OssLoginFlowService {

    private final CrmHttpClient crmHttpClient;
    private final CrmProperties crmProperties;
    private final CrmPermissionService permissionService;

    /**
     * 直接使用用户名和密码进行 OSS 认证（不依赖 User entity）。
     * @param username 用户名
     * @param password 密码
     * @return 登录结果
     */
    public OssLoginResult authenticateDirect(String username, String password) {
        String baseUrl = crmProperties.getEffectiveAuthBaseUrl();
        String systemName = crmProperties.getOauthSystem();

        OssLoginResult.Builder result = OssLoginResult.builder();
        result.username(username);

        // Step 1: POST /oauth/login - 获取 token
        LinkedMultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("username", username);
        formData.add("password", password);
        formData.add("system", systemName);

        log.info("OSS login flow step 1: POST /oauth/login for user={}", username);
        CrmResponseHandler.CrmApiResponse loginResponse = crmHttpClient.postForm(
                baseUrl, crmProperties.getAuth().getOauthLoginPath(), formData);

        if (!loginResponse.success() || loginResponse.data() == null) {
            log.warn("OSS login failed for user={} code={} msg={}",
                    username, loginResponse.code(), loginResponse.msg());
            result.authenticated(false);
            return result.build();
        }

        String accessToken = loginResponse.data().path("access_token").asText();
        result.authenticated(true);
        result.ossAccessToken(accessToken);
        log.info("OSS login succeeded for user={}", username);

        if (accessToken == null || accessToken.isBlank()) {
            log.warn("OSS login returned empty access_token for user={}", username);
            return result.build();
        }

        // Step 2: GET /oauth/getUserInfo - 获取员工信息
        try {
            String employeePath = crmProperties.getAuth().getEmployeePath();
            CrmResponseHandler.CrmApiResponse employeeResponse = crmHttpClient.get(
                    baseUrl, employeePath, accessToken);
            if (employeeResponse != null && employeeResponse.data() != null) {
                result.employeeInfo(employeeResponse.data());
                log.info("OSS user info retrieved for user={}", username);
            }
        } catch (RuntimeException e) {
            log.warn("OSS getUserInfo failed (non-fatal): {}", e.getMessage());
        }

        // Step 3: GET /oauth/getUserPermission - 获取系统权限
        try {
            CrmUserPermission permission = permissionService.getUserPermission(accessToken, systemName);
            if (permission != null && !permission.isEmpty()) {
                result.permission(permission);
                log.info("OSS user permission retrieved for user={}", username);
            }
        } catch (RuntimeException e) {
            log.warn("OSS getUserPermission failed (non-fatal): {}", e.getMessage());
        }

        return result.build();
    }
}
