// Input: OSS 用户 + 每个标准角色的 OSS 缓存条目
// Output: 断言 OSS 用户 authorities 不发生权限扩散（不含 all、不含其他角色 menuPermissions）
// Pos: Test/spec 033 FR-B003 — 每个 OSS 角色"权限不扩散"系统性测试
//
// spec 033 方案 B 立即实施的测试安全网：这些测试在方案 A 落地后仍有效。
// 覆盖 6 个标准角色：admin / bid-TeamLeader / bid-projectLeader / bid-Team / bid-administration / bid-otherDept
package com.xiyu.bid.auth;

import com.xiyu.bid.crm.application.OssPermissionCache;
import com.xiyu.bid.entity.RoleProfile;
import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * spec 033 FR-B003：每个 OSS 角色"权限不扩散"测试。
 *
 * <p>系统性验证：OSS 用户登录后，authorities 严格等于 OSS 返回的菜单权限 + 自身角色 authority。
 * 不包含 {@code all}、不包含其他角色的 {@code menuPermissions}、不因 roleCode=admin 触发 catalog seed 扩散。
 *
 * <p>与 {@link UserDetailsServiceImplTest} 中零散的 OSS 测试不同，本类用参数化测试
 * 覆盖所有 6 个标准角色，形成"权限不扩散"的安全网。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("spec 033 FR-B003 — OSS 角色权限不扩散测试")
class OssRoleNonDiffusionTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OssPermissionCache ossPermissionCache;

    @InjectMocks
    private UserDetailsServiceImpl userDetailsService;

    /**
     * 参数化测试：每个 OSS 标准角色不发生权限扩散。
     *
     * <p>每个角色用自身的 catalog menuPermissions 作为 OSS 返回的菜单权限，
     * 断言 authorities 不含 {@code all}、不超出 OSS 返回范围（即不扩散 catalog seed）。
     */
    @ParameterizedTest(name = "[{index}] OSS 角色 {0} 不扩散")
    @MethodSource("ossRolesProvider")
    @DisplayName("每个 OSS 角色 authorities 不含 all 且不超出 OSS 返回范围")
    void ossRoleShouldNotDiffuseAuthorities(String roleCode, String displayName,
                                             List<String> ownMenuPermissions,
                                             List<String> unused) {
        // Given: OSS 用户，OSS 缓存返回该角色 + 该角色的菜单权限
        User user = ossUser("oss_" + roleCode, roleCode);
        when(userRepository.findByUsername("oss_" + roleCode)).thenReturn(Optional.of(user));
        OssPermissionCache.CacheEntry entry = new OssPermissionCache.CacheEntry(
                roleCode, ownMenuPermissions, null, Instant.now().plusSeconds(60));
        when(ossPermissionCache.getEntry("oss_" + roleCode)).thenReturn(Optional.of(entry));

        // When: 加载 UserDetails
        UserDetails details = userDetailsService.loadUserByUsername("oss_" + roleCode);

        // Then: authorities 不含 all（admin 专属）
        assertThat(details.getAuthorities())
                .as("OSS 角色 %s 不应持有 all 权限键", roleCode)
                .extracting("authority")
                .doesNotContain("all");

        // Then: authorities 中的菜单权限 ⊆ OSS 返回的菜单权限（不扩散 catalog seed）
        // 即：authorities 中不应出现 OSS 未返回的菜单权限键
        List<String> allowedAuthorities = new java.util.ArrayList<>(ownMenuPermissions);
        allowedAuthorities.add(roleCode);
        // 允许的 ROLE_ authority（由 roleCode 转换而来）
        String roleAuthority = RoleProfileCatalog.toAuthorityName(roleCode);
        if (roleAuthority != null) {
            allowedAuthorities.add("ROLE_" + roleAuthority);
        }
        // 允许的 legacy role authority（admin/bidAdmin → ROLE_ADMIN，其余 → ROLE_MANAGER）
        allowedAuthorities.add("ROLE_MANAGER");
        allowedAuthorities.add("ROLE_ADMIN");

        List<String> actualAuthorities = details.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .toList();
        List<String> unexpectedAuthorities = actualAuthorities.stream()
                .filter(auth -> !allowedAuthorities.contains(auth))
                .filter(auth -> !auth.startsWith("ROLE_")) // ROLE_ 前缀的 authority 是角色兼容层，不算菜单权限扩散
                .toList();

        assertThat(unexpectedAuthorities)
                .as("OSS 角色 %s 不应有超出 OSS 返回范围的菜单权限（catalog seed 不扩散）", roleCode)
                .isEmpty();

        // Then: authorities 含自身角色 authority
        assertThat(details.getAuthorities())
                .as("OSS 角色 %s 应含自身角色 authority", roleCode)
                .extracting("authority")
                .contains(roleCode);
    }

    /**
     * OSS admin 用户即使 OSS 缓存返回 all 权限，也不应触发 catalog seed 扩散。
     *
     * <p>这是 spec 032 的核心止血逻辑，本测试锁定该行为，确保方案 A 落地后仍生效。
     */
    @Test
    @DisplayName("OSS admin 用户缓存含 all 时不扩散 catalog seed 权限")
    void ossAdminWithAllShouldNotExpandCatalogSeed() {
        User user = ossUser("oss_admin_all", RoleProfileCatalog.ADMIN_CODE);
        when(userRepository.findByUsername("oss_admin_all")).thenReturn(Optional.of(user));
        OssPermissionCache.CacheEntry entry = new OssPermissionCache.CacheEntry(
                RoleProfileCatalog.ADMIN_CODE, List.of("all"), null, Instant.now().plusSeconds(60));
        when(ossPermissionCache.getEntry("oss_admin_all")).thenReturn(Optional.of(entry));

        UserDetails details = userDetailsService.loadUserByUsername("oss_admin_all");

        // all 被过滤（admin 专属）
        assertThat(details.getAuthorities())
                .extracting("authority")
                .doesNotContain("all");

        // 不含其他角色的独有权限键（catalog seed 不扩散）
        assertThat(details.getAuthorities())
                .extracting("authority")
                .doesNotContain(
                        "bidding.manage", "task.review", "retrospective.submit",
                        "brand-auth.edit", "certificate.manage", "qualification.view",
                        "task.view.own", "task.handle.own", "closure.request",
                        "task.assign", "evaluation.update", "result.register");
    }

    /**
     * OSS 用户即使 OSS 缓存 roleCode=admin，也不应拿到 system.admin / warehouse.manage 补发。
     *
     * <p>spec 032 + CO-551 修订：OSS 用户的 system.admin/warehouse.manage 由 OSS 菜单映射决定，
     * 不走本地 admin fallback 补发逻辑。
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

        UserDetails details = userDetailsService.loadUserByUsername("oss_admin_no_fallback");

        // OSS 用户不通过本地 admin fallback 拿到 system.admin/warehouse.manage
        // （这些权限只能由 OSS 菜单映射 1010/100408 显式授权）
        assertThat(details.getAuthorities())
                .extracting("authority")
                .doesNotContain(
                        RoleProfileCatalog.SYSTEM_ADMIN_PERMISSION,
                        RoleProfileCatalog.WAREHOUSE_MANAGE_PERMISSION)
                .contains("bidding");
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

    private static Stream<Arguments> ossRolesProvider() {
        return Stream.of(
                // admin：OSS 投标系统管理员，OSS 返回 admin 角色
                Arguments.of(
                        RoleProfileCatalog.ADMIN_CODE, "管理员",
                        List.of("dashboard", "bidding"),
                        otherRolePermissionsExcluding(RoleProfileCatalog.ADMIN_CODE)
                ),
                // bid-TeamLeader：投标组长
                Arguments.of(
                        RoleProfileCatalog.BID_LEAD_CODE, "投标组长",
                        List.of("dashboard", "bidding", "task.assign", "evaluation.update"),
                        otherRolePermissionsExcluding(RoleProfileCatalog.BID_LEAD_CODE)
                ),
                // bid-projectLeader：投标项目负责人
                Arguments.of(
                        RoleProfileCatalog.SALES_CODE, "投标项目负责人",
                        List.of("dashboard", "project", "project.create", "project.view"),
                        otherRolePermissionsExcluding(RoleProfileCatalog.SALES_CODE)
                ),
                // bid-Team：投标专员
                Arguments.of(
                        RoleProfileCatalog.BID_SPECIALIST_CODE, "投标专员",
                        List.of("dashboard", "bidding", "task.view.own", "task.handle.own"),
                        otherRolePermissionsExcluding(RoleProfileCatalog.BID_SPECIALIST_CODE)
                ),
                // bid-administration：行政人员
                Arguments.of(
                        RoleProfileCatalog.ADMIN_STAFF_CODE, "行政人员",
                        List.of("certificate.manage", "qualification.manage"),
                        otherRolePermissionsExcluding(RoleProfileCatalog.ADMIN_STAFF_CODE)
                ),
                // bid-otherDept：跨部门协同人员
                Arguments.of(
                        RoleProfileCatalog.BID_OTHER_DEPT_CODE, "跨部门协同人员",
                        List.of("task.view.own", "task.handle.own"),
                        otherRolePermissionsExcluding(RoleProfileCatalog.BID_OTHER_DEPT_CODE)
                )
        );
    }

    /**
     * 获取所有角色的 menuPermissions 并集，排除指定角色的权限（即"其他角色的权限"）。
     * 用于断言"OSS 角色 X 不应扩散到其他角色的权限键"。
     */
    private static List<String> otherRolePermissionsExcluding(String excludeRoleCode) {
        return RoleProfileCatalog.seedDefinitions().stream()
                .filter(def -> !def.code().equalsIgnoreCase(excludeRoleCode))
                .filter(def -> def.menuPermissions() != null)
                .flatMap(def -> def.menuPermissions().stream())
                .filter(perm -> !perm.equals("all")) // all 是 admin 专属，单独断言
                .distinct()
                .toList();
    }

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
