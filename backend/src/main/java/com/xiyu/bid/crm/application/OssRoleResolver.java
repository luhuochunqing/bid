package com.xiyu.bid.crm.application;

import com.fasterxml.jackson.databind.JsonNode;
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
     * OSS sysRoleList 中的 roleCode 可能是 OSS/Home/CRM 等系统的角色码（如 admin、SE、PE），
     * 也可能是 bid 系统的角色码（如 bid-administration、bid-Team）。
     * 通过 {@link JobRoleLookupResolver#mapOssRoleCodeToInternal} 判断 roleCode 是否为
     * bid 系统的已知角色码，命中则直接使用。
     * <p>
     * 解析优先级：
     * <ol>
     *   <li>sysRoleList 中 status=1 的 roleCode（bid-* 角色码优先）</li>
     *   <li>sysRoleList 中 status=1 的 roleName（中文角色名称通过映射表匹配）</li>
     *   <li>jobName（岗位名通过映射表或正则匹配）</li>
     * </ol>
     * 无法解析时返回 null（fail-closed），调用方可通过
     * {@link #resolveRoleCodeFromEmployeeInfo} 做 fallback。
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

        // 1. 从 sysRoleList 解析（优先检查 roleCode，再检查 roleName）
        //    roleCode 更准确：bid-* 前缀的角色码可直接映射为内部角色码（如 bid-administration）
        //    roleName 是中文角色名称（如"投标-行政专员"），需通过映射表匹配
        if (jobInfo.getSysRoleList() != null) {
            for (CrmJobListResponse.SysRole sysRole : jobInfo.getSysRoleList()) {
                if ("1".equals(sysRole.getStatus()) && !Boolean.TRUE.equals(sysRole.getDel())) {
                    // 1a. 优先检查 roleCode（bid-* 角色码可直接映射）
                    String ossRoleCode = sysRole.getRoleCode();
                    if (ossRoleCode != null && !ossRoleCode.isBlank()) {
                        String internalCode = JobRoleLookupResolver.mapOssRoleCodeToInternal(ossRoleCode);
                        if (internalCode != null && !internalCode.isBlank()) {
                            log.info("OSS login: role resolved from sysRoleList roleCode: {} -> {}", ossRoleCode, internalCode);
                            return internalCode;
                        }
                    }
                    // 1b. 再检查 roleName（中文角色名称通过映射表匹配）
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
     * 从 getUserInfo 返回的 employeeInfo 中的 roleList 解析 bid 内部角色码。
     * <p>
     * OSS getUserInfo 返回的 roleList 包含用户在 OSS 平台所有系统中的角色，
     * 每个角色有 roleName 和 roleCode。其中 roleCode 以 "bid-" 开头的是 bid 系统
     * 的角色码（如 bid-administration），可直接映射为内部角色码。
     * <p>
     * 此方法作为 {@link #resolveRoleCodeFromJobList} 的 fallback，当 jobList
     * 解析失败时调用。确保 OSS 端已分配 bid 系统角色的用户不会因 jobList
     * 的 sysRoleList 缺失 roleCode 而被误拒。
     *
     * @param employeeInfo getUserInfo 返回的 data 字段（JsonNode）
     * @param username 用户名（用于日志）
     * @return 解析到的内部角色码；无法解析时返回 null
     */
    public String resolveRoleCodeFromEmployeeInfo(JsonNode employeeInfo, String username) {
        if (employeeInfo == null || employeeInfo.isMissingNode() || employeeInfo.isNull()) {
            return null;
        }
        JsonNode roleList = employeeInfo.path("roleList");
        if (roleList == null || !roleList.isArray() || roleList.size() == 0) {
            log.info("OSS login: no roleList in employeeInfo for user={}", username);
            return null;
        }
        for (JsonNode role : roleList) {
            String status = role.path("status").asText(null);
            boolean del = role.path("del").asBoolean(false);
            if (!"1".equals(status) || del) {
                continue;
            }
            String ossRoleCode = role.path("roleCode").asText(null);
            if (ossRoleCode != null && !ossRoleCode.isBlank()) {
                String internalCode = JobRoleLookupResolver.mapOssRoleCodeToInternal(ossRoleCode);
                if (internalCode != null && !internalCode.isBlank()) {
                    log.info("OSS login: role resolved from employeeInfo roleList roleCode: {} -> {}", ossRoleCode, internalCode);
                    return internalCode;
                }
            }
        }
        log.warn("OSS login: cannot resolve internal role from employeeInfo roleList for user={}", username);
        return null;
    }

    // isWhitelistedPerson 方法已删除——白名单已废弃，不再有"白名单用户"的概念。
    // 菜单权限完全来自 OSS 端 getUserPermission 返回，不合并角色标准权限。

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
