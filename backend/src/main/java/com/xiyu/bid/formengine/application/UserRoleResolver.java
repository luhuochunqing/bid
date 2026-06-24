// Input: username
// Output: 用户角色集合
// Pos: Application 层（临时实现，最终应从 JWT 或 UserService 获取）
// 维护声明: 临时实现，M3+ 应替换为从 SecurityContext 或用户服务获取真实角色.
package com.xiyu.bid.formengine.application;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 用户角色解析器。
 * <p>
 * 临时实现：根据用户名模式推断角色。
 * 最终应从 JWT 声明或 UserService 获取真实角色映射。
 */
@Component
public class UserRoleResolver {

    private static final List<String> ADMIN_ROLES = List.of("admin", "bidAdmin", "ADMIN", "BIDADMIN");
    private static final List<String> MANAGER_ROLES = List.of("bid-TeamLeader", "bid-projectLeader", "MANAGER", "BID_TEAMLEADER", "BID_PROJECTLEADER");

    /**
     * 根据用户名解析用户角色集合。
     *
     * @param username 登录用户名
     * @return 角色集合
     */
    public Set<String> resolveRoles(String username) {
        if (username == null || username.isBlank()) {
            return Set.of();
        }

        Set<String> roles = new HashSet<>();

        // 精确匹配已知的测试账号
        String lower = username.toLowerCase();
        if (ADMIN_ROLES.stream().anyMatch(r -> lower.contains(r.toLowerCase()))) {
            roles.add("admin");
        }
        if (MANAGER_ROLES.stream().anyMatch(r -> lower.contains(r.toLowerCase()))) {
            roles.add("manager");
        }
        // 如果是已知测试账号，添加精确角色
        if (lower.equals("admin") || lower.equals("lizong")) {
            roles.add("admin");
        }

        // 默认角色
        if (roles.isEmpty()) {
            roles.add("bid-Team"); // 默认降级为投标专员
        }

        return roles;
    }
}
