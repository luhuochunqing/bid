package com.xiyu.bid.crm.application;

import com.xiyu.bid.integration.organization.application.OrganizationIntegrationProperties;
import com.xiyu.bid.integration.organization.domain.policy.JobRoleLookupResolver;
import com.xiyu.bid.integration.organization.domain.policy.OssMenuPermissionMapper;
import com.xiyu.bid.integration.organization.infrastructure.mapper.PositionToRoleMapper;
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
 *   <li>sysRoleList 中 status=1 且 roleName 能映射到内部角色</li>
 *   <li>jobName 能映射到内部角色</li>
 *   <li>无法解析时返回 null（fail-closed），不 fallback 到本地 DB roleCode</li>
 * </ol>
 * <p>
 * 注意：OSS 用户的角色必须来自 OSS 实时抓取；本地 User.roleCode 可能是历史同步值或
 * role_id=NULL 时的 fallback "manager"，不能作为 OSS 登录的权限来源。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OssRoleResolver {

    private final OssMenuPermissionMapper ossMenuPermissionMapper;
    private final PositionToRoleMapper positionToRoleMapper;
    private final OrganizationIntegrationProperties orgProperties;

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
     * @param jobList   OSS 返回的 jobList
     * @param jobNumber 用户工号
     * @return 解析到的内部角色码；无法解析时返回 null（fail-closed）
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

        // 0. 从人员白名单映射解析（最高优先级，与后台同步保持一致，防止 06234 等特权账号被拒绝）
        if (orgProperties != null && orgProperties.getPersonToRoleMappings() != null) {
            for (OrganizationIntegrationProperties.PersonToRoleMapping mapping : orgProperties.getPersonToRoleMappings()) {
                if (mapping.matches(jobNumber) || mapping.matches(fallbackUsername)) {
                    String roleCode = mapping.getRoleCode() == null ? null : mapping.getRoleCode().trim();
                    log.info("OSS login: role resolved from person-to-role-mappings: {}/{} -> {}", jobNumber, fallbackUsername, roleCode);
                    return roleCode;
                }
            }
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

        // 3. OSS 实时抓取无法解析到内部角色：fail-closed，不 fallback 到本地 DB roleCode
        log.warn("OSS login: cannot resolve internal role from OSS jobList for jobNumber={}, user={}", jobNumber, fallbackUsername);
        return null;
    }

    /**
     * 将 OSS 权限码列表映射为内部菜单权限码列表。
     */
    public List<String> mapOssPermissionsToInternal(CrmUserPermission permission, String systemName) {
        List<String> ossMenuCodes = permission.getMenusForSystem(systemName);
        if (ossMenuCodes.isEmpty()) {
            log.info("OSS login: no menu codes for system={}", systemName);
            return List.of();
        }
        log.info("OSS login: raw menu codes for user, system={}, codes={}", systemName, ossMenuCodes);
        Set<String> internalPermissions = ossMenuPermissionMapper.mapCodes(ossMenuCodes);
        log.info("OSS login: mapped internal permissions={}", internalPermissions);
        return new ArrayList<>(internalPermissions);
    }
}
