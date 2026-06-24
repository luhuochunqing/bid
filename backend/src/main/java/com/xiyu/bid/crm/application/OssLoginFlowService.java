package com.xiyu.bid.crm.application;

import com.xiyu.bid.security.domain.LoginRoleWhitelist;
import com.xiyu.bid.crm.config.CrmProperties;
import com.xiyu.bid.crm.infrastructure.CrmHttpClient;
import com.xiyu.bid.crm.infrastructure.CrmResponseHandler;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.integration.organization.application.OrganizationIntegrationProperties;
import com.xiyu.bid.integration.organization.domain.policy.JobRoleLookupResolver;
import com.xiyu.bid.integration.organization.domain.policy.OssMenuPermissionMapper;
import com.xiyu.bid.integration.organization.infrastructure.mapper.PositionToRoleMapper;
import com.xiyu.bid.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;

import java.util.ArrayList;
import java.util.List;
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
 *   <li>解析角色+权限写入内存缓存（不写本地 DB）</li>
 * </ol>
 * <p>
 * 缓存策略：登录时写入 {@link OssPermissionCache}，登出时由 AuthService 调用 invalidate 删除。
 * 不再读取本地 DB 的 RoleProfile.menu_permissions，所有鉴权读取均来自内存缓存。
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
    private final OrganizationIntegrationProperties organizationProperties;
    private final OssPermissionCache ossPermissionCache;
    private final PositionToRoleMapper positionToRoleMapper;

    /**
     * 直接使用用户名和密码进行 OSS 认证（不依赖 User entity）。
     * @param username 用户名
     * @param password 密码
     * @return 登录结果
     */
    public OssLoginResult authenticateDirect(String username, String password) {
        String baseUrl = crmProperties.getEffectiveAuthBaseUrl();
        String oauthSystem = crmProperties.getAuth().getUserLoginSystem();
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

        // Step 5: 解析角色+权限，写入内存缓存（不写本地 DB）
        OssLoginResult built = result.build();
        cacheOssPermissions(built, username, jobNumber, permissionSystemName);

        return built;
    }

    /**
     * 解析 OSS 返回的角色+权限，写入内存缓存。
     * <p>
     * 替代原 syncOssPermissionsToRole 方法：不再写本地 DB RoleProfile.menu_permissions，
     * 改为写入 OssPermissionCache，供 UserDetailsServiceImpl 和 DataScopeConfigService 读取。
     * <p>
     * 若解析到的角色不在登录白名单（staff/未映射角色），则清空缓存并拒绝写入，
     * 确保这些用户无法通过缓存角色登录。
     */
    private void cacheOssPermissions(OssLoginResult loginResult, String username,
                                      String jobNumber, String permissionSystemName) {
        try {
            CrmUserPermission permission = loginResult.getPermission();
            if (permission == null || permission.isEmpty()) {
                log.info("OSS login: no permission to cache for user={}", username);
                return;
            }

            String resolvedRoleCode = resolveRoleCodeFromJobList(loginResult.getJobList(), jobNumber, username);
            if (!LoginRoleWhitelist.isAllowed(resolvedRoleCode)) {
                log.warn("OSS login: role not allowed for user={}, roleCode={}, clearing cache",
                        username, resolvedRoleCode);
                ossPermissionCache.invalidate(username);
                return;
            }

            List<String> menuPermissions = mapOssPermissionsToInternal(permission, permissionSystemName);
            ossPermissionCache.put(username, resolvedRoleCode, menuPermissions, permission);
            log.info("OSS login: permission cached (not written to DB): username={}, roleCode={}, menuPermissions={}",
                    username, resolvedRoleCode, menuPermissions);
        } catch (RuntimeException e) {
            log.warn("OSS login: permission caching failed (non-fatal) for user={}: {}", username, e.getMessage());
        }
    }

    /**
     * 从 OSS jobList 解析内部角色码。
     * <p>
     * 优先级：
     * 1. sysRoleList 中 status=1 的角色（先用 PositionToRoleMapper 正则匹配，再用 OSS 角色码硬编码映射）
     * 2. jobName（先用 PositionToRoleMapper 正则匹配，再用 OSS 角色码硬编码映射）
     * 3. fallback 到本地 User.roleCode
     */
    private String resolveRoleCodeFromJobList(CrmJobListResponse jobList, String jobNumber, String fallbackUsername) {
        if (jobList == null || jobList.getData() == null || jobNumber == null) {
            log.warn("OSS login: jobList or jobNumber is null, denying role resolution for user={}", fallbackUsername);
            return null;
        }
        CrmJobListResponse.JobInfo jobInfo = jobList.getData().get(jobNumber);
        if (jobInfo == null) {
            log.warn("OSS login: jobInfo not found for jobNumber={}, denying role resolution for user={}", jobNumber, fallbackUsername);
            return null;
        }

        // 1. 优先从 sysRoleList 解析
        if (jobInfo.getSysRoleList() != null) {
            for (CrmJobListResponse.SysRole sysRole : jobInfo.getSysRoleList()) {
                if ("1".equals(sysRole.getStatus()) && !Boolean.TRUE.equals(sysRole.getDel())) {
                    String roleName = sysRole.getRoleName();
                    if (roleName != null && !roleName.isBlank()) {
                        // 先用综合映射（OSS 角色码 + 中文角色名称精确匹配）
                        String roleCode = JobRoleLookupResolver.mapOssRoleTextToInternal(roleName);
                        // 再用 PositionToRoleMapper 正则匹配岗位名（如"主管"等宽泛匹配）
                        if (roleCode == null || roleCode.isBlank()) {
                            roleCode = positionToRoleMapper.map(roleName);
                        }
                        if (roleCode != null && !roleCode.isBlank()) {
                            log.info("OSS login: role resolved from sysRoleList: {} -> {}", roleName, roleCode);
                            return roleCode;
                        }
                    }
                }
            }
        }

        // 2. 从 jobName 解析
        String jobName = jobInfo.getJobName();
        if (jobName != null && !jobName.isBlank()) {
            // 先用综合映射（OSS 角色码 + 中文角色名称精确匹配）
            String roleCode = JobRoleLookupResolver.mapOssRoleTextToInternal(jobName);
            // 再用 PositionToRoleMapper 正则匹配岗位名
            if (roleCode == null || roleCode.isBlank()) {
                roleCode = positionToRoleMapper.map(jobName);
            }
            if (roleCode != null && !roleCode.isBlank()) {
                log.info("OSS login: role resolved from jobName: {} -> {}", jobName, roleCode);
                return roleCode;
            }
        }

        // 3. fallback 到本地 User.roleCode
        return resolveLocalRoleCode(fallbackUsername);
    }

    /**
     * 从本地 DB 读取 User.roleCode 作为兜底。
     */
    private String resolveLocalRoleCode(String username) {
        try {
            return userRepository.findByUsername(username)
                    .map(User::getRoleCode)
                    .orElse(null);
        } catch (RuntimeException e) {
            log.warn("OSS login: failed to resolve local role code for user={}: {}", username, e.getMessage());
            return null;
        }
    }

    /**
     * 将 OSS 权限码列表映射为内部菜单权限码列表。
     */
    private List<String> mapOssPermissionsToInternal(CrmUserPermission permission, String systemName) {
        OrganizationIntegrationProperties.Directory directory = organizationProperties.getDirectory();
        if (directory == null) {
            log.warn("OSS login: Directory config not available, skip permission mapping");
            return List.of();
        }
        List<String> ossMenuCodes = permission.getMenusForSystem(systemName);
        if (ossMenuCodes.isEmpty()) {
            log.info("OSS login: no menu codes for system={}", systemName);
            return List.of();
        }
        log.info("OSS login: raw menu codes for user, system={}, codes={}", systemName, ossMenuCodes);
        OssMenuPermissionMapper mapper = new OssMenuPermissionMapper(
                directory.getMenuCodeToPermissionKeyMappings(),
                directory.getUnmappedMenuCodeBehavior()
        );
        Set<String> internalPermissions = mapper.mapCodes(ossMenuCodes);
        log.info("OSS login: mapped internal permissions={}", internalPermissions);
        return new ArrayList<>(internalPermissions);
    }
}
