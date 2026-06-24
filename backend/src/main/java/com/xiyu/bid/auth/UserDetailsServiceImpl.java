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

        if (isOssUser) {
            if (ossEntry.isPresent() && ossEntry.get().roleCode() != null) {
                // OSS 用户：用缓存中的实时角色+权限（来自 5 接口实时抓取）
                roleCode = ossEntry.get().roleCode();
                menuPermissions = ossEntry.get().menuPermissions();
                skipLegacyCompat = true; // OSS 用户严格断绝 legacy role 兼容注入，防止角色越权
            } else {
                // OSS 用户缓存未命中/失效：严格执行“缓存/OSS调用失败即无权限”的合规底线，禁止本地 DB 兜底
                log.warn("UserDetails denied for OSS user={}: Cache/OSS call failed or expired", user.getUsername());
                throw new org.springframework.security.core.AuthenticationException("权限验证失败：无法实时获取OSS权限信息，请重新登录") {};
            }
        } else {
            // 非 OSS 用户（本地内置测试账号）：用本地 DB 兜底
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

        // 4. catalog 兜底（仅对已注册角色且 menuPermissions 为空时）
        //    确保 catalog 定义的新增权限（如 retrospective.submit）在权限为空时仍被授予。
        if (roleCode != null && !roleCode.isBlank() && RoleProfileCatalog.isRegisteredCode(roleCode)
                && (menuPermissions == null || menuPermissions.isEmpty())) {
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
