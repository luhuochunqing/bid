// Input: OSS sysRoleList 字符串或对象数组
// Output: 活动角色的规范 code 或兼容名称文本
// Pos: organization/infrastructure - 批量岗位角色响应的角色子结构映射
package com.xiyu.bid.integration.organization.infrastructure.client;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/** 过滤 OSS 停用/删除角色，并优先保留活动角色的 roleCode。 */
final class OrganizationSystemRoleJsonMapper {

    List<String> activeRoleTexts(JsonNode node, String fieldName) {
        JsonNode roles = node.path(fieldName);
        if (!roles.isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        roles.forEach(role -> addActiveRole(result, role));
        return result;
    }

    private void addActiveRole(List<String> result, JsonNode role) {
        if (role.isTextual() && !role.isNull()) {
            result.add(role.asText());
            return;
        }
        if (!role.isObject() || isInactive(role)) {
            return;
        }
        String roleCode = firstText(role, "roleCode", "code");
        String roleName = firstText(role, "roleName", "name", "role");
        String roleText = roleCode.isBlank() ? roleName : roleCode;
        if (!roleText.isBlank()) {
            result.add(roleText);
        }
    }

    private boolean isInactive(JsonNode role) {
        Integer deleted = firstInt(role, "del", "deleted");
        Integer status = firstInt(role, "status", "roleStatus");
        return deleted != null && deleted == 1 || status != null && status != 1;
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isValueNode() && !value.isNull() && !value.asText().trim().isBlank()) {
                return value.asText().trim();
            }
        }
        return "";
    }

    private Integer firstInt(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isBoolean()) {
                return value.booleanValue() ? 1 : 0;
            }
            if (value.isIntegralNumber()) {
                return value.intValue();
            }
            if (value.isTextual() && !value.asText().trim().isBlank()) {
                try {
                    return Integer.parseInt(value.asText().trim());
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }
}
