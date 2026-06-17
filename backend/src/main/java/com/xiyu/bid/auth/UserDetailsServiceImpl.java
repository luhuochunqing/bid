// Input: UserRepository 与用户名查询参数 + 新角色 → 旧角色兼容映射
// Output: Spring Security UserDetails（含新旧角色兼容权限）
// Pos: Auth/用户加载层
// 维护声明: 仅维护用户加载逻辑；权限字段映射变更请同步认证链路.
package com.xiyu.bid.auth;

import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;



    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .authorities(authoritiesFor(user))
                .disabled(!user.getEnabled())
                .build();
    }

    private List<SimpleGrantedAuthority> authoritiesFor(User user) {
        Set<String> authorities = new LinkedHashSet<>();
        
        // 0. roleCode 提前，判定是否跳过 Legacy User.Role 兼容（bid_other_dept 等新式受限角色
        //    及任何未注册角色不发 ROLE_STAFF/ADMIN/MANAGER，避免误入 hasAnyRole(... 'STAFF' ...) 白名单）。
        String roleCode = user.getRoleCode();
        boolean skipLegacyCompat = RoleProfileCatalog.shouldSkipLegacyRoleCompat(roleCode);

        // 1. Legacy role (e.g., ROLE_STAFF, ROLE_ADMIN, ROLE_MANAGER)
        User.Role legacyRole = user.getRole() == null ? User.Role.STAFF : user.getRole();
        if (!skipLegacyCompat) {
            authorities.add("ROLE_" + legacyRole.name());
        }
        
        // 2. Role code as authority (e.g., bid_admin, staff) + 新旧兼容映射
        if (roleCode != null && !roleCode.isBlank()) {
            authorities.add(roleCode);
            authorities.add("ROLE_" + roleCode.toUpperCase(Locale.ROOT));
            // 新角色 (roleCode) → 旧角色 (User.Role) 兼容层代理：
            User.Role compatLegacy = RoleProfileCatalog.securityCompatLegacyRole(roleCode);
            if (compatLegacy != null && !skipLegacyCompat) {
                authorities.add("ROLE_" + compatLegacy.name());
            }
        }
        
        // 3. Auditor Role check (kept for compatibility)
        
        // 4. Menu permissions — merge DB-stored permissions with catalog (catalog is authoritative)
        if (user.getRoleProfile() != null) {
            List<String> permissions = user.getRoleProfile().getMenuPermissions();
            if (permissions != null) {
                authorities.addAll(permissions);

                // Admin role or having "all" permission gets all known permissions dynamically
                if (permissions.contains("all") || "admin".equalsIgnoreCase(roleCode) || User.Role.ADMIN == legacyRole) {
                    for (RoleProfileCatalog.SeedDefinition def : RoleProfileCatalog.seedDefinitions()) {
                        if (def.menuPermissions() != null) {
                            authorities.addAll(def.menuPermissions());
                        }
                    }
                    authorities.add(RoleProfileCatalog.WAREHOUSE_MANAGE_PERMISSION);
                }
            }
        }

        // 4b. Always merge catalog-defined permissions for the user's roleCode,
        //     ensuring newly added permissions (e.g. retrospective.submit) are
        //     granted even when the DB-stored permission list is stale.
        //     仅对已注册角色生效——未注册 roleCode 的 definitionForCode 会 fallback 到 staff，
        //     错误授予标讯/项目/知识库权限，故用 isRegisteredCode 拦截。
        if (roleCode != null && !roleCode.isBlank() && RoleProfileCatalog.isRegisteredCode(roleCode)
                && (user.getRoleProfile() == null || user.getRoleProfile().getMenuPermissions().isEmpty())) {
            RoleProfileCatalog.SeedDefinition catalogDef = RoleProfileCatalog.definitionForCode(roleCode);
            if (catalogDef != null && catalogDef.menuPermissions() != null) {
                authorities.addAll(catalogDef.menuPermissions());
            }
        }

        // Fallback for Admin legacy role
        if (User.Role.ADMIN == legacyRole || "admin".equalsIgnoreCase(roleCode)) {
            authorities.add(RoleProfileCatalog.WAREHOUSE_MANAGE_PERMISSION);
        }
        
        return authorities.stream().map(SimpleGrantedAuthority::new).toList();
    }
}
