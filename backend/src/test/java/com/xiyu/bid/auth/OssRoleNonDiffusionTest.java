// Input: OSS 用户 + OSS 缓存条目
// Output: 断言 OSS 用户 authorities 不发生权限扩散（不含 all、不含 catalog seed 扩散权限）
// Pos: Test/spec 033 FR-B003 — OSS 角色"权限不扩散"专项测试
//
// spec 033 方案 B 立即实施的测试安全网：这些测试在方案 A 落地后仍有效。
// 角色级参数化覆盖由 UserDetailsServiceImplTest#ossUserAuthoritiesMustNotContainExpansionPermissions 哨兵测试承担，
// 本类聚焦 3 个专项场景：admin catalog seed 扩散 / admin fallback 补发 / 缓存权限原样保留。
package com.xiyu.bid.auth;

import com.xiyu.bid.crm.application.OssPermissionCache;
import com.xiyu.bid.entity.RoleProfile;
import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.security.EffectiveRoleResolver;
import com.xiyu.bid.security.domain.EffectiveRoleResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * spec 033 FR-B003：OSS 角色"权限不扩散"专项测试。
 *
 * <p>角色级参数化覆盖（7 个 OSS 角色）由
 * {@link UserDetailsServiceImplTest#ossUserAuthoritiesMustNotContainExpansionPermissions}
 * 哨兵测试承担。本类聚焦 3 个需要特定数据构造的专项场景：
 * <ol>
 *   <li>admin catalog seed 扩散：OSS admin 缓存含 all 时不扩散其他角色权限</li>
 *   <li>admin fallback 补发：OSS admin 不走本地 admin fallback 拿 system.admin/warehouse.manage</li>
 *   <li>缓存权限原样保留：OSS 返回的混合权限原样保留，不丢失也不扩散</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("spec 033 FR-B003 — OSS 角色权限不扩散专项测试")
class OssRoleNonDiffusionTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OssPermissionCache ossPermissionCache;

    @Mock
    private EffectiveRoleResolver effectiveRoleResolver;

    @InjectMocks
    private UserDetailsServiceImpl userDetailsService;

    @BeforeEach
    void setUp() {
        // 模拟 EffectiveRoleResolver 的真实决策逻辑（与 EffectiveRolePolicy.decide 一致，
        // 与 UserDetailsServiceImplTest#setUp 同源）：
        // - OSS 用户缓存 admin：OSS_ADMIN_REJECTED（fail-closed 拒绝）
        // - OSS 用户缓存命中（非 admin）：缓存 roleCode + CACHE_HIT
        // - OSS 用户 cache miss：CACHE_MISS_FAIL_CLOSED
        // - 本地用户：entity roleCode + LOCAL_USER
        lenient().when(effectiveRoleResolver.resolve(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            Optional<OssPermissionCache.CacheEntry> entry = ossPermissionCache.getEntry(u.getUsername());
            boolean cacheHit = entry.isPresent() && entry.get().roleCode() != null
                    && !entry.get().roleCode().isBlank();
            if (cacheHit) {
                String cachedRoleCode = entry.get().roleCode();
                if (u.isOssUser() && "admin".equalsIgnoreCase(cachedRoleCode.trim())) {
                    return new EffectiveRoleResult(null, EffectiveRoleResult.Source.OSS_ADMIN_REJECTED);
                }
                return new EffectiveRoleResult(cachedRoleCode, EffectiveRoleResult.Source.CACHE_HIT);
            }
            if (u.isOssUser()) {
                return new EffectiveRoleResult(null, EffectiveRoleResult.Source.CACHE_MISS_FAIL_CLOSED);
            }
            return new EffectiveRoleResult(u.getRoleCode(), EffectiveRoleResult.Source.LOCAL_USER);
        });
    }

    /**
     * OSS admin 用户即使 OSS 缓存返回 all 权限，也不应触发 catalog seed 扩散。
     *
     * <p>CO-373 + §78 后该行为已升级为 fail-closed 直接拒绝（OSS 的 admin 属于其他系统，
     * 本系统不接受），拒绝本身即天然保证不扩散。
     */
    @Test
    @DisplayName("OSS admin 用户缓存含 all 时 fail-closed 拒绝，不扩散 catalog seed 权限")
    void ossAdminWithAllShouldNotExpandCatalogSeed() {
        User user = ossUser("oss_admin_all", RoleProfileCatalog.ADMIN_CODE);
        when(userRepository.findByUsername("oss_admin_all")).thenReturn(Optional.of(user));
        OssPermissionCache.CacheEntry entry = new OssPermissionCache.CacheEntry(
                RoleProfileCatalog.ADMIN_CODE, List.of("all"), null, Instant.now().plusSeconds(60));
        when(ossPermissionCache.getEntry("oss_admin_all")).thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("oss_admin_all"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("OSS 用户缓存角色为其他系统的 admin");
    }

    /**
     * OSS 用户即使 OSS 缓存 roleCode=admin，也不应拿到 system.admin / warehouse.manage 补发。
     *
     * <p>spec 032 + CO-551 修订：OSS 用户的 system.admin/warehouse.manage 由 OSS 菜单映射决定，
     * 不走本地 admin fallback 补发逻辑。CO-373 后 OSS admin 直接 fail-closed 拒绝。
     */
    @Test
    @DisplayName("OSS admin 用户不走本地 admin fallback 补发 system.admin/warehouse.manage")
    void ossAdminShouldNotGetLocalAdminFallbackPermissions() {
        User user = ossUser("oss_admin_no_fallback", RoleProfileCatalog.ADMIN_CODE);
        when(userRepository.findByUsername("oss_admin_no_fallback")).thenReturn(Optional.of(user));
        // OSS 缓存只返回 bidding，不含 system.admin/warehouse.manage
        OssPermissionCache.CacheEntry entry = new OssPermissionCache.CacheEntry(
                RoleProfileCatalog.ADMIN_CODE, List.of("bidding"), null, Instant.now().plusSeconds(60));
        when(ossPermissionCache.getEntry("oss_admin_no_fallback")).thenReturn(Optional.of(entry));

        // OSS 用户缓存 admin → fail-closed 拒绝，根本不会构造 authorities，
        // 天然不可能拿到 system.admin/warehouse.manage 补发
        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("oss_admin_no_fallback"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("OSS 用户缓存角色为其他系统的 admin");
    }

    /**
     * OSS 用户缓存返回其他角色的独有权限时，这些权限应原样保留（不扩散也不丢失）。
     *
     * <p>验证 OSS 权限的"精确性"：OSS 返回什么就有什么，不多不少。
     */
    @Test
    @DisplayName("OSS 用户缓存权限原样保留，不丢失也不扩散")
    void ossCachedPermissionsShouldBePreservedExactly() {
        User user = ossUser("oss_exact", RoleProfileCatalog.BID_SPECIALIST_CODE);
        when(userRepository.findByUsername("oss_exact")).thenReturn(Optional.of(user));
        // OSS 返回跨角色的混合权限（真实场景：OSS 端可灵活配置）
        List<String> mixedPermissions = List.of(
                "dashboard", "bidding", "task.view.own", "task.handle.own",
                RoleProfileCatalog.SYSTEM_ADMIN_PERMISSION);
        OssPermissionCache.CacheEntry entry = new OssPermissionCache.CacheEntry(
                RoleProfileCatalog.BID_SPECIALIST_CODE, mixedPermissions, null, Instant.now().plusSeconds(60));
        when(ossPermissionCache.getEntry("oss_exact")).thenReturn(Optional.of(entry));

        UserDetails details = userDetailsService.loadUserByUsername("oss_exact");

        // OSS 返回的权限原样保留（含 system.admin，CO-551 修订允许）
        assertThat(details.getAuthorities())
                .extracting("authority")
                .contains("dashboard", "bidding", "task.view.own", "task.handle.own",
                        RoleProfileCatalog.SYSTEM_ADMIN_PERMISSION)
                // 但不含 all
                .doesNotContain("all")
                // 不含未在 OSS 返回中的其他角色独有权限
                .doesNotContain("bidding.manage", "task.review", "retrospective.submit",
                        "brand-auth.edit", "certificate.manage");
    }

    // ───────────────────────── 辅助方法 ─────────────────────────

    private User ossUser(String username, String roleCode) {
        RoleProfile roleProfile = RoleProfile.builder()
                .code(roleCode)
                .name(roleCode)
                .build();
        return User.builder()
                .username(username)
                .password("{noop}password")
                .email(username + "@example.com")
                .fullName(username)
                .role(User.Role.MANAGER)
                .roleProfile(roleProfile)
                .externalOrgSourceApp("OSS")
                .enabled(true)
                .build();
    }
}
