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

    private static final Map<String, User.Role> ROLE_CODE_TO_LEGACY = Map.ofEntries(
            Map.entry("admin", User.Role.ADMIN),
            Map.entry("bid_admin", User.Role.MANAGER),
            Map.entry("bid_lead", User.Role.MANAGER),
            Map.entry("sales", User.Role.MANAGER),
            Map.entry("bid_specialist", User.Role.STAFF),
            Map.entry("admin_staff", User.Role.STAFF),
            Map.entry("task_executor", User.Role.STAFF)
    );

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
        
        // 1. Legacy role (e.g., ROLE_STAFF, ROLE_ADMIN, ROLE_MANAGER)
        User.Role legacyRole = user.getRole() == null ? User.Role.STAFF : user.getRole();
        authorities.add("ROLE_" + legacyRole.name());
        
        // 2. Role code as authority (e.g., bid_admin, staff) + 新旧兼容映射
        String roleCode = user.getRoleCode();
        if (roleCode != null && !roleCode.isBlank()) {
            authorities.add(roleCode);
            authorities.add("ROLE_" + roleCode.toUpperCase(Locale.ROOT));
            // 新角色 (roleCode) → 旧角色 (User.Role) 兼容：
            // bid_admin/bid_lead/sales 也获得 ROLE_MANAGER 权限
            User.Role compatLegacy = ROLE_CODE_TO_LEGACY.get(roleCode.trim().toLowerCase(Locale.ROOT));
            if (compatLegacy != null) {
                authorities.add("ROLE_" + compatLegacy.name());
            }
        }
        
        // 3. Auditor Role check (kept for compatibility)
        if (RoleProfileCatalog.AUDITOR_CODE.equalsIgnoreCase(roleCode)) {
            authorities.add("ROLE_" + RoleProfileCatalog.AUDITOR_CODE.toUpperCase(Locale.ROOT));
        }
        
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
        if (roleCode != null && !roleCode.isBlank()) {
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
