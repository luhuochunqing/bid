package com.xiyu.bid.crm.application;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.integration.organization.application.OrganizationIntegrationProperties;
import com.xiyu.bid.integration.organization.domain.policy.JobRoleLookupResolver;
import com.xiyu.bid.integration.organization.domain.policy.OssMenuPermissionMapper;
import com.xiyu.bid.integration.organization.infrastructure.mapper.PositionToRoleMapper;
import com.xiyu.bid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * OSS 角色/权限解析器。
 * <p>
 * 从 OSS jobList 解析内部角色码，并将 OSS 权限码映射为内部菜单权限码。
 * <p>
 * 优先级：
 * <ol>
 *   <li>sysRoleList 中 status=1 且 roleCode 在内部白名单的角色（直接用 roleCode）</li>
 *   <li>sysRoleList 中 status=1 的角色（先用 PositionToRoleMapper 正则匹配，再用 OSS 角色码硬编码映射）</li>
 *   <li>jobName（先用 PositionToRoleMapper 正则匹配，再用 OSS 角色码硬编码映射）</li>
 *   <li>fallback 到本地 User.roleCode</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OssRoleResolver {

    private final UserRepository userRepository;
    private final OrganizationIntegrationProperties organizationProperties;
    private final PositionToRoleMapper positionToRoleMapper;

    /**
     * 从 OSS jobList 解析内部角色码。
     * <p>
     * OSS sysRoleList 中的 roleCode（如 admin、HomeReadonly、KunLunAdmin 等）是 OSS/Home/CRM
     * 等系统的角色码，不是 bid 系统的 RoleProfile code。即使名字相同（如 OSS 的 "admin" 和 bid
     * 的 "admin"）也是不同系统的角色，不能直接复用。
     * <p>
     * 因此本方法只通过以下途径解析 bid 角色码：
     * <ol>
     *   <li>sysRoleList 中 status=1 的 roleName（中文角色名称，如"投标管理员"）通过映射表匹配</li>
     *   <li>jobName（岗位名，如"投标项目负责人"）通过映射表或正则匹配</li>
     *   <li>fallback 到本地 User.roleCode</li>
     * </ol>
     *
     * @param jobList          OSS 返回的 jobList
     * @param jobNumber        用户工号
     * @param fallbackUsername 当 jobList 解析失败时的兜底用户名（用于查本地 DB）
     * @return 解析到的内部角色码，可能为 null
     */
    public String resolveRoleCodeFromJobList(CrmJobListResponse jobList, String jobNumber, String fallbackUsername) {
        if (jobList == null || jobList.getData() == null || jobNumber == null) {
            log.warn("OSS login: jobList or jobNumber is null, denying role resolution for user={}", fallbackUsername);
            return null;
        }
        CrmJobListResponse.JobInfo jobInfo = jobList.getData().get(jobNumber);
        if (jobInfo == null) {
            log.warn("OSS login: jobInfo not found for jobNumber={}, denying role resolution for user={}", jobNumber, fallbackUsername);
            return null;
        }

        // 1. 从 sysRoleList 的 roleName 解析（不使用 roleCode，因为 OSS roleCode 是 OSS 系统角色码）
        if (jobInfo.getSysRoleList() != null) {
            for (CrmJobListResponse.SysRole sysRole : jobInfo.getSysRoleList()) {
                if ("1".equals(sysRole.getStatus()) && !Boolean.TRUE.equals(sysRole.getDel())) {
                    String roleName = sysRole.getRoleName();
                    if (roleName != null && !roleName.isBlank()) {
                        String roleCode = JobRoleLookupResolver.mapOssRoleTextToInternal(roleName);
                        if (roleCode == null || roleCode.isBlank()) {
                            roleCode = positionToRoleMapper.map(roleName);
                        }
                        if (roleCode != null && !roleCode.isBlank()) {
                            log.info("OSS login: role resolved from sysRoleList roleName: {} -> {}", roleName, roleCode);
                            return roleCode;
                        }
                    }
                }
            }
        }

        // 2. 从 jobName 解析
        String jobName = jobInfo.getJobName();
        if (jobName != null && !jobName.isBlank()) {
            String roleCode = JobRoleLookupResolver.mapOssRoleTextToInternal(jobName);
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
    public String resolveLocalRoleCode(String username) {
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
    public List<String> mapOssPermissionsToInternal(CrmUserPermission permission, String systemName) {
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
