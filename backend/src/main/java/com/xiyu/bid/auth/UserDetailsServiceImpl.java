// Input: UserRepository 与用户名查询参数
// Output: Spring Security UserDetails
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
        
        // 1. Legacy role (e.g., ROLE_STAFF, ROLE_ADMIN, ROLE_MANAGER)
        User.Role legacyRole = user.getRole() == null ? User.Role.STAFF : user.getRole();
        authorities.add("ROLE_" + legacyRole.name());
        
        // 2. Role code as authority (e.g., bid_admin, staff)
        String roleCode = user.getRoleCode();
        if (roleCode != null && !roleCode.isBlank()) {
            authorities.add(roleCode);
            authorities.add("ROLE_" + roleCode.toUpperCase(Locale.ROOT));
        }
        
        // 3. Auditor Role check (kept for compatibility)
        if (RoleProfileCatalog.AUDITOR_CODE.equalsIgnoreCase(roleCode)) {
            authorities.add("ROLE_" + RoleProfileCatalog.AUDITOR_CODE.toUpperCase(Locale.ROOT));
        }
        
        // 4. Menu permissions
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
        
        // Fallback for Admin legacy role
        if (User.Role.ADMIN == legacyRole || "admin".equalsIgnoreCase(roleCode)) {
            authorities.add(RoleProfileCatalog.WAREHOUSE_MANAGE_PERMISSION);
        }
        
        return authorities.stream().map(SimpleGrantedAuthority::new).toList();
    }
}
