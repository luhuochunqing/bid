package com.xiyu.bid.integration.organization.domain.policy;

import com.xiyu.bid.integration.organization.infrastructure.mapper.PositionToRoleMapper;

import java.util.List;
import java.util.Locale;

/**
 * 将 OSS 系统角色列表（sysRoleList）映射为内部角色码。
 * <p>
 * 复用岗位映射规则（position-to-role-mappings），对列表中每个角色名进行大小写不敏感的正则匹配，
 * 返回第一个命中的内部角色码。无匹配时返回 null。
 */
public class SystemRoleListMapper {

    private final PositionToRoleMapper positionToRoleMapper;

    public SystemRoleListMapper(PositionToRoleMapper positionToRoleMapper) {
        this.positionToRoleMapper = positionToRoleMapper;
    }

    /**
     * 映射系统角色列表到内部角色码。
     *
     * @param sysRoleList OSS 系统角色名称列表，可能为 null 或空
     * @return 第一个命中的内部角色码，无匹配返回 null
     */
    public String map(List<String> sysRoleList) {
        if (sysRoleList == null || sysRoleList.isEmpty()) {
            return null;
        }
        for (String roleName : sysRoleList) {
            if (roleName == null || roleName.isBlank()) {
                continue;
            }
            String roleCode = positionToRoleMapper.map(roleName.trim());
            if (roleCode != null && !roleCode.isBlank()) {
                // 保留 roleCode 原始大小写：OSS 角色码大小写敏感（如 bidAdmin、bid-TeamLeader）
                return roleCode;
            }
        }
        return null;
    }
}
