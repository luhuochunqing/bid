package com.xiyu.bid.crm.application;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * OSS 用户权限响应。
 * <p>
 * 对应 GET /oauth/getUserPermission 的 data 字段。
 * <p>
 * 格式示例：
 * <pre>
 * {
 *   "OPC": ["sale_log", "sale_order"],
 *   "SMS": ["sys_org", "sys_user"]
 * }
 * </pre>
 */
public record CrmUserPermission(
        Map<String, List<String>> systemPermissions
) {
    public CrmUserPermission {
        if (systemPermissions == null) {
            systemPermissions = Collections.emptyMap();
        }
    }

    public boolean hasPermission(String systemName, String menuKey) {
        List<String> menus = systemPermissions.get(systemName);
        if (menus == null) {
            return false;
        }
        return menus.contains(menuKey);
    }

    public List<String> getMenusForSystem(String systemName) {
        List<String> menus = systemPermissions.get(systemName);
        return menus == null ? Collections.emptyList() : Collections.unmodifiableList(menus);
    }

    public boolean isEmpty() {
        return systemPermissions.isEmpty()
                || systemPermissions.values().stream().allMatch(list -> list == null || list.isEmpty());
    }
}
