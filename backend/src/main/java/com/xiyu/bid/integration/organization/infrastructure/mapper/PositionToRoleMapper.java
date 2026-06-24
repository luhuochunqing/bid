package com.xiyu.bid.integration.organization.infrastructure.mapper;

import com.xiyu.bid.integration.organization.application.OrganizationIntegrationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class PositionToRoleMapper {

    private final OrganizationIntegrationProperties properties;

    public String map(String positionText) {
        if (positionText == null || positionText.isBlank()) {
            return null;
        }
        for (OrganizationIntegrationProperties.PositionToRoleMapping mapping : properties.getPositionToRoleMappings()) {
            String pattern = mapping.getPositionPattern();
            String roleCode = mapping.getRoleCode();
            if (pattern == null || pattern.isBlank() || roleCode == null || roleCode.isBlank()) {
                continue;
            }
            if (Pattern.compile(pattern).matcher(positionText).find()) {
                // 保留 roleCode 原始大小写：OSS 角色码大小写敏感（如 bidAdmin、bid-TeamLeader）
                // RoleProfileCatalog 用 case-insensitive TreeMap 查找，但 User.roleCode 字段存原值
                return roleCode.trim();
            }
        }
        return null;
    }
}
