package com.xiyu.bid.crm.application;

import com.xiyu.bid.crm.config.CrmProperties;
import com.xiyu.bid.crm.infrastructure.CrmHttpClient;
import com.xiyu.bid.crm.infrastructure.CrmResponseHandler;
import com.xiyu.bid.entity.RoleProfile;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.integration.organization.application.OrganizationDirectoryGateway;
import com.xiyu.bid.integration.organization.application.OrganizationIntegrationProperties;
import com.xiyu.bid.integration.organization.domain.OrganizationDirectoryLookupContext;
import com.xiyu.bid.integration.organization.domain.policy.OssMenuPermissionMapper;
import com.xiyu.bid.integration.organization.dto.OssMenuTreeNode;
import com.xiyu.bid.repository.RoleProfileRepository;
import com.xiyu.bid.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
    private final CrmRoleService roleService;
    private final UserRepository userRepository;
    private final RoleProfileRepository roleProfileRepository;
    private final ObjectProvider<OrganizationDirectoryGateway> gatewayProvider;
    private final ObjectProvider<OrganizationIntegrationProperties.Directory> directoryProvider;

    /**
     * 直接使用用户名和密码进行 OSS 认证（不依赖 User entity）。
     * @param username 用户名
     * @param password 密码
     * @return 登录结果
     */
    public OssLoginResult authenticateDirect(String username, String password) {
        String baseUrl = crmProperties.getEffectiveAuthBaseUrl();
        String oauthSystem = crmProperties.getOauthSystem();
        String permissionSystemName = crmProperties.getAuth().getUserPermissionSystemName();

        OssLoginResult.Builder result = OssLoginResult.builder();
        result.username(username);

        // Step 1: POST /oauth/login - 获取 token
        LinkedMultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("username", username);
        formData.add("password", password);
        formData.add("system", oauthSystem);

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
        // 注意：OSS返回的employeeInfo中，username字段就是工号(jobNumber)
        String jobNumber = null;
        try {
            String employeePath = crmProperties.getAuth().getEmployeePath();
            CrmResponseHandler.CrmApiResponse employeeResponse = crmHttpClient.get(
                    baseUrl, employeePath, accessToken);
            if (employeeResponse != null && employeeResponse.data() != null) {
                result.employeeInfo(employeeResponse.data());
                // OSS接口返回的username字段就是工号
                jobNumber = employeeResponse.data().path("username").asText(null);
                log.info("OSS user info retrieved for user={}, jobNumber={}", username, jobNumber);
            }
        } catch (RuntimeException e) {
            log.warn("OSS getUserInfo failed (non-fatal): {}", e.getMessage());
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

        // Step 5: 同步 OSS 权限到本地 RoleProfile（前端菜单显示需要）
        CrmUserPermission permission = result.build().getPermission();
        if (permission != null && !permission.isEmpty() && jobNumber != null) {
            syncOssPermissionsToRole(username, jobNumber, permission);
        }

        return result.build();
    }

    /**
     * 将 OSS 权限同步到用户的本地 RoleProfile。
     * <p>
     * OSS 返回的菜单 code（如 1001, 100402）需要映射为内部权限码（如 dashboard, knowledge-qualification），
     * 然后合并到用户的 RoleProfile.menu_permissions，供前端菜单显示使用。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void syncOssPermissionsToRole(String username, String jobNumber, CrmUserPermission permission) {
        try {
            // 1. 根据用户名查找本地用户
            Optional<User> userOpt = userRepository.findByUsername(username);
            if (userOpt.isEmpty()) {
                log.debug("OSS login: user not found in local DB, skip permission sync: username={}", username);
                return;
            }
            User user = userOpt.get();

            // 2. 获取用户的 RoleProfile
            String roleCode = user.getRoleCode();
            if (roleCode == null || roleCode.isBlank()) {
                log.debug("OSS login: user has no role code, skip permission sync: username={}", username);
                return;
            }
            Optional<RoleProfile> roleOpt = roleProfileRepository.findByCodeIgnoreCase(roleCode);
            if (roleOpt.isEmpty()) {
                log.debug("OSS login: role profile not found, skip permission sync: username={}, roleCode={}", username, roleCode);
                return;
            }
            RoleProfile role = roleOpt.get();

            // 3. 通过 OrganizationDirectoryGateway 获取 OSS 菜单树
            OrganizationDirectoryGateway gateway = gatewayProvider.getIfAvailable();
            if (gateway == null) {
                log.debug("OSS login: OrganizationDirectoryGateway not available, skip permission sync");
                return;
            }
            Optional<List<OssMenuTreeNode>> menuTree = gateway.fetchUserMenuTree(
                    jobNumber, OrganizationDirectoryLookupContext.empty());
            if (menuTree.isEmpty()) {
                log.info("OSS login: menu tree is empty, skip permission sync: username={}, jobNumber={}", username, jobNumber);
                return;
            }

            // 4. 获取 Directory 配置
            OrganizationIntegrationProperties.Directory directory = directoryProvider.getIfAvailable();
            if (directory == null) {
                log.debug("OSS login: Directory config not available, skip permission sync");
                return;
            }

            // 5. 使用 OssMenuPermissionMapper 将菜单 code 映射为内部权限码
            OssMenuPermissionMapper mapper = new OssMenuPermissionMapper(
                    directory.getMenuCodeToPermissionKeyMappings(),
                    directory.getUnmappedMenuCodeBehavior()
            );
            Set<String> internalPermissions = mapper.map(menuTree.get());

            // 6. 合并到 RoleProfile.menu_permissions
            Set<String> merged = new HashSet<>(role.getMenuPermissions());
            merged.addAll(internalPermissions);
            role.setMenuPermissions(new ArrayList<>(merged));
            roleProfileRepository.save(role);

            log.info("OSS login: permission sync completed: username={}, roleCode={}, newPermissions={}",
                    username, roleCode, internalPermissions);
        } catch (RuntimeException e) {
            log.error("OSS login: permission sync failed: username={}, error={}", username, e.getMessage());
            // 非致命错误，不影响登录流程
        }
    }
}
