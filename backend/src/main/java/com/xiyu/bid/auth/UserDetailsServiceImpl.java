// Input: UserRepository 与用户名查询参数 + 新角色 → 旧角色兼容映射
// Output: Spring Security UserDetails（含新旧角色兼容权限）
// Pos: Auth/用户加载层
// 维护声明: 仅维护用户加载逻辑；权限字段映射变更请同步认证链路.
package com.xiyu.bid.auth;

import com.xiyu.bid.security.domain.LoginRoleWhitelist;
import com.xiyu.bid.crm.application.OssPermissionCache;
import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;
    private final OssPermissionCache ossPermissionCache;



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

        // 0. 优先从 OSS 权限缓存读取实时抓取的角色+权限（不读本地 DB RoleProfile.menu_permissions）
        Optional<OssPermissionCache.CacheEntry> ossEntry = ossPermissionCache.getEntry(user.getUsername());
        boolean isOssUser = user.getExternalOrgSourceApp() != null && !user.getExternalOrgSourceApp().isBlank();

        String roleCode;
        List<String> menuPermissions;
        boolean skipLegacyCompat;
        boolean usingOssCachedPermissions = false;

        if (ossEntry.isPresent() && ossEntry.get().roleCode() != null) {
            // OSS 用户：用缓存中的实时角色+权限（来自 5 接口实时抓取）
            roleCode = ossEntry.get().roleCode();
            menuPermissions = ossEntry.get().menuPermissions();
            skipLegacyCompat = RoleProfileCatalog.shouldSkipLegacyRoleCompat(roleCode);
            usingOssCachedPermissions = true;
        } else if (isOssUser) {
            // OSS 用户 cache miss：fail-closed，禁止 DB fallback
            // 原因：OSS 用户的角色+权限必须由 OSS 实时抓取决定，DB 中的 roleProfile 可能过期或被篡改。
            // 若允许 DB fallback，OSS 用户可能拿到 DB 中的 /bidAdmin 等高权限，违反权限最小化原则。
            log.warn("UserDetails denied for OSS user={} (cache miss): fail-closed, no DB fallback",
                    user.getUsername());
            throw new UsernameNotFoundException(
                    "OSS 用户缓存未命中，禁止 DB 兜底: " + user.getUsername());
        } else {
            // SAFE: 本地系统账号（admin 等）在 OSS 缓存未命中时登录。此场景下 OSS 缓存没有，
            // 必须使用本地 DB roleCode 才能让管理员登录。上方分支已显式拒绝 OSS 用户的 DB 兜底，
            // 此分支只对 admin 本地账号生效（与 DataScopeConfigService.isLocalSystemAccount 一致）。
            // 本地账号由用户表 unique key + 密码哈希独立验证，不会触发 CO-373 的 OSS fallback 问题。
            roleCode = user.getRoleCode();
            menuPermissions = user.getRoleProfile() != null ? user.getRoleProfile().getMenuPermissions() : null;
            skipLegacyCompat = RoleProfileCatalog.shouldSkipLegacyRoleCompat(roleCode);
        }

        // OSS 同步用户必须有白名单内的有效角色；本地账号保持兼容兜底。
        if (isOssUser && !LoginRoleWhitelist.isAllowed(roleCode)) {
            log.warn("UserDetails denied for OSS user={}: roleCode={} not allowed", user.getUsername(), roleCode);
            throw new org.springframework.security.core.AuthenticationException("角色未授权，不允许访问") {};
        }

        // 1. Legacy role (e.g., ROLE_ADMIN, ROLE_MANAGER)
        User.Role legacyRole = user.getRole() == null ? User.Role.MANAGER : user.getRole();
        if (!skipLegacyCompat) {
            authorities.add("ROLE_" + legacyRole.name());
        }

        // 2. Role code as authority (e.g., bidAdmin) + 新旧兼容映射
        if (roleCode != null && !roleCode.isBlank()) {
            authorities.add(roleCode);
            // Spring Security authority 生成规则：连字符转下划线再大写
            // bid-TeamLeader → ROLE_BID_TEAMLEADER，bidAdmin → ROLE_BIDADMIN
            // 使用 RoleProfileCatalog.toAuthorityName 统一转换，避免各处手动 replace
            String authorityName = RoleProfileCatalog.toAuthorityName(roleCode);
            if (authorityName != null) {
                authorities.add("ROLE_" + authorityName);
            }
            // 新角色 (roleCode) → 旧角色 (User.Role) 兼容层代理：
            User.Role compatLegacy = RoleProfileCatalog.legacyRoleForCode(roleCode);
            if (compatLegacy != null && !skipLegacyCompat) {
                authorities.add("ROLE_" + compatLegacy.name());
            }
        }

        // 3. Menu permissions — 优先用 OSS 缓存的实时权限，缓存未命中时用 DB 兜底
        if (menuPermissions != null) {
            authorities.addAll(menuPermissions);

            // Admin role or having "all" permission gets all known permissions dynamically
            if (menuPermissions.contains("all") || "admin".equalsIgnoreCase(roleCode) || User.Role.ADMIN == legacyRole) {
                for (RoleProfileCatalog.SeedDefinition def : RoleProfileCatalog.seedDefinitions()) {
                    if (def.menuPermissions() != null) {
                        authorities.addAll(def.menuPermissions());
                    }
                }
                authorities.add(RoleProfileCatalog.WAREHOUSE_MANAGE_PERMISSION);
            }
        }

        // 4. catalog 基线权限（仅对已注册标准角色）
        //    OSS 缓存权限只表达菜单可见性，需补充 catalog 中的细粒度业务权限；
        //    本地 DB 显式 menu_permissions 仍保持权威，仅为空时才 fallback 到 catalog。
        if (roleCode != null && !roleCode.isBlank() && RoleProfileCatalog.isRegisteredCode(roleCode)
                && (usingOssCachedPermissions || menuPermissions == null || menuPermissions.isEmpty())) {
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
