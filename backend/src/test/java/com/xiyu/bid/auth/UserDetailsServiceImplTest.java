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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

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
        // CO-373 + §78：模拟 EffectiveRoleResolver 的真实决策逻辑（与 EffectiveRolePolicy.decide 一致）
        // - OSS 用户缓存 admin：返回 null + OSS_ADMIN_REJECTED（修复 1+2 核心）
        // - OSS 用户缓存命中（非 admin）：返回缓存 roleCode + CACHE_HIT
        // - OSS 用户 cache miss：返回 null + CACHE_MISS_FAIL_CLOSED
        // - 本地用户缓存命中：返回缓存 roleCode + CACHE_HIT（缓存优先于 entity roleCode）
        // - 本地用户缓存空：返回 entity roleCode + LOCAL_USER
        lenient().when(effectiveRoleResolver.resolve(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            Optional<OssPermissionCache.CacheEntry> entry = ossPermissionCache.getEntry(u.getUsername());
            boolean cacheHit = entry.isPresent() && entry.get().roleCode() != null
                    && !entry.get().roleCode().isBlank();
            boolean isOssUser = u.isOssUser();
            if (cacheHit) {
                String cachedRoleCode = entry.get().roleCode();
                // §78：OSS 用户缓存 admin → fail-closed（本地用户缓存 admin 仍合法）
                if (isOssUser && "admin".equalsIgnoreCase(cachedRoleCode.trim())) {
                    return new EffectiveRoleResult(null, EffectiveRoleResult.Source.OSS_ADMIN_REJECTED);
                }
                return new EffectiveRoleResult(cachedRoleCode, EffectiveRoleResult.Source.CACHE_HIT);
            }
            if (isOssUser) {
                return new EffectiveRoleResult(null, EffectiveRoleResult.Source.CACHE_MISS_FAIL_CLOSED);
            }
            return new EffectiveRoleResult(u.getRoleCode(), EffectiveRoleResult.Source.LOCAL_USER);
        });
    }

    @Test
    void unregisteredCustomRoleShouldNotInheritLegacyStaffAuthority() {
        // 未注册 roleCode（legal-reviewer）不应继承 STAFF 兼容，避免误入 hasAnyRole(... 'STAFF' ...) 白名单
        User user = userWithRoleProfile("legal", User.Role.MANAGER, "legal-reviewer");
        when(userRepository.findByUsername("legal")).thenReturn(Optional.of(user));

        UserDetails details = userDetailsService.loadUserByUsername("legal");

        assertThat(details.getAuthorities())
                .extracting("authority")
                .contains("legal-reviewer", "ROLE_LEGAL_REVIEWER")
                .doesNotContain("ROLE_STAFF", "bidding", "project", "knowledge");
    }

    @Test
    void bidOtherDeptShouldNotInheritStaffButKeepOwnCodeAndTaskPermissions() {
        // bid-otherDept（跨部门协同人员）按蓝图不应访问标讯/项目/知识库 → 不继承 ROLE_STAFF；
        // 但保留 ROLE_BID_OTHERDEPT + catalog 的 task 权限（任务 API 用 isAuthenticated，仍可用）
        RoleProfile roleProfile = RoleProfile.builder()
                .code(RoleProfileCatalog.BID_OTHER_DEPT_CODE)
                .name(RoleProfileCatalog.BID_OTHER_DEPT_CODE)
                .build();
        roleProfile.setMenuPermissions(List.of("task-board", "task.view.own", "task.handle.own"));
        User user = User.builder()
                .username("hanhui")
                .password("{noop}password")
                .email("hanhui@example.com")
                .fullName("hanhui")
                .role(User.Role.MANAGER)
                .roleProfile(roleProfile)
                .enabled(true)
                .build();
        when(userRepository.findByUsername("hanhui")).thenReturn(Optional.of(user));

        UserDetails details = userDetailsService.loadUserByUsername("hanhui");

        assertThat(details.getAuthorities())
                .extracting("authority")
                .contains("bid-otherDept", "ROLE_BID_OTHERDEPT",
                        "task-board", "task.view.own", "task.handle.own")
                .doesNotContain("ROLE_STAFF", "bidding", "project", "knowledge", "resource");
    }

    @Test
    void legacyUserWithoutRoleProfileShouldStillGetManagerAuthority() {
        // roleCode 为 null 的纯 Legacy 用户（仅 users.role=MANAGER）不受影响，保留 ROLE_MANAGER
        User user = User.builder()
                .username("legacy")
                .password("{noop}password")
                .email("legacy@example.com")
                .fullName("legacy")
                .role(User.Role.MANAGER)
                .enabled(true)
                .build();
        when(userRepository.findByUsername("legacy")).thenReturn(Optional.of(user));

        UserDetails details = userDetailsService.loadUserByUsername("legacy");

        assertThat(details.getAuthorities())
                .extracting("authority")
                .contains("ROLE_MANAGER");
    }

    @Test
    void unregisteredRoleWithDbMenuPermissionsShouldRespectDbConfig() {
        // 未注册角色保留 DB 显式 menu_permissions（管理员授权），但不继承 STAFF，也不 fallback staff 全套
        RoleProfile roleProfile = RoleProfile.builder()
                .code("vendor-user")
                .name("vendor-user")
                .build();
        roleProfile.setMenuPermissions(java.util.List.of("custom.perm"));
        User user = User.builder()
                .username("vendor")
                .password("{noop}password")
                .email("vendor@example.com")
                .fullName("vendor")
                .role(User.Role.MANAGER)
                .roleProfile(roleProfile)
                .enabled(true)
                .build();
        when(userRepository.findByUsername("vendor")).thenReturn(Optional.of(user));

        UserDetails details = userDetailsService.loadUserByUsername("vendor");

        assertThat(details.getAuthorities())
                .extracting("authority")
                .contains("custom.perm", "vendor-user", "ROLE_VENDOR_USER")
                .doesNotContain("ROLE_STAFF", "bidding");
    }

    @Test
    void bidSpecialistRoleProfileShouldAddBidSpecialistAuthority() {
        User user = userWithRoleProfile("bid_specialist", User.Role.MANAGER, "bid-Team");
        when(userRepository.findByUsername("bid_specialist")).thenReturn(Optional.of(user));

        UserDetails details = userDetailsService.loadUserByUsername("bid_specialist");

        assertThat(details.getAuthorities())
                .extracting("authority")
                .contains("ROLE_BID_TEAM", "bid-Team")
                .doesNotContain("ROLE_STAFF");
    }


    @Test
    void bidAdminShouldHaveRoleAdminCompatibility() {
        User user = userWithRoleProfile("bid_admin", User.Role.MANAGER, "/bidAdmin");
        when(userRepository.findByUsername("bid_admin")).thenReturn(Optional.of(user));
        UserDetails details = userDetailsService.loadUserByUsername("bid_admin");
        assertThat(details.getAuthorities()).extracting("authority").contains("ROLE_ADMIN", "ROLE_BIDADMIN");
    }

    @Test
    void salesShouldHaveRoleManagerCompatibility() {
        User user = userWithRoleProfile("sales_user", User.Role.MANAGER, "bid-projectLeader");
        when(userRepository.findByUsername("sales_user")).thenReturn(Optional.of(user));
        UserDetails details = userDetailsService.loadUserByUsername("sales_user");
        assertThat(details.getAuthorities()).extracting("authority").contains("ROLE_MANAGER");
    }

    @Test
    void bidSpecialistShouldNotHaveRoleStaffCompatibility() {
        User user = userWithRoleProfile("spec_user", User.Role.MANAGER, "bid-Team");
        when(userRepository.findByUsername("spec_user")).thenReturn(Optional.of(user));
        UserDetails details = userDetailsService.loadUserByUsername("spec_user");
        assertThat(details.getAuthorities())
                .extracting("authority")
                .contains("ROLE_BID_TEAM", "bid-Team")
                .doesNotContain("ROLE_STAFF");
    }

    private User userWithRoleProfile(String username, User.Role role, String roleCode) {
        RoleProfile roleProfile = RoleProfile.builder()
                .code(roleCode)
                .name(roleCode)
                .build();
        return User.builder()
                .username(username)
                .password("{noop}password")
                .email(username + "@example.com")
                .fullName(username)
                .role(role)
                .roleProfile(roleProfile)
                .enabled(true)
                .build();
    }

    private User ossUserWithRoleProfile(String username, User.Role role, String roleCode) {
        User user = userWithRoleProfile(username, role, roleCode);
        user.setExternalOrgSourceApp("OSS");
        return user;
    }

    // ——— catalog 守卫（第4b步）测试 ———

    @Test
    void registeredRoleWithCustomMenuPermissionsShouldNotMergeCatalog() {
        // bid_admin（已注册角色）DB 中有自定义 menuPermissions=["dashboard"]，
        // catalog 中定义的 "bidding", "project" 等不应合并进来
        RoleProfile roleProfile = RoleProfile.builder()
                .code("/bidAdmin")
                .name("投标部门管理员")
                .build();
        roleProfile.setMenuPermissions(List.of("dashboard"));
        User user = User.builder()
                .username("custom_bid_admin")
                .password("{noop}password")
                .email("custom_bid_admin@example.com")
                .fullName("custom_bid_admin")
                .role(User.Role.MANAGER)
                .roleProfile(roleProfile)
                .enabled(true)
                .build();
        when(userRepository.findByUsername("custom_bid_admin")).thenReturn(Optional.of(user));

        UserDetails details = userDetailsService.loadUserByUsername("custom_bid_admin");

        assertThat(details.getAuthorities())
                .extracting("authority")
                .contains("dashboard")
                .contains("/bidAdmin", "ROLE_BIDADMIN", "ROLE_ADMIN")
                // catalog 中有但不含在自定义 DB 列表中 → 不应出现
                .doesNotContain("bidding", "project", "bidding.manage", "task.review");
    }

    @Test
    void registeredRoleWithoutMenuPermissionsShouldFallbackToCatalog() {
        // bid_admin DB 中 menu_permissions 为 null → 应 fallback 到 catalog 合并
        // userWithRoleProfile 默认不设 menuPermissions → menuPermissionsValue=null
        User user = userWithRoleProfile("default_bid_admin", User.Role.MANAGER, "/bidAdmin");
        when(userRepository.findByUsername("default_bid_admin")).thenReturn(Optional.of(user));

        UserDetails details = userDetailsService.loadUserByUsername("default_bid_admin");

        assertThat(details.getAuthorities())
                .extracting("authority")
                .contains("bidding", "bidding.manage", "task.review", "project");
    }

    @Test
    void adminStaffShouldNotInheritStaffLegacyRole() {
        User user = userWithRoleProfile("admin_staff_user", User.Role.MANAGER, RoleProfileCatalog.ADMIN_STAFF_CODE);
        when(userRepository.findByUsername("admin_staff_user")).thenReturn(Optional.of(user));

        UserDetails details = userDetailsService.loadUserByUsername("admin_staff_user");

        assertThat(details.getAuthorities())
                .extracting("authority")
                .contains("bid-administration", "ROLE_BID_ADMINISTRATION")
                .doesNotContain("ROLE_STAFF");
    }

    @Test
    void adminStaffShouldKeepCatalogPermissions() {
        User user = userWithRoleProfile("admin_staff2", User.Role.MANAGER, RoleProfileCatalog.ADMIN_STAFF_CODE);
        when(userRepository.findByUsername("admin_staff2")).thenReturn(Optional.of(user));

        UserDetails details = userDetailsService.loadUserByUsername("admin_staff2");

        assertThat(details.getAuthorities())
                .extracting("authority")
                .contains("certificate.manage", "qualification.manage", "qualification.view", "knowledge", "knowledge-qualification")
                .doesNotContain("bidding", "project", "resource", "settings");
    }

    // ——— OSS fail-closed 单元测试 ———

    @Test
    @DisplayName("OSS 用户 cache miss 时应抛 BadCredentialsException，禁止 DB 兜底")
    void ossUserCacheMissShouldThrowAndNotFallbackToDb() {
        // 构造一个 OSS 用户（externalOrgSourceApp 不为空），DB 中虽有 roleProfile 但不应被读取
        RoleProfile roleProfile = RoleProfile.builder()
                .code("/bidAdmin")
                .name("投标管理员")
                .build();
        roleProfile.setMenuPermissions(List.of("bidding"));
        User user = User.builder()
                .username("oss_cache_miss")
                .password("{noop}password")
                .email("oss_cache_miss@example.com")
                .fullName("oss_cache_miss")
                .role(User.Role.MANAGER)
                .roleProfile(roleProfile)
                .externalOrgSourceApp("OSS")
                .enabled(true)
                .build();
        when(userRepository.findByUsername("oss_cache_miss")).thenReturn(Optional.of(user));
        // mock cache miss
        when(ossPermissionCache.getEntry("oss_cache_miss")).thenReturn(Optional.empty());

        // 抛出 BadCredentialsException 即证明 DB 兜底分支未被执行；语义上用户存在但权限状态无效，
        // 避免 Sentry 将合法 OSS 用户误判为"用户不存在"噪声。
        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("oss_cache_miss"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("OSS 用户缓存未命中")
                .hasMessageContaining("oss_cache_miss");
    }

    @Test
    @DisplayName("OSS 用户 cache hit 时应返回缓存中的实时角色权限")
    void ossUserCacheHitShouldReturnAuthoritiesFromCache() {
        // 构造一个 OSS 用户，DB roleProfile 与缓存角色不一致，验证权限来自缓存而非 DB
        RoleProfile roleProfile = RoleProfile.builder()
                .code("bid-specialist")
                .name("投标专员")
                .build();
        roleProfile.setMenuPermissions(List.of("task.view.own"));
        User user = User.builder()
                .username("oss_cache_hit")
                .password("{noop}password")
                .email("oss_cache_hit@example.com")
                .fullName("oss_cache_hit")
                .role(User.Role.MANAGER)
                .roleProfile(roleProfile)
                .externalOrgSourceApp("OSS")
                .enabled(true)
                .build();
        when(userRepository.findByUsername("oss_cache_hit")).thenReturn(Optional.of(user));
        // mock cache hit: /bidAdmin + bidding 权限（与 DB 中的 bid-specialist 不同）
        OssPermissionCache.CacheEntry entry = new OssPermissionCache.CacheEntry(
                "/bidAdmin", List.of("bidding"), null, Instant.now().plusSeconds(60));
        when(ossPermissionCache.getEntry("oss_cache_hit")).thenReturn(Optional.of(entry));

        UserDetails details = userDetailsService.loadUserByUsername("oss_cache_hit");

        // 权限来自缓存（/bidAdmin + bidding），而非 DB 的 bid-specialist；并保留旧接口 ROLE_ADMIN 兼容
        assertThat(details.getAuthorities())
                .extracting("authority")
                .contains("/bidAdmin", "ROLE_BIDADMIN", "ROLE_ADMIN", "bidding")
                .doesNotContain("bid-specialist", "task.view.own");
    }

    @Test
    @DisplayName("OSS 管理类标准角色 cache hit 时应保留 Legacy Role 兼容")
    void ossManagementRolesShouldKeepLegacyRoleCompatibility() {
        User bidLead = ossUserWithRoleProfile("oss_bid_lead", User.Role.MANAGER, RoleProfileCatalog.BID_SPECIALIST_CODE);
        when(userRepository.findByUsername("oss_bid_lead")).thenReturn(Optional.of(bidLead));
        when(ossPermissionCache.getEntry("oss_bid_lead")).thenReturn(Optional.of(new OssPermissionCache.CacheEntry(
                RoleProfileCatalog.BID_LEAD_CODE, List.of("dashboard"), null, Instant.now().plusSeconds(60))));

        UserDetails bidLeadDetails = userDetailsService.loadUserByUsername("oss_bid_lead");

        assertThat(bidLeadDetails.getAuthorities())
                .extracting("authority")
                .contains(RoleProfileCatalog.BID_LEAD_CODE, "ROLE_BID_TEAMLEADER", "ROLE_MANAGER");

        User sales = ossUserWithRoleProfile("oss_sales", User.Role.MANAGER, RoleProfileCatalog.BID_SPECIALIST_CODE);
        when(userRepository.findByUsername("oss_sales")).thenReturn(Optional.of(sales));
        when(ossPermissionCache.getEntry("oss_sales")).thenReturn(Optional.of(new OssPermissionCache.CacheEntry(
                RoleProfileCatalog.SALES_CODE, List.of("dashboard"), null, Instant.now().plusSeconds(60))));

        UserDetails salesDetails = userDetailsService.loadUserByUsername("oss_sales");

        assertThat(salesDetails.getAuthorities())
                .extracting("authority")
                .contains(RoleProfileCatalog.SALES_CODE, "ROLE_BID_PROJECTLEADER", "ROLE_MANAGER");
    }

    @Test
    @DisplayName("OSS 受限标准角色 cache hit 时不应继承 Legacy Role 兼容")
    void ossRestrictedRolesShouldNotInheritLegacyRoleCompatibility() {
        User specialist = ossUserWithRoleProfile("oss_specialist", User.Role.MANAGER, RoleProfileCatalog.BID_ADMIN_CODE);
        when(userRepository.findByUsername("oss_specialist")).thenReturn(Optional.of(specialist));
        when(ossPermissionCache.getEntry("oss_specialist")).thenReturn(Optional.of(new OssPermissionCache.CacheEntry(
                RoleProfileCatalog.BID_SPECIALIST_CODE, List.of("task.view.own"), null, Instant.now().plusSeconds(60))));

        UserDetails specialistDetails = userDetailsService.loadUserByUsername("oss_specialist");

        assertThat(specialistDetails.getAuthorities())
                .extracting("authority")
                .contains(RoleProfileCatalog.BID_SPECIALIST_CODE, "ROLE_BID_TEAM", "task.view.own")
                .doesNotContain("ROLE_MANAGER", "ROLE_ADMIN");

        User adminStaff = ossUserWithRoleProfile("oss_admin_staff", User.Role.MANAGER, RoleProfileCatalog.BID_ADMIN_CODE);
        when(userRepository.findByUsername("oss_admin_staff")).thenReturn(Optional.of(adminStaff));
        when(ossPermissionCache.getEntry("oss_admin_staff")).thenReturn(Optional.of(new OssPermissionCache.CacheEntry(
                RoleProfileCatalog.ADMIN_STAFF_CODE, List.of("certificate.manage"), null, Instant.now().plusSeconds(60))));

        UserDetails adminStaffDetails = userDetailsService.loadUserByUsername("oss_admin_staff");

        assertThat(adminStaffDetails.getAuthorities())
                .extracting("authority")
                .contains(RoleProfileCatalog.ADMIN_STAFF_CODE, "ROLE_BID_ADMINISTRATION", "certificate.manage")
                .doesNotContain("ROLE_MANAGER", "ROLE_ADMIN");

        User otherDept = ossUserWithRoleProfile("oss_other_dept", User.Role.MANAGER, RoleProfileCatalog.BID_ADMIN_CODE);
        when(userRepository.findByUsername("oss_other_dept")).thenReturn(Optional.of(otherDept));
        when(ossPermissionCache.getEntry("oss_other_dept")).thenReturn(Optional.of(new OssPermissionCache.CacheEntry(
                RoleProfileCatalog.BID_OTHER_DEPT_CODE, List.of("task.handle.own"), null, Instant.now().plusSeconds(60))));

        UserDetails otherDeptDetails = userDetailsService.loadUserByUsername("oss_other_dept");

        assertThat(otherDeptDetails.getAuthorities())
                .extracting("authority")
                .contains(RoleProfileCatalog.BID_OTHER_DEPT_CODE, "ROLE_BID_OTHERDEPT", "task.handle.own")
                .doesNotContain("ROLE_MANAGER", "ROLE_ADMIN");
    }

    @Test
    @DisplayName("OSS 缓存菜单权限非空时不应合并标准角色 catalog 权限")
    void ossCachedMenuPermissionsShouldNotMergeRegisteredRoleCatalogPermissions() {
        RoleProfile roleProfile = RoleProfile.builder()
                .code(RoleProfileCatalog.BID_SPECIALIST_CODE)
                .name("投标专员")
                .build();
        roleProfile.setMenuPermissions(List.of("task.view.own"));
        User user = User.builder()
                .username("oss_catalog_merge")
                .password("{noop}password")
                .email("oss_catalog_merge@example.com")
                .fullName("oss_catalog_merge")
                .role(User.Role.MANAGER)
                .roleProfile(roleProfile)
                .externalOrgSourceApp("OSS")
                .enabled(true)
                .build();
        when(userRepository.findByUsername("oss_catalog_merge")).thenReturn(Optional.of(user));
        OssPermissionCache.CacheEntry entry = new OssPermissionCache.CacheEntry(
                RoleProfileCatalog.BID_ADMIN_CODE, List.of("dashboard", "bidding"), null, Instant.now().plusSeconds(60));
        when(ossPermissionCache.getEntry("oss_catalog_merge")).thenReturn(Optional.of(entry));

        UserDetails details = userDetailsService.loadUserByUsername("oss_catalog_merge");

        assertThat(details.getAuthorities())
                .extracting("authority")
                .contains("/bidAdmin", "ROLE_BIDADMIN", "ROLE_ADMIN", "ROLE_MANAGER")
                .contains("dashboard", "bidding")
                .doesNotContain("retrospective.submit", "bidding.sync", "warehouse.manage", "brand-auth.edit",
                        "task.view.own");
    }

    @Test
    @DisplayName("本地账号 cache miss 时应使用 DB roleProfile 兜底")
    void localUserCacheMissShouldFallbackToDbRoleProfile() {
        // 构造一个本地账号（externalOrgSourceApp 为空），cache miss 时走 DB 兜底
        User user = userWithRoleProfile("local_admin_fallback", User.Role.MANAGER, "/bidAdmin");
        when(userRepository.findByUsername("local_admin_fallback")).thenReturn(Optional.of(user));
        // mock cache miss
        when(ossPermissionCache.getEntry("local_admin_fallback")).thenReturn(Optional.empty());

        UserDetails details = userDetailsService.loadUserByUsername("local_admin_fallback");

        // 权限来自 DB roleProfile（/bidAdmin），证明 DB 兜底正常工作
        assertThat(details.getAuthorities())
                .extracting("authority")
                .contains("/bidAdmin", "ROLE_BIDADMIN", "ROLE_ADMIN");
    }

    // ——— 补充测试：OSS 缓存边缘场景 ———

    @Test
    @DisplayName("OSS 用户缓存中角色未在 catalog 注册时应被白名单拒绝")
    void ossUserWithUnregisteredRoleShouldBeRejectedByWhitelist() {
        // OSS 缓存返回未注册角色码 → LoginRoleWhitelist 拒绝，不应进入 catalog 合并逻辑
        RoleProfile roleProfile = RoleProfile.builder()
                .code("custom-oss-role")
                .name("自定义角色")
                .build();
        User user = User.builder()
                .username("oss_unregistered")
                .password("{noop}password")
                .email("oss_unregistered@example.com")
                .fullName("oss_unregistered")
                .role(User.Role.MANAGER)
                .roleProfile(roleProfile)
                .externalOrgSourceApp("OSS")
                .enabled(true)
                .build();
        when(userRepository.findByUsername("oss_unregistered")).thenReturn(Optional.of(user));
        OssPermissionCache.CacheEntry entry = new OssPermissionCache.CacheEntry(
                "custom-oss-role", List.of("dashboard"), null, Instant.now().plusSeconds(60));
        when(ossPermissionCache.getEntry("oss_unregistered")).thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("oss_unregistered"))
                .isInstanceOf(org.springframework.security.core.AuthenticationException.class)
                .hasMessageContaining("角色未授权");
    }

    @Test
    @DisplayName("§78: OSS 缓存 admin 角色（含 all 权限）应 fail-closed 拒绝，不进入权限扩散逻辑")
    void ossCachedAdminRoleShouldBeRejected() {
        // §78 修复 1+2：OSS 用户缓存 admin 时，EffectiveRolePolicy.decide 返回 OSS_ADMIN_REJECTED，
        // UserDetailsServiceImpl.resolveRoleSource 抛 BadCredentialsException，根本不进入权限构建逻辑。
        RoleProfile roleProfile = RoleProfile.builder()
                .code(RoleProfileCatalog.BID_SPECIALIST_CODE)
                .name("投标专员")
                .build();
        User user = User.builder()
                .username("oss_admin_all")
                .password("{noop}password")
                .email("oss_admin_all@example.com")
                .fullName("oss_admin_all")
                .role(User.Role.MANAGER)
                .roleProfile(roleProfile)
                .externalOrgSourceApp("OSS")
                .enabled(true)
                .build();
        when(userRepository.findByUsername("oss_admin_all")).thenReturn(Optional.of(user));
        OssPermissionCache.CacheEntry entry = new OssPermissionCache.CacheEntry(
                RoleProfileCatalog.ADMIN_CODE, List.of("all"), null, Instant.now().plusSeconds(60));
        when(ossPermissionCache.getEntry("oss_admin_all")).thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("oss_admin_all"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("OSS 用户缓存角色为其他系统的 admin")
                .hasMessageContaining("oss_admin_all");
    }

    @Test
    @DisplayName("§78: OSS admin 用户（含 system.admin/warehouse.manage）也应 fail-closed 拒绝")
    void ossAdminUserShouldBeRejectedEvenWithSystemAdminPermission() {
        // 即使 OSS 端下发了 system.admin/warehouse.manage 等业务权限，只要 roleCode=admin 就拒绝
        RoleProfile roleProfile = RoleProfile.builder()
                .code(RoleProfileCatalog.BID_SPECIALIST_CODE)
                .name("投标专员")
                .build();
        User user = User.builder()
                .username("oss_admin_sys")
                .password("{noop}password")
                .email("oss_admin_sys@example.com")
                .fullName("oss_admin_sys")
                .role(User.Role.MANAGER)
                .roleProfile(roleProfile)
                .externalOrgSourceApp("OSS")
                .enabled(true)
                .build();
        when(userRepository.findByUsername("oss_admin_sys")).thenReturn(Optional.of(user));
        OssPermissionCache.CacheEntry entry = new OssPermissionCache.CacheEntry(
                RoleProfileCatalog.ADMIN_CODE,
                List.of("bidding", RoleProfileCatalog.SYSTEM_ADMIN_PERMISSION,
                        RoleProfileCatalog.WAREHOUSE_MANAGE_PERMISSION),
                null, Instant.now().plusSeconds(60));
        when(ossPermissionCache.getEntry("oss_admin_sys")).thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("oss_admin_sys"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("OSS 用户缓存角色为其他系统的 admin");
    }

    @Test
    @DisplayName("本地 admin 用户 authorities 仍含 all/system.admin/warehouse.manage（回归）")
    void localAdminUserShouldHaveAllAndSystemAdminPermissionRegression() {
        RoleProfile roleProfile = RoleProfile.builder()
                .code(RoleProfileCatalog.ADMIN_CODE)
                .name("管理员")
                .build();
        roleProfile.setMenuPermissions(List.of("all"));
        User user = User.builder()
                .username("local_admin_regression")
                .password("{noop}password")
                .email("local_admin@example.com")
                .fullName("local_admin")
                .role(User.Role.ADMIN)
                .roleProfile(roleProfile)
                .enabled(true)
                .build();
        when(userRepository.findByUsername("local_admin_regression")).thenReturn(Optional.of(user));

        UserDetails details = userDetailsService.loadUserByUsername("local_admin_regression");

        assertThat(details.getAuthorities())
                .extracting("authority")
                .contains("all", RoleProfileCatalog.SYSTEM_ADMIN_PERMISSION,
                        RoleProfileCatalog.WAREHOUSE_MANAGE_PERMISSION)
                .contains("bidding.manage", "task.review", "retrospective.submit",
                        "brand-auth.edit", "certificate.manage", "qualification.view");
    }

    @Test
    @DisplayName("OSS 缓存 menuPermissions 为空时不应合并 catalog 权限")
    void ossCachedEmptyMenuPermissionsShouldNotMergeCatalog() {
        // OSS 缓存命中但 menuPermissions 为空列表 → OSS 用户权限唯一来源是 OSS 菜单映射，不合并 catalog
        RoleProfile roleProfile = RoleProfile.builder()
                .code(RoleProfileCatalog.BID_SPECIALIST_CODE)
                .name("投标专员")
                .build();
        User user = User.builder()
                .username("oss_empty_menu")
                .password("{noop}password")
                .email("oss_empty_menu@example.com")
                .fullName("oss_empty_menu")
                .role(User.Role.MANAGER)
                .roleProfile(roleProfile)
                .externalOrgSourceApp("OSS")
                .enabled(true)
                .build();
        when(userRepository.findByUsername("oss_empty_menu")).thenReturn(Optional.of(user));
        OssPermissionCache.CacheEntry entry = new OssPermissionCache.CacheEntry(
                RoleProfileCatalog.BID_LEAD_CODE, List.of(), null, Instant.now().plusSeconds(60));
        when(ossPermissionCache.getEntry("oss_empty_menu")).thenReturn(Optional.of(entry));

        UserDetails details = userDetailsService.loadUserByUsername("oss_empty_menu");

        assertThat(details.getAuthorities())
                .extracting("authority")
                .contains("bid-TeamLeader", "ROLE_BID_TEAMLEADER", "ROLE_MANAGER")
                .doesNotContain("bidding.manage", "task.assign", "retrospective.submit",
                        "closure.request", "warehouse.manage");
    }

    @Test
    @DisplayName("§78: 本地账号 OSS 缓存命中时 roleCode 用缓存值，menuPermissions 仍来自 DB（端口边界）")
    void localUserWithOssCacheHitShouldUseCacheRoleCodeButDbMenuPermissions() {
        // §78 修复 2 后的新行为：
        //   - roleCode 决策走 EffectiveRoleResolver（缓存命中 → CACHE_HIT + /bidAdmin）
        //   - menuPermissions 仍来自 DB RoleProfile.menu_permissions（security 端口只暴露 roleCode，
        //     不暴露缓存内部结构，避免 security ↔ crm 循环依赖）
        //   - DB menuPermissions 非空时不合并 catalog（保持原"权限不扩散"语义）
        RoleProfile roleProfile = RoleProfile.builder()
                .code(RoleProfileCatalog.BID_SPECIALIST_CODE)
                .name("投标专员")
                .build();
        roleProfile.setMenuPermissions(List.of("task.view.own"));
        User user = User.builder()
                .username("local_with_cache")
                .password("{noop}password")
                .email("local_with_cache@example.com")
                .fullName("local_with_cache")
                .role(User.Role.MANAGER)
                .roleProfile(roleProfile)
                .enabled(true)
                .build();
        when(userRepository.findByUsername("local_with_cache")).thenReturn(Optional.of(user));
        OssPermissionCache.CacheEntry entry = new OssPermissionCache.CacheEntry(
                RoleProfileCatalog.BID_ADMIN_CODE, List.of("dashboard", "bidding"),
                null, Instant.now().plusSeconds(60));
        when(ossPermissionCache.getEntry("local_with_cache")).thenReturn(Optional.of(entry));

        UserDetails details = userDetailsService.loadUserByUsername("local_with_cache");

        // roleCode 来自缓存（/bidAdmin），menuPermissions 来自 DB（task.view.own）；
        // 不合并 catalog 细粒度权限（DB menuPermissions 非空时跳过 fallback）
        assertThat(details.getAuthorities())
                .extracting("authority")
                .contains("/bidAdmin", "ROLE_BIDADMIN", "ROLE_ADMIN", "ROLE_MANAGER")
                .contains("task.view.own")
                .doesNotContain("dashboard", "bidding", "bidding.manage", "task.review",
                        "retrospective.submit", "warehouse.manage");
    }

    @Test
    @DisplayName("§78: OSS 用户缓存 roleCode 漂移为 admin（CO-391 真实根因）应 fail-closed 拒绝")
    void ossUserCacheHitWithDriftedAdminRoleCodeShouldBeRejected() {
        // CO-391 真实根因（production 数据已确认）：
        // 06234 郑蓉蓉为 OSS 用户（external_org_source_app=oss），DB roleProfile.code=/bidAdmin，
        // 但 OSS 缓存中 roleCode="admin"（来自 OSS 系统映射，与 DB 不一致）。
        //
        // §78 修复 1+2 后的新行为：
        //   - EffectiveRolePolicy.decide 检测到 OSS 用户缓存 admin → 返回 null + OSS_ADMIN_REJECTED
        //   - UserDetailsServiceImpl.resolveRoleSource 检测到 OSS_ADMIN_REJECTED → 抛 BadCredentialsException
        //   - 不再走"controller 注解加 admin 字面字符串兜底"的 specs/032 旧策略
        RoleProfile roleProfile = RoleProfile.builder()
                .code(RoleProfileCatalog.BID_ADMIN_CODE)
                .name("投标管理员")
                .build();
        User user = User.builder()
                .username("06234")
                .password("{noop}password")
                .email("06234@example.com")
                .fullName("郑蓉蓉")
                .role(User.Role.MANAGER)
                .roleProfile(roleProfile)
                .externalOrgSourceApp("OSS")
                .enabled(true)
                .build();
        when(userRepository.findByUsername("06234")).thenReturn(Optional.of(user));
        // OSS 缓存 roleCode 漂移为 "admin"（非 DB 规范 /bidAdmin）
        OssPermissionCache.CacheEntry entry = new OssPermissionCache.CacheEntry(
                "admin", List.of("bidding"), null, Instant.now().plusSeconds(60));
        when(ossPermissionCache.getEntry("06234")).thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("06234"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("OSS 用户缓存角色为其他系统的 admin")
                .hasMessageContaining("06234");
    }

    @Test
    @DisplayName("§78: 覃超颖案例（OSS 用户缓存 admin）应 fail-closed 拒绝（修复 1+2 核心回归测试）")
    void ossUserCachedAdminShouldBeRejected_QinChaoyingCase() {
        // 覃超颖案例：bidding/60 报 403，根因是 OSS 端把 admin 角色误传给本系统。
        // 修复 1（EffectiveRolePolicy.decide OSS admin fail-closed）+
        // 修复 2（UserDetailsServiceImpl 改走 EffectiveRoleResolver）+
        // 修复 3（TenderController @PreAuthorize 加 BID_SYSTEMADMIN）+
        // 修复 4（hasAdminAccess 排除 OSS 用户）四条故障链联合修复后，
        // 覃超颖应直接在登录阶段被拒绝（而不是登录成功后访问 Tender 403）。
        RoleProfile roleProfile = RoleProfile.builder()
                .code(RoleProfileCatalog.BID_ADMIN_CODE)
                .name("投标管理员")
                .build();
        User user = User.builder()
                .username("qinchaoying")
                .password("{noop}password")
                .email("qinchaoying@example.com")
                .fullName("覃超颖")
                .role(User.Role.MANAGER)
                .roleProfile(roleProfile)
                .externalOrgSourceApp("OSS")
                .enabled(true)
                .build();
        when(userRepository.findByUsername("qinchaoying")).thenReturn(Optional.of(user));
        OssPermissionCache.CacheEntry entry = new OssPermissionCache.CacheEntry(
                "admin", List.of("bidding"), null, Instant.now().plusSeconds(60));
        when(ossPermissionCache.getEntry("qinchaoying")).thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("qinchaoying"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("OSS 用户缓存角色为其他系统的 admin")
                .hasMessageContaining("qinchaoying");
    }

    @Test
    @DisplayName("OSS 用户只有 resource-* 子权限时自动补 resource 父权限")
    void ossUserWithResourceChildrenShouldGainResourceParent() {
        User user = ossUserWithRoleProfile("oss_project_leader_resource", User.Role.MANAGER,
                RoleProfileCatalog.SALES_CODE);
        when(userRepository.findByUsername(user.getUsername())).thenReturn(Optional.of(user));
        // OSS 端对 bid-projectLeader 只下发 100504/100505 → 映射为 resource-account/resource-ca
        when(ossPermissionCache.getEntry(user.getUsername())).thenReturn(Optional.of(
                new OssPermissionCache.CacheEntry(RoleProfileCatalog.SALES_CODE,
                        List.of("resource-account", "resource-ca"), null,
                        Instant.now().plusSeconds(60))));

        UserDetails details = userDetailsService.loadUserByUsername(user.getUsername());

        assertThat(details.getAuthorities())
                .extracting("authority")
                .contains("resource-account", "resource-ca", "resource")
                .contains(RoleProfileCatalog.SALES_CODE, "ROLE_BID_PROJECTLEADER", "ROLE_MANAGER");
    }

    @Test
    @DisplayName("本地用户只有 resource-* 子权限时自动补 resource 父权限")
    void localUserWithResourceChildrenShouldGainResourceParent() {
        RoleProfile roleProfile = RoleProfile.builder()
                .code(RoleProfileCatalog.SALES_CODE)
                .name("投标项目负责人")
                .build();
        roleProfile.setMenuPermissions(List.of("resource-account", "resource-ca"));
        User user = User.builder()
                .username("local_project_leader_resource")
                .password("{noop}password")
                .email("local_plr@example.com")
                .fullName("local_plr")
                .role(User.Role.MANAGER)
                .roleProfile(roleProfile)
                .enabled(true)
                .build();
        when(userRepository.findByUsername(user.getUsername())).thenReturn(Optional.of(user));
        when(ossPermissionCache.getEntry(user.getUsername())).thenReturn(Optional.empty());

        UserDetails details = userDetailsService.loadUserByUsername(user.getUsername());

        assertThat(details.getAuthorities())
                .extracting("authority")
                .contains("resource-account", "resource-ca", "resource")
                .contains(RoleProfileCatalog.SALES_CODE, "ROLE_BID_PROJECTLEADER", "ROLE_MANAGER");
    }

    @Test
    @DisplayName("没有 resource-* 子权限时不应补 resource 父权限")
    void userWithoutResourceChildrenShouldNotGainResourceParent() {
        User user = ossUserWithRoleProfile("oss_no_resource", User.Role.MANAGER,
                RoleProfileCatalog.BID_OTHER_DEPT_CODE);
        when(userRepository.findByUsername(user.getUsername())).thenReturn(Optional.of(user));
        when(ossPermissionCache.getEntry(user.getUsername())).thenReturn(Optional.of(
                new OssPermissionCache.CacheEntry(RoleProfileCatalog.BID_OTHER_DEPT_CODE,
                        List.of("task.view.own", "task.handle.own"), null,
                        Instant.now().plusSeconds(60))));

        UserDetails details = userDetailsService.loadUserByUsername(user.getUsername());

        assertThat(details.getAuthorities())
                .extracting("authority")
                .doesNotContain("resource");
    }

    @ParameterizedTest(name = "OSS {0} 用户权限不扩散")
    @ValueSource(strings = {"/bidAdmin", "bid-SystemAdmin", "bid-TeamLeader", "bid-projectLeader",
            "bid-Team", "bid-otherDept", "bid-administration"})
    @DisplayName("OSS 用户权限严格等于 OSS 返回值，不含扩散权限（哨兵测试，不含 admin：admin 走 §78 拒绝路径）")
    void ossUserAuthoritiesMustNotContainExpansionPermissions(String roleCode) {
        User user = ossUserWithRoleProfile("oss_sentinel_" + roleCode.replace("/", ""),
                User.Role.MANAGER, roleCode);
        when(userRepository.findByUsername(user.getUsername())).thenReturn(Optional.of(user));

        // OSS 只返回 2 个菜单权限
        List<String> ossMenuPerms = List.of("dashboard", "bidding");
        when(ossPermissionCache.getEntry(user.getUsername())).thenReturn(Optional.of(
                new OssPermissionCache.CacheEntry(roleCode, ossMenuPerms, null,
                        Instant.now().plusSeconds(60))));

        UserDetails details = userDetailsService.loadUserByUsername(user.getUsername());

        // ★ 核心断言：authorities 只包含 OSS 返回的菜单权限 + 角色 authority
        assertThat(details.getAuthorities())
                .extracting("authority")
                .contains("dashboard", "bidding")         // OSS 返回的
                .doesNotContain(
                        "all",                             // admin 专属
                        "task.review",                     // /bidAdmin seed
                        "retrospective.submit",            // /bidAdmin seed
                        "certificate.manage",              // bid-administration seed
                        "task.view.own",                   // bid-Team seed（非 OSS 返回）
                        "closure.review"                   // /bidAdmin seed
                );
    }

    @Test
    @DisplayName("OSS bid-SystemAdmin：独立角色 authority + ROLE_ADMIN 过渡兼容，不含 all / catalog 扩散")
    void ossBidSystemAdminShouldKeepOwnCodeAndLegacyAdminCompatWithoutAll() {
        User user = ossUserWithRoleProfile("oss_system_admin", User.Role.MANAGER,
                RoleProfileCatalog.BID_SYSTEM_ADMIN_CODE);
        when(userRepository.findByUsername("oss_system_admin")).thenReturn(Optional.of(user));
        // OSS 只下发 dashboard；不应合并 BID_ADMIN_PERMISSIONS seed
        when(ossPermissionCache.getEntry("oss_system_admin")).thenReturn(Optional.of(
                new OssPermissionCache.CacheEntry(
                        RoleProfileCatalog.BID_SYSTEM_ADMIN_CODE,
                        List.of("dashboard", "bidding"),
                        null,
                        Instant.now().plusSeconds(60))));

        UserDetails details = userDetailsService.loadUserByUsername("oss_system_admin");

        assertThat(details.getAuthorities())
                .extracting("authority")
                .contains(
                        RoleProfileCatalog.BID_SYSTEM_ADMIN_CODE,
                        "ROLE_BID_SYSTEMADMIN",
                        // 过渡兼容：legacyRoleForCode(bid-SystemAdmin) → ADMIN，保证 hasAnyRole('ADMIN') 不炸
                        "ROLE_ADMIN",
                        "dashboard",
                        "bidding")
                .doesNotContain(
                        "all",
                        "task.review",
                        "retrospective.submit",
                        "closure.review",
                        RoleProfileCatalog.SYSTEM_ADMIN_PERMISSION);
    }
}
