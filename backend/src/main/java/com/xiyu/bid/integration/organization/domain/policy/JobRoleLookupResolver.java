package com.xiyu.bid.integration.organization.domain.policy;

import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.integration.organization.application.OrganizationIntegrationProperties;
import com.xiyu.bid.integration.organization.domain.OrganizationUserSnapshot;
import com.xiyu.bid.integration.organization.dto.OssUserJobAndRoleDto;
import com.xiyu.bid.integration.organization.infrastructure.mapper.PositionToRoleMapper;

import java.util.Locale;
import java.util.Map;

/**
 * 按 Constitution V 优先级解析用户内部角色码。
 * <p>
 * 优先级：人员级规则 > 部门级规则 > 岗位级规则 > 系统角色列表（sysRoleList）。
 * 所有文本比较大小写不敏感，返回的角色码已经过 OSS 角色码标准化映射。
 * <p>
 * OSS code 与内部 code 已对齐（如 bid-TeamLeader、bid-Team、bid-projectLeader 等），
 * 不再需要 OSS_TO_INTERNAL_ROLE 映射表。唯一例外：bid-SystemAdmin → admin
 * （投标系统管理员对应系统默认 admin）。
 */
public class JobRoleLookupResolver {

    /**
     * OSS sysRoleList 中 roleName（中文角色名称）到内部角色码的映射。
     * <p>
     * OSS 接口返回的 sysRoleList 只包含 roleName（如"投标项目负责人"），不包含 roleCode（如 bid-projectLeader）。
     * 此映射用于将中文角色名称直接映射为内部角色码。
     * 角色码引用 {@link RoleProfileCatalog} 常量，避免硬编码。
     */
    private static final Map<String, String> OSS_ROLE_NAME_TO_INTERNAL = Map.of(
            "投标管理员", RoleProfileCatalog.BID_ADMIN_CODE,
            "投标组长", RoleProfileCatalog.BID_LEAD_CODE,
            "投标系统管理员", RoleProfileCatalog.ADMIN_CODE,
            "投标专员", RoleProfileCatalog.BID_SPECIALIST_CODE,
            "投标项目负责人", RoleProfileCatalog.SALES_CODE,
            "行政人员", RoleProfileCatalog.ADMIN_STAFF_CODE,
            "跨部门协同人员", RoleProfileCatalog.BID_OTHER_DEPT_CODE
    );
    private static final Map<String, String> OSS_ROLE_NAME_TO_INTERNAL_IGNORE_CASE = OSS_ROLE_NAME_TO_INTERNAL.entrySet().stream()
            .collect(java.util.stream.Collectors.toUnmodifiableMap(
                    e -> e.getKey().toLowerCase(Locale.ROOT),
                    Map.Entry::getValue));

    private final OrganizationIntegrationProperties properties;
    private final PositionToRoleMapper positionToRoleMapper;
    private final SystemRoleListMapper systemRoleListMapper;

    public JobRoleLookupResolver(
            OrganizationIntegrationProperties properties,
            PositionToRoleMapper positionToRoleMapper,
            SystemRoleListMapper systemRoleListMapper) {
        this.properties = properties;
        this.positionToRoleMapper = positionToRoleMapper;
        this.systemRoleListMapper = systemRoleListMapper;
    }

    /**
     * 解析用户角色。
     *
     * @param snapshot   用户快照
     * @param lookupMap  批量岗位/角色回查结果，可能为空
     * @return 解析结果，包含角色码、来源与命中文本
     */
    public ResolvedRole resolve(OrganizationUserSnapshot snapshot, Map<String, OssUserJobAndRoleDto> lookupMap) {
        // 1. 人员级规则
        String personRoleCode = mapPersonToRole(snapshot);
        if (personRoleCode != null && !personRoleCode.isBlank()) {
            return new ResolvedRole(normalizeRoleCode(personRoleCode), RoleMappingSource.PERSON, snapshot.email());
        }

        // 2. 部门级规则
        String departmentRoleCode = mapDepartmentToRole(snapshot);
        if (departmentRoleCode != null && !departmentRoleCode.isBlank()) {
            return new ResolvedRole(normalizeRoleCode(departmentRoleCode), RoleMappingSource.DEPARTMENT, snapshot.departmentName());
        }

        // 3. 岗位级规则
        String jobRoleCode = mapJobToRole(snapshot, lookupMap);
        if (jobRoleCode != null && !jobRoleCode.isBlank()) {
            return new ResolvedRole(normalizeRoleCode(jobRoleCode), RoleMappingSource.JOB, resolveJobText(snapshot, lookupMap));
        }

        // 4. 系统角色列表
        String sysRoleCode = mapSysRoleListToRole(snapshot, lookupMap);
        if (sysRoleCode != null && !sysRoleCode.isBlank()) {
            return new ResolvedRole(normalizeRoleCode(sysRoleCode), RoleMappingSource.SYS_ROLE_LIST, "sysRoleList");
        }

        return new ResolvedRole(null, RoleMappingSource.NONE, null);
    }

    private String mapPersonToRole(OrganizationUserSnapshot snapshot) {
        String email = snapshot.email();
        String externalUserId = snapshot.externalUserId();
        String username = snapshot.username();
        String fullName = snapshot.fullName();
        for (OrganizationIntegrationProperties.PersonToRoleMapping mapping : properties.getPersonToRoleMappings()) {
            if ((email != null && mapping.matches(email))
                    || (externalUserId != null && mapping.matches(externalUserId))
                    || (username != null && mapping.matches(username))
                    || (fullName != null && mapping.matches(fullName))) {
                return mapping.getRoleCode();
            }
        }
        return null;
    }

    private String mapDepartmentToRole(OrganizationUserSnapshot snapshot) {
        String deptName = snapshot.departmentName();
        if (deptName == null || deptName.isBlank()) {
            return null;
        }
        for (OrganizationIntegrationProperties.DepartmentToRoleMapping mapping : properties.getDepartmentToRoleMappings()) {
            if (mapping.matches(deptName)) {
                return mapping.getRoleCode();
            }
        }
        return null;
    }

    private String mapJobToRole(OrganizationUserSnapshot snapshot, Map<String, OssUserJobAndRoleDto> lookupMap) {
        // 优先使用快照中的岗位码/岗位名
        String externalRoleCode = snapshot.externalRoleCode();
        if (externalRoleCode != null && !externalRoleCode.isBlank()) {
            // 先尝试 OSS 角色码硬编码映射（如 bid-Team -> bid_specialist）
            String ossMappedRoleCode = mapOssRoleCodeToInternal(externalRoleCode);
            if (ossMappedRoleCode != null && !ossMappedRoleCode.isBlank()) {
                return ossMappedRoleCode;
            }
            // 再用配置的正则映射
            String roleCode = positionToRoleMapper.map(externalRoleCode);
            if (roleCode != null && !roleCode.isBlank()) {
                return roleCode;
            }
        }

        // 从批量回查结果取岗位名再映射
        OssUserJobAndRoleDto lookup = lookupMap == null ? null : lookupMap.get(snapshot.username());
        if (lookup != null && lookup.jobName() != null && !lookup.jobName().isBlank()) {
            return positionToRoleMapper.map(lookup.jobName());
        }
        return null;
    }

    private String resolveJobText(OrganizationUserSnapshot snapshot, Map<String, OssUserJobAndRoleDto> lookupMap) {
        if (snapshot.externalRoleCode() != null && !snapshot.externalRoleCode().isBlank()) {
            return snapshot.externalRoleCode();
        }
        OssUserJobAndRoleDto lookup = lookupMap == null ? null : lookupMap.get(snapshot.username());
        return lookup == null ? null : lookup.jobName();
    }

    private String mapSysRoleListToRole(OrganizationUserSnapshot snapshot, Map<String, OssUserJobAndRoleDto> lookupMap) {
        OssUserJobAndRoleDto lookup = lookupMap == null ? null : lookupMap.get(snapshot.username());
        if (lookup == null) {
            return null;
        }
        return systemRoleListMapper.map(lookup.sysRoleList());
    }

    /**
     * 将 OSS 角色码映射为内部规范角色码（来自 {@link RoleProfileCatalog#DEFINITIONS}）。
     * <p>
     * 处理 OSS 输入变体：
     * <ul>
     *   <li>大小写不一致（如 {@code /BidAdmin}、{@code /BIDADMIN}）— 归一化为规范码 {@code /bidAdmin}</li>
     *   <li>特殊映射：{@code bid-SystemAdmin} → {@code admin}（投标系统管理员对应系统默认 admin）</li>
     * </ul>
     * 返回的总是 {@link RoleProfileCatalog} 中注册的规范码（如 {@code /bidAdmin}），
     * 而非原始输入，避免大小写不一致导致后续权限匹配失败。
     * <p>
     * 注意：OSS 投标管理员角色码本身带前导斜杠（{@code /bidAdmin}），这是 OSS 规范，
     * 不要去除斜杠。其他角色码（如 {@code bid-TeamLeader}）不带斜杠。
     * <p>
     * 未命中（null/空白/未注册）返回 null，以便调用方继续尝试 positionToRoleMapper 等后续映射。
     */
    public static String mapOssRoleCodeToInternal(String ossRoleCode) {
        if (ossRoleCode == null || ossRoleCode.isBlank()) {
            return null;
        }
        String trimmed = ossRoleCode.trim();
        // 特殊映射：bid-SystemAdmin → admin（投标系统管理员对应系统默认 admin）
        if (trimmed.equalsIgnoreCase("bid-SystemAdmin")) {
            return RoleProfileCatalog.ADMIN_CODE;
        }
        // 通过 case-insensitive 查找返回规范码（而非原始输入）
        // 例如输入 "/BidAdmin" 或 "/BIDADMIN" → 返回规范码 "/bidAdmin"
        return RoleProfileCatalog.canonicalCode(trimmed);
    }

    /**
     * 将 OSS sysRoleList 中的 roleName（中文角色名称）映射为内部角色码。
     * <p>
     * OSS 接口返回的 sysRoleList 只包含 roleName（如"投标项目负责人"），不包含 roleCode。
     * 此方法用于在 {@link #mapOssRoleCodeToInternal} 未命中时，尝试用中文角色名称匹配。
     * 未命中的角色名称返回 null。
     */
    public static String mapOssRoleNameToInternal(String ossRoleName) {
        if (ossRoleName == null || ossRoleName.isBlank()) {
            return null;
        }
        String trimmed = ossRoleName.trim().toLowerCase(Locale.ROOT);
        return OSS_ROLE_NAME_TO_INTERNAL_IGNORE_CASE.get(trimmed);
    }

    /**
     * 综合映射：先尝试 OSS 角色码映射，再尝试中文角色名称映射。
     * <p>
     * 用于 sysRoleList.roleName 或 jobName 等不确定是角色码还是角色名称的场景。
     *
     * @param text 待映射的文本（可能是 OSS 角色码或中文角色名称）
     * @return 内部角色码，未命中返回 null
     */
    public static String mapOssRoleTextToInternal(String text) {
        String roleCode = mapOssRoleCodeToInternal(text);
        if (roleCode != null) {
            return roleCode;
        }
        return mapOssRoleNameToInternal(text);
    }

    private String normalizeRoleCode(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return null;
        }
        return roleCode.trim();
    }

    public record ResolvedRole(String roleCode, RoleMappingSource source, String matchedText) {
    }

    public enum RoleMappingSource {
        PERSON,
        DEPARTMENT,
        JOB,
        SYS_ROLE_LIST,
        NONE
    }
}
