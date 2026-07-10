// Input: UserRepository 与用户名查询参数 + 新角色 → 旧角色兼容映射
// Output: Spring Security UserDetails（含新旧角色兼容权限）
// Pos: Auth/用户加载层
// 维护声明: 仅维护用户加载逻辑；权限字段映射变更请同步认证链路.
package com.xiyu.bid.auth;

import com.xiyu.bid.security.domain.LoginRoleWhitelist;
import com.xiyu.bid.crm.application.OssPermissionCache;
import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.exception.RoleNotAuthorizedException;
import com.xiyu.bid.permission.RoleProfileAdminPermissionFilter;
import com.xiyu.bid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
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
        boolean isOssUser = user.isOssUser();

        // 0. 优先从 OSS 权限缓存读取实时抓取的角色+权限（不读本地 DB RoleProfile.menu_permissions）
        RoleSource roleSource = resolveRoleSource(user);
        String roleCode = roleSource.roleCode();
        List<String> menuPermissions = roleSource.menuPermissions();
        boolean skipLegacyCompat = roleSource.skipLegacyCompat();

        if (isOssUser) {
            return ossAuthorities(user, roleSource);
        }

        Set<String> authorities = new LinkedHashSet<>();
        User.Role legacyRole = user.getRole() == null ? User.Role.MANAGER : user.getRole();

        addLegacyRoleAuthority(authorities, legacyRole, skipLegacyCompat);
        addRoleCodeAuthorities(authorities, roleCode, skipLegacyCompat);
        addMenuPermissionAuthorities(authorities, menuPermissions, roleCode, legacyRole, false);
        addCatalogFallbackAuthorities(authorities, roleCode, menuPermissions, false);
        addAdminFallbackAuthorities(authorities, roleCode, legacyRole, false);

        // CO-391 诊断日志：输出最终 roleCode 与 authorities 集合，便于排查 403 鉴权失败。
        // INFO 级别（OSS/登录频次低，不爆量）；覆盖 OSS 缓存命中与 DB 兜底两条路径。
        log.info("UserDetails authorities built: user={} isOssUser=false roleCode={} skipLegacyCompat={} authorities={}",
                user.getUsername(), roleCode, skipLegacyCompat, authorities);

        return authorities.stream().map(SimpleGrantedAuthority::new).toList();
    }

    private List<SimpleGrantedAuthority> ossAuthorities(User user, RoleSource roleSource) {
        String roleCode = roleSource.roleCode();
        List<String> menuPermissions = roleSource.menuPermissions();
        boolean skipLegacyCompat = roleSource.skipLegacyCompat();

        // OSS 同步用户必须有白名单内的有效角色
        if (!LoginRoleWhitelist.isAllowed(roleCode)) {
            log.warn("UserDetails denied for OSS user={}: roleCode={} not allowed", user.getUsername(), roleCode);
            throw new RoleNotAuthorizedException("角色未授权，不允许访问: " + roleCode);
        }

        Set<String> authorities = new LinkedHashSet<>();

        if (!skipLegacyCompat) {
            User.Role legacyRole = user.getRole() == null ? User.Role.MANAGER : user.getRole();
            authorities.add("ROLE_" + legacyRole.name());
        }

        if (roleCode != null && !roleCode.isBlank()) {
            applyRoleCodeAuthorities(authorities, roleCode, skipLegacyCompat);
        }

        if (menuPermissions != null) {
            authorities.addAll(RoleProfileAdminPermissionFilter.filter(menuPermissions));
        }

        log.info("UserDetails authorities built: user={} isOssUser=true roleCode={} skipLegacyCompat={} authorities={}",
                user.getUsername(), roleCode, skipLegacyCompat, authorities);

        return authorities.stream().map(SimpleGrantedAuthority::new).toList();
    }

    private RoleSource resolveRoleSource(User user) {
        Optional<OssPermissionCache.CacheEntry> ossEntry = ossPermissionCache.getEntry(user.getUsername());
        boolean isOssUser = user.isOssUser();

        if (ossEntry.isPresent() && ossEntry.get().roleCode() != null) {
            String roleCode = ossEntry.get().roleCode();
            return new RoleSource(roleCode, ossEntry.get().menuPermissions(),
                    RoleProfileCatalog.shouldSkipLegacyRoleCompat(roleCode));
        }

        if (isOssUser) {
            // OSS 用户 cache miss：fail-closed，禁止 DB fallback
            // 原因：OSS 用户的角色+权限必须由 OSS 实时抓取决定，DB 中的 roleProfile 可能过期或被篡改。
            // 若允许 DB fallback，OSS 用户可能拿到 DB 中的 /bidAdmin 等高权限，违反权限最小化原则。
            // 使用 BadCredentialsException（而非 UsernameNotFoundException）：用户存在但认证凭证/权限状态
            // 无效，语义准确；避免 Sentry 将合法 OSS 用户误判为"用户不存在"噪声。
            log.warn("UserDetails denied for OSS user={} (cache miss): fail-closed, no DB fallback",
                    user.getUsername());
            throw new BadCredentialsException(
                    "OSS 用户缓存未命中，禁止 DB 兜底: " + user.getUsername());
        }

        // SAFE: 本地系统账号（admin 等）在 OSS 缓存未命中时登录。此场景下 OSS 缓存没有，
        // 必须使用本地 DB roleCode 才能让管理员登录。上方分支已显式拒绝 OSS 用户的 DB 兜底，
        // 此分支只对 admin 本地账号生效（与 DataScopeConfigService.isLocalSystemAccount 一致）。
        // 本地账号由用户表 unique key + 密码哈希独立验证，不会触发 CO-373 的 OSS fallback 问题。
        String roleCode = user.getRoleCode();
        List<String> menuPermissions = user.getRoleProfile() != null ? user.getRoleProfile().getMenuPermissions() : null;
        return new RoleSource(roleCode, menuPermissions, RoleProfileCatalog.shouldSkipLegacyRoleCompat(roleCode));
    }

    private void addLegacyRoleAuthority(Set<String> authorities, User.Role legacyRole, boolean skipLegacyCompat) {
        if (!skipLegacyCompat) {
            authorities.add("ROLE_" + legacyRole.name());
        }
    }

    private void addRoleCodeAuthorities(Set<String> authorities, String roleCode, boolean skipLegacyCompat) {
        applyRoleCodeAuthorities(authorities, roleCode, skipLegacyCompat);
    }

    /**
     * OSS 路径与本地路径共用的 roleCode → authorities 转换原语。
     * <p>仅使用 RoleProfileCatalog 的纯函数转换方法（toAuthorityName/legacyRoleForCode），
     * 不调用 seedDefinitions()/definitionForCode() 等可能触发权限扩散的方法。
     * OSS 路径与本地路径共用此方法，避免逻辑重复导致后续修改遗漏。
     */
    private static void applyRoleCodeAuthorities(Set<String> authorities,
                                                  String roleCode, boolean skipLegacyCompat) {
        if (roleCode == null || roleCode.isBlank()) {
            return;
        }
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

    private void addMenuPermissionAuthorities(Set<String> authorities, List<String> menuPermissions,
                                              String roleCode, User.Role legacyRole, boolean isOssUser) {
        if (menuPermissions == null) {
            return;
        }
        // specs/032: OSS 用户过滤 "all"（内部 admin 专属权限键，OSS 用户不应持有）
        if (isOssUser) {
            authorities.addAll(RoleProfileAdminPermissionFilter.filter(menuPermissions));
        } else {
            authorities.addAll(menuPermissions);
        }

        // Admin role or having "all" permission gets all known permissions dynamically
        // specs/032: OSS 用户严格按 OSS 返回的菜单权限鉴权，不因 roleCode=admin 触发扩散
        if (!isOssUser && (menuPermissions.contains("all") || "admin".equalsIgnoreCase(roleCode) || User.Role.ADMIN == legacyRole)) {
            for (RoleProfileCatalog.SeedDefinition def : RoleProfileCatalog.seedDefinitions()) {
                if (def.menuPermissions() != null) {
                    authorities.addAll(def.menuPermissions());
                }
            }
            authorities.add(RoleProfileCatalog.WAREHOUSE_MANAGE_PERMISSION);
        }
    }

    private void addCatalogFallbackAuthorities(Set<String> authorities, String roleCode,
                                               List<String> menuPermissions, boolean isOssUser) {
        // catalog 基线权限（仅对本地系统账号）
        // 本地 DB 显式 menu_permissions 仍保持权威，仅为空时才 fallback 到 catalog。
        // OSS 用户权限唯一来源是 OSS 菜单映射，不合并 catalog，避免权限扩散。
        if (isOssUser || roleCode == null || roleCode.isBlank() || !RoleProfileCatalog.isRegisteredCode(roleCode)
                || (menuPermissions != null && !menuPermissions.isEmpty())) {
            return;
        }
        RoleProfileCatalog.SeedDefinition catalogDef = RoleProfileCatalog.definitionForCode(roleCode);
        if (catalogDef != null && catalogDef.menuPermissions() != null) {
            authorities.addAll(catalogDef.menuPermissions());
        }
    }

    private void addAdminFallbackAuthorities(Set<String> authorities, String roleCode,
                                             User.Role legacyRole, boolean isOssUser) {
        // Fallback for Admin legacy role
        // specs/032 + CO-551 修订：OSS 用户的 system.admin/warehouse.manage 由 OSS 菜单映射决定，
        // 此处仅给本地 admin 补发；OSS 用户不走 fallback（其权限来自 OSS 菜单映射）。
        if (isOssUser || (User.Role.ADMIN != legacyRole && !"admin".equalsIgnoreCase(roleCode))) {
            return;
        }
        authorities.add(RoleProfileCatalog.WAREHOUSE_MANAGE_PERMISSION);
        // system.admin：本地 admin fallback 补发；OSS 用户可由 OSS 菜单 1010 映射获得（CO-551）
        authorities.add(RoleProfileCatalog.SYSTEM_ADMIN_PERMISSION);
    }

    private record RoleSource(String roleCode, List<String> menuPermissions, boolean skipLegacyCompat) {
    }
}
