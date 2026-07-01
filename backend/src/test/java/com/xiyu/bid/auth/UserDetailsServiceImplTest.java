package com.xiyu.bid.auth;

import com.xiyu.bid.crm.application.OssPermissionCache;
import com.xiyu.bid.entity.RoleProfile;
import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OssPermissionCache ossPermissionCache;

    @InjectMocks
    private UserDetailsServiceImpl userDetailsService;

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
                .contains("certificate.manage", "qualification.view", "knowledge", "knowledge-qualification")
                .doesNotContain("bidding", "project", "resource", "settings");
    }

    // ——— OSS fail-closed 单元测试 ———

    @Test
    @DisplayName("OSS 用户 cache miss 时应抛 UsernameNotFoundException，禁止 DB 兜底")
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

        // 抛出 UsernameNotFoundException 即证明 DB 兜底分支未被执行
        // （若走了 DB fallback，roleCode=/bidAdmin 不会抛异常，会正常返回 authorities）
        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("oss_cache_miss"))
                .isInstanceOf(UsernameNotFoundException.class)
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
    @DisplayName("OSS 缓存菜单权限非空时应合并标准角色 catalog 权限")
    void ossCachedMenuPermissionsShouldMergeRegisteredRoleCatalogPermissions() {
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
                .contains("dashboard", "bidding",
                        "retrospective.submit", "bidding.sync", "warehouse.manage", "brand-auth.edit")
                .doesNotContain("task.view.own");
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
    @DisplayName("OSS 缓存 admin 角色含 all 权限时应展开全部 seed 权限")
    void ossCachedAdminRoleShouldExpandAllSeedPermissions() {
        // OSS 缓存角色=admin + menuPermissions=["all"] → 触发全 seed 权限展开
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

        UserDetails details = userDetailsService.loadUserByUsername("oss_admin_all");

        // admin + all → 展开所有 seed 权限（含各角色的细粒度权限）
        assertThat(details.getAuthorities())
                .extracting("authority")
                .contains("admin", "ROLE_ADMIN")
                .contains("bidding.manage", "task.review", "retrospective.submit",
                        "warehouse.manage", "brand-auth.edit")
                .contains("certificate.manage", "qualification.view");
    }

    @Test
    @DisplayName("OSS 缓存 menuPermissions 为空时仍应合并 catalog 权限")
    void ossCachedEmptyMenuPermissionsShouldStillMergeCatalog() {
        // OSS 缓存命中但 menuPermissions 为空列表 → usingOssCachedPermissions=true → 合并 catalog
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

        // menuPermissions 为空，但 usingOssCachedPermissions=true → 合并 catalog
        assertThat(details.getAuthorities())
                .extracting("authority")
                .contains("bid-TeamLeader", "ROLE_BID_TEAMLEADER", "ROLE_MANAGER")
                .contains("bidding.manage", "task.assign", "retrospective.submit",
                        "closure.request", "warehouse.manage");
    }

    @Test
    @DisplayName("本地账号 OSS 缓存命中时应优先使用缓存权限并合并 catalog")
    void localUserWithOssCacheHitShouldUseCacheAndMergeCatalog() {
        // 本地账号（externalOrgSourceApp 为空）但 OSS 缓存中有记录
        // → 走 OSS 缓存分支，usingOssCachedPermissions=true → 合并 catalog 细粒度权限
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

        // 缓存命中 → 用缓存角色 /bidAdmin + 缓存菜单权限
        // usingOssCachedPermissions=true → 合并 catalog 细粒度权限
        assertThat(details.getAuthorities())
                .extracting("authority")
                .contains("/bidAdmin", "ROLE_BIDADMIN", "ROLE_ADMIN")
                .contains("dashboard", "bidding")
                .contains("bidding.manage", "task.review", "retrospective.submit", "warehouse.manage")
                .doesNotContain("task.view.own");
    }

    @Test
    @DisplayName("OSS 用户缓存 roleCode 漂移为 admin（非 DB 规范 /bidAdmin）时 authorities 含 admin 但缺失 /bidAdmin（CO-391 真实根因）")
    void ossUserCacheHitWithDriftedAdminRoleCodeShouldLogAuthoritiesForDiagnosis() {
        // CO-391 真实根因（production 数据已确认）：
        // 06234 郑蓉蓉为 OSS 用户（external_org_source_app=oss），DB roleProfile.code=/bidAdmin，
        // 但 OSS 缓存中 roleCode="admin"（来自 OSS 系统映射，与 DB 不一致）。
        // 走 OSS 缓存命中分支（行 62-67）：
        //   - "admin" 在 LoginRoleWhitelist 白名单内 → 不被拒绝
        //   - shouldSkipLegacyRoleCompat("admin")=false（admin 在 DEFINITIONS case-insensitive map）
        //   - authorities.add("ROLE_MANAGER")（DB user.role=MANAGER）
        //   - authorities.add("admin")（原值）
        //   - authorities.add("ROLE_ADMIN")（toAuthorityName("admin")="ADMIN"，行 106 总是执行）
        //   - authorities.add("ROLE_ADMIN")（legacyRoleForCode("admin")=ADMIN，行 110-111）
        //   - authorities 不含 "/bidAdmin"（规范形式）
        // 结果：旧 Controller 注解 hasAnyAuthority('/bidAdmin', 'bid-TeamLeader') 不匹配 → 403。
        // 修复：Controller 注解加 'admin' 字面字符串兜底覆盖此真实场景。
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

        UserDetails details = userDetailsService.loadUserByUsername("06234");

        // 验证漂移场景下的 authorities 集合形态：
        //   - 含 "admin" 字面字符串（命中 Controller 注解 'admin' 兜底）
        //   - 含 ROLE_ADMIN（toAuthorityName + legacyRoleForCode 双路径生成）
        //   - 含 ROLE_MANAGER（DB user.role=MANAGER，!skipLegacyCompat）
        //   - 不含规范形式 "/bidAdmin"（根因）
        // Controller 注解加 'admin' 兜底后，此场景可访问 /import/template 等端点。
        assertThat(details.getAuthorities())
                .extracting("authority")
                .contains("admin", "ROLE_ADMIN", "ROLE_MANAGER")
                .doesNotContain("/bidAdmin");
    }
}
