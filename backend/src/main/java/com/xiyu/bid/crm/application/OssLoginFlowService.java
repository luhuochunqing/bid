package com.xiyu.bid.crm.application;

import com.xiyu.bid.security.domain.LoginRoleWhitelist;
import com.xiyu.bid.crm.config.CrmProperties;
import com.xiyu.bid.crm.infrastructure.CrmHttpClient;
import com.xiyu.bid.crm.infrastructure.CrmResponseHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;

import java.util.List;

/**
 * OSS 登录流程服务。
 * <p>
 * 按泊冉文档要求的顺序调用 OSS 接口：
 * <ol>
 *   <li>POST /oauth/login - 获取 token</li>
 *   <li>GET /oauth/getUserInfo - 获取员工信息</li>
 *   <li>GET /oauth/getUserPermission - 获取系统权限</li>
 *   <li>POST /oss/.../getUserJobListByJobNumberList - 获取用户角色</li>
 *   <li>解析角色+权限写入内存缓存（不写本地 DB）</li>
 * </ol>
 * <p>
 * 角色/权限解析已拆分到 {@link OssRoleResolver}。
 * 缓存策略：登录时写入 {@link OssPermissionCache}，登出时由 AuthService 调用 invalidate 删除。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OssLoginFlowService {

    private final CrmHttpClient crmHttpClient;
    private final CrmProperties crmProperties;
    private final CrmPermissionService permissionService;
    private final CrmRoleService roleService;
    private final OssPermissionCache ossPermissionCache;
    private final OssRoleResolver ossRoleResolver;

    /**
     * 直接使用用户名和密码进行 OSS 认证（不依赖 User entity）。
     * @param username 用户名
     * @param password 密码
     * @return 登录结果
     */
    public OssLoginResult authenticateDirect(String username, String password) {
        String baseUrl = crmProperties.getEffectiveAuthBaseUrl();
        String userLoginSystem = crmProperties.getAuth().getUserLoginSystem();

        OssLoginResult.Builder result = OssLoginResult.builder();
        result.username(username);

        // Step 1: POST /oauth/login - 获取 token
        LinkedMultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("username", username);
        formData.add("password", password);
        formData.add("system", userLoginSystem);

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

        // Step 2-5: 用 token 走后续 OSS 流程（getUserInfo → getUserPermission → getUserJobList → 写缓存）
        return executePostLoginFlow(accessToken, username, result);
    }

    /**
     * SSO 场景：用 Home 平台已有的 token 走完整 OSS 流程，不调用 /oauth/login。
     * <p>
     * 按接口文档 oss-integration-api.md §单点登录（SSO）实现方案：方式 B（直接获取用户信息）。
     * 调用 OSS 实时接口获取用户信息、权限、角色，写入内存缓存，不依赖本地 DB 判断用户有效性。
     *
     * @param token Home 平台跳转携带的 access_token
     * @return 登录结果（包含员工信息、权限等）
     */
    public OssLoginResult authenticateWithExistingToken(String token) {
        OssLoginResult.Builder result = OssLoginResult.builder();
        result.ossAccessToken(token);
        result.authenticated(true);

        // 直接走 step 2-5（username 在 getUserInfo 返回后填充）
        return executePostLoginFlow(token, null, result);
    }

    /**
     * 执行 OSS 登录后续流程：getUserInfo → getUserPermission → getUserJobList → 写缓存。
     * <p>
     * 供两个场景复用：
     * - 普通登录：authenticateDirect 在 step 1 拿到 token 后调用
     * - SSO 登录：authenticateWithExistingToken 直接用现有 token 调用
     */
    private OssLoginResult executePostLoginFlow(String accessToken, String fallbackUsername,
                                                 OssLoginResult.Builder result) {
        String baseUrl = crmProperties.getEffectiveAuthBaseUrl();
        String permissionSystemName = crmProperties.getAuth().getUserPermissionSystemName();
        String username = fallbackUsername;
        String jobNumber = null;

        // Step 2: GET /oauth/getUserInfo - 获取员工信息
        // OSS 返回的 employeeInfo 中，username 字段就是工号(jobNumber)
        try {
            String employeePath = crmProperties.getAuth().getEmployeePath();
            CrmResponseHandler.CrmApiResponse employeeResponse = crmHttpClient.get(
                    baseUrl, employeePath, accessToken);
            if (employeeResponse != null && employeeResponse.data() != null) {
                result.employeeInfo(employeeResponse.data());
                jobNumber = employeeResponse.data().path("username").asText(null);
                if (jobNumber != null && !jobNumber.isBlank()) {
                    username = jobNumber;
                    result.username(username);
                }
                log.info("OSS user info retrieved for user={}, jobNumber={}", username, jobNumber);
            } else {
                log.warn("OSS getUserInfo returned empty data, token may be invalid");
                result.authenticated(false);
                return result.build();
            }
        } catch (RuntimeException e) {
            log.warn("OSS getUserInfo failed: {}", e.getMessage());
            result.authenticated(false);
            return result.build();
        }

        // Step 3: GET /oauth/getUserPermission - 获取系统权限
        try {
            CrmUserPermission permission = permissionService.getUserPermission(accessToken, permissionSystemName);
            if (permission != null && !permission.isEmpty()) {
                result.permission(permission);
                log.info("OSS user permission retrieved for user={}", username);
            }
        } catch (RuntimeException e) {
            log.warn("OSS getUserPermission failed (non-fatal): {}", e.getMessage());
        }

        // Step 4: POST /oss/.../getUserJobListByJobNumberList - 获取用户角色
        if (jobNumber != null && !jobNumber.isBlank()) {
            try {
                CrmJobListResponse jobList = roleService.getUserJobList(List.of(jobNumber));
                if (jobList != null) {
                    result.jobList(jobList);
                    log.info("OSS user job list retrieved for user={}, jobNumber={}", username, jobNumber);
                }
            } catch (RuntimeException e) {
                log.warn("OSS getUserJobList failed (non-fatal): {}", e.getMessage());
            }
        }

        // Step 5: 解析角色+权限，写入内存缓存（不写本地 DB）
        OssLoginResult built = result.build();
        cacheOssPermissions(built, username, jobNumber, permissionSystemName);

        return built;
    }

    /**
     * 解析 OSS 返回的角色+权限，写入内存缓存。
     * <p>
     * 若解析到的角色不在登录白名单（staff/未映射角色），则清空缓存并拒绝写入，
     * 确保这些用户无法通过缓存角色登录。
     */
    private void cacheOssPermissions(OssLoginResult loginResult, String username,
                                      String jobNumber, String permissionSystemName) {
        try {
            CrmUserPermission permission = loginResult.getPermission();
            if (permission == null || permission.isEmpty()) {
                log.info("OSS login: no permission to cache for user={}, will try role-only caching", username);
                permission = new CrmUserPermission(java.util.Collections.emptyMap());
            }

            // 即使 permission 为空，也尝试从 jobList 解析角色并缓存（SSO 场景用户可能未配置系统权限）
            String resolvedRoleCode = ossRoleResolver.resolveRoleCodeFromJobList(
                    loginResult.getJobList(), jobNumber, username);
            if (!LoginRoleWhitelist.isAllowed(resolvedRoleCode)) {
                log.warn("OSS login: role not allowed for user={}, roleCode={}, clearing cache",
                        username, resolvedRoleCode);
                ossPermissionCache.invalidate(username);
                return;
            }

            List<String> menuPermissions = ossRoleResolver.mapOssPermissionsToInternal(permission, permissionSystemName);
            ossPermissionCache.put(username, resolvedRoleCode, menuPermissions, permission);
            log.info("OSS login: permission cached (not written to DB): username={}, roleCode={}, menuPermissions={}",
                    username, resolvedRoleCode, menuPermissions);
        } catch (RuntimeException e) {
            log.warn("OSS login: permission caching failed (non-fatal) for user={}: {}", username, e.getMessage());
        }
    }
}
