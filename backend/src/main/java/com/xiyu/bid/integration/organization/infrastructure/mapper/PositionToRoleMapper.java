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
                return roleCode.trim().toLowerCase(java.util.Locale.ROOT);
            }
        }
        return null;
    }
}
