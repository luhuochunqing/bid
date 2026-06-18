package com.xiyu.bid.integration.organization.domain.policy;

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
 * 所有文本比较大小写不敏感，返回的角色码已转换为小写并经过 OSS 角色码标准化映射。
 */
public class JobRoleLookupResolver {

    /** OSS 侧历史角色码到内部角色码的映射（配置中大小写可能不一致，查找时忽略大小写） */
    private static final Map<String, String> OSS_TO_INTERNAL_ROLE = Map.of(
            "/bidAdmin", "bid_admin",
            "bid-TeamLeader", "bid_lead",
            "bid-SystemAdmin", "admin",
            "bid-Team", "bid_specialist",
            "bid-projectLeader", "sales",
            "bid-administration", "admin_staff",
            "bid-otherDept", "bid_other_dept"
    );
    private static final Map<String, String> OSS_TO_INTERNAL_ROLE_IGNORE_CASE = OSS_TO_INTERNAL_ROLE.entrySet().stream()
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
        String roleCode = positionToRoleMapper.map(snapshot.externalRoleCode());
        if (roleCode != null && !roleCode.isBlank()) {
            return roleCode;
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

    private String normalizeRoleCode(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return null;
        }
        String trimmed = roleCode.trim().toLowerCase(Locale.ROOT);
        return OSS_TO_INTERNAL_ROLE_IGNORE_CASE.getOrDefault(trimmed, trimmed);
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
