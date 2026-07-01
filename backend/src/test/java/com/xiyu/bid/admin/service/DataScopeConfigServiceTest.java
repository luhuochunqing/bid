package com.xiyu.bid.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.crm.application.OssPermissionCache;
import com.xiyu.bid.dto.DataScopeConfigResponse;
import com.xiyu.bid.entity.RoleProfile;
import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.RoleProfileRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.roleprofile.RoleProfileBootstrap;
import com.xiyu.bid.settings.entity.SystemSetting;
import com.xiyu.bid.settings.repository.SystemSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionAttribute;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataScopeConfigServiceTest {

    @Mock
    private SystemSettingRepository systemSettingRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleProfileRepository roleProfileRepository;

    @Mock
    private RoleProfileBootstrap roleProfileBootstrap;

    private DataScopeConfigService dataScopeConfigService;

    @BeforeEach
    void setUp() {
        OssPermissionCache ossPermissionCache = new OssPermissionCache();
        dataScopeConfigService = new DataScopeConfigService(systemSettingRepository, userRepository, roleProfileRepository, roleProfileBootstrap, new ObjectMapper(), ossPermissionCache);
        org.mockito.Mockito.lenient().when(roleProfileRepository.findByCodeIgnoreCase(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
        org.mockito.Mockito.lenient().when(roleProfileRepository.findAll()).thenReturn(List.of());
    }

    @Test
    void getConfig_ShouldMergeStoredRulesWithCurrentUsers() {
        User salesUser = User.builder()
                .id(1L)
                .username("alice")
                .fullName("Alice")
                .role(User.Role.MANAGER)
                .departmentCode("SALES")
                .departmentName("销售部")
                .enabled(true)
                .build();
        String payloadJson = """
                {
                  "departmentTree": [
                    {
                      "departmentCode": "SALES",
                      "departmentName": "销售部",
                      "parentDepartmentCode": null,
                      "sortOrder": 1
                    },
                    {
                      "departmentCode": "TECH",
                      "departmentName": "技术部",
                      "parentDepartmentCode": "SALES",
                      "sortOrder": 2
                    }
                  ],
                  "userRules": [
                    {
                      "userId": 1,
                      "dataScope": "dept",
                      "allowedProjectIds": [101, 102],
                      "allowedDeptCodes": ["TECH"]
                    }
                  ],
                  "departmentRules": [
                    {
                      "departmentCode": "SALES",
                      "dataScope": "dept",
                      "canViewOtherDepts": true,
                      "allowedDeptCodes": ["TECH"]
                    }
                  ]
                }
                """;

        when(userRepository.findAll()).thenReturn(List.of(salesUser));
        when(systemSettingRepository.findByConfigKey(DataScopeConfigService.DATA_SCOPE_CONFIG_KEY))
                .thenReturn(Optional.of(SystemSetting.builder().configKey(DataScopeConfigService.DATA_SCOPE_CONFIG_KEY).payloadJson(payloadJson).build()));

        DataScopeConfigResponse response = dataScopeConfigService.getConfig();

        assertThat(response.getUserDataScope()).hasSize(1);
        assertThat(response.getUserDataScope().get(0).getAllowedProjects()).containsExactly(101L, 102L);
        assertThat(response.getUserDataScope().get(0).getAllowedDepts()).containsExactly("TECH");
        assertThat(response.getDeptDataScope()).hasSize(2);
        assertThat(response.getDeptDataScope().get(0).isCanViewOtherDepts()).isTrue();
        assertThat(response.getDeptTree()).hasSize(2);
    }

    @Test
    void getConfig_ShouldEnsureSystemRolesInsideAdminBoundary() {
        when(userRepository.findAll()).thenReturn(List.of());
        when(systemSettingRepository.findByConfigKey(DataScopeConfigService.DATA_SCOPE_CONFIG_KEY))
                .thenReturn(Optional.empty());

        DataScopeConfigResponse response = dataScopeConfigService.getConfig();

        assertThat(response.getRoles()).isEmpty();
        verify(roleProfileBootstrap).ensureSystemRoles();
    }

    @Test
    void getConfig_ShouldUseWritableTransactionBecauseRoleBootstrapMayInsertRoles() throws Exception {
        Method method = DataScopeConfigService.class.getMethod("getConfig");
        TransactionAttribute attribute = new AnnotationTransactionAttributeSource()
                .getTransactionAttribute(method, DataScopeConfigService.class);

        assertThat(attribute).isNotNull();
        assertThat(attribute.isReadOnly()).isFalse();
    }

    @Test
    void getAccessProfile_ShouldPreferUserRuleOverDepartmentRule() {
        User salesUser = User.builder()
                .id(1L)
                .username("alice")
                .fullName("Alice")
                .role(User.Role.MANAGER)
                .departmentCode("SALES")
                .departmentName("销售部")
                .enabled(true)
                .build();
        String payloadJson = """
                {
                  "departmentTree": [
                    {
                      "departmentCode": "SALES",
                      "departmentName": "销售部",
                      "parentDepartmentCode": null,
                      "sortOrder": 1
                    }
                  ],
                  "userRules": [
                    {
                      "userId": 1,
                      "dataScope": "self",
                      "allowedProjectIds": [9],
                      "allowedDeptCodes": ["FINANCE"]
                    }
                  ],
                  "departmentRules": [
                    {
                      "departmentCode": "SALES",
                      "dataScope": "dept",
                      "canViewOtherDepts": true,
                      "allowedDeptCodes": ["TECH"]
                    }
                  ]
                }
                """;

        when(userRepository.findAll()).thenReturn(List.of(salesUser));
        when(systemSettingRepository.findByConfigKey(DataScopeConfigService.DATA_SCOPE_CONFIG_KEY))
                .thenReturn(Optional.of(SystemSetting.builder().configKey(DataScopeConfigService.DATA_SCOPE_CONFIG_KEY).payloadJson(payloadJson).build()));

        DataScopeAccessProfile profile = dataScopeConfigService.getAccessProfile(salesUser);

        assertThat(profile.getDataScope()).isEqualTo("self");
        assertThat(profile.getExplicitProjectIds()).containsExactly(9L);
        assertThat(profile.getAllowedDepartmentCodes()).isEmpty();
    }

    @Test
    void getAccessProfile_ShouldExpandDescendantsForDeptAndSub() {
        User managerUser = User.builder()
                .id(2L)
                .username("manager")
                .fullName("Manager")
                .role(User.Role.MANAGER)
                .departmentCode("SALES")
                .departmentName("销售部")
                .enabled(true)
                .build();
        String payloadJson = """
                {
                  "departmentTree": [
                    {
                      "departmentCode": "SALES",
                      "departmentName": "销售部",
                      "parentDepartmentCode": null,
                      "sortOrder": 1
                    },
                    {
                      "departmentCode": "SALES_EAST",
                      "departmentName": "华东销售部",
                      "parentDepartmentCode": "SALES",
                      "sortOrder": 2
                    }
                  ],
                  "userRules": [],
                  "departmentRules": [
                    {
                      "departmentCode": "SALES",
                      "dataScope": "deptAndSub",
                      "canViewOtherDepts": false,
                      "allowedDeptCodes": []
                    }
                  ]
                }
                """;

        when(userRepository.findAll()).thenReturn(List.of(managerUser));
        when(systemSettingRepository.findByConfigKey(DataScopeConfigService.DATA_SCOPE_CONFIG_KEY))
                .thenReturn(Optional.of(SystemSetting.builder().configKey(DataScopeConfigService.DATA_SCOPE_CONFIG_KEY).payloadJson(payloadJson).build()));

        DataScopeAccessProfile profile = dataScopeConfigService.getAccessProfile(managerUser);

        assertThat(profile.getAllowedDepartmentCodes()).containsExactly("SALES", "SALES_EAST");
    }

    @Test
    void saveConfig_ShouldPersistNormalizedRules() {
        User salesUser = User.builder()
                .id(1L)
                .username("alice")
                .fullName("Alice")
                .role(User.Role.MANAGER)
                .departmentCode("SALES")
                .departmentName("销售部")
                .enabled(true)
                .build();
        DataScopeConfigResponse request = DataScopeConfigResponse.builder()
                .userDataScope(List.of(DataScopeConfigResponse.UserDataScopeItem.builder()
                        .userId(1L)
                        .dataScope("dept")
                        .allowedProjects(List.of(100L, 99L, 99L))
                        .allowedDepts(List.of("TECH", "TECH"))
                        .build()))
                .deptDataScope(List.of(DataScopeConfigResponse.DepartmentDataScopeItem.builder()
                        .deptCode("SALES")
                        .dataScope("dept")
                        .canViewOtherDepts(true)
                        .allowedDepts(List.of("TECH"))
                        .build()))
                .deptTree(List.of(
                        DataScopeConfigResponse.DepartmentTreeItem.builder()
                                .deptCode("SALES")
                                .deptName("销售部")
                                .sortOrder(1)
                                .build(),
                        DataScopeConfigResponse.DepartmentTreeItem.builder()
                                .deptCode("TECH")
                                .deptName("技术部")
                                .parentDeptCode("SALES")
                                .sortOrder(2)
                                .build()))
                .build();
        AtomicReference<String> savedPayload = new AtomicReference<>();

        when(userRepository.findAll()).thenReturn(List.of(salesUser));
        when(systemSettingRepository.findByConfigKey(DataScopeConfigService.DATA_SCOPE_CONFIG_KEY))
                .thenAnswer(invocation -> savedPayload.get() == null
                        ? Optional.empty()
                        : Optional.of(SystemSetting.builder()
                                .configKey(DataScopeConfigService.DATA_SCOPE_CONFIG_KEY)
                                .payloadJson(savedPayload.get())
                                .build()));
        when(systemSettingRepository.save(any(SystemSetting.class))).thenAnswer(invocation -> {
            SystemSetting setting = invocation.getArgument(0);
            savedPayload.set(setting.getPayloadJson());
            return setting;
        });

        DataScopeConfigResponse response = dataScopeConfigService.saveConfig(request);

        assertThat(response.getUserDataScope().get(0).getAllowedProjects()).containsExactly(99L, 100L);
        assertThat(response.getDeptDataScope().get(0).getAllowedDepts()).containsExactly("TECH");
        assertThat(response.getDeptTree()).hasSize(2);
    }

    @Test
    void getRoleMenuPermissions_ShouldMergeOssPermissionsWithDbRoleProfile() {
        // CO-438: OSS 缓存命中时，合并 DB RoleProfile 的管理权限点
        // 原因：OSS menuCode→权限码映射表不含 performance.manage/warehouse.manage/personnel.manage
        // 这些写操作权限点只存在于 DB RoleProfile，OSS 用户若不合并将看不到管理按钮
        User user = User.builder()
                .id(9L).username("06234").fullName("郑蓉蓉")
                .role(User.Role.MANAGER)
                .enabled(true)
                .build();
        OssPermissionCache ossPermissionCache = new OssPermissionCache();
        ossPermissionCache.put(user.getUsername(), RoleProfileCatalog.BID_ADMIN_CODE,
                List.of("project", "project-detail"), null);
        dataScopeConfigService = new DataScopeConfigService(systemSettingRepository, userRepository, roleProfileRepository,
                roleProfileBootstrap, new ObjectMapper(), ossPermissionCache);

        List<String> perms = dataScopeConfigService.getRoleMenuPermissions(user);

        // OSS 权限 + DB RoleProfile 管理权限点合并
        assertThat(perms)
                .contains("project", "project-detail")
                .contains(RoleProfileCatalog.PERFORMANCE_MANAGE_PERMISSION,
                        RoleProfileCatalog.WAREHOUSE_MANAGE_PERMISSION,
                        RoleProfileCatalog.PERSONNEL_MANAGE_PERMISSION);
    }

    @Test
    void getRoleMenuPermissions_ShouldReturnEmptyForUnregisteredRoleCode() {
        // 未注册 roleCode（DB 无 + catalog 无）不应 fallback staff，避免前端菜单越权可见标讯/项目/知识库
        RoleProfile vendorProfile = RoleProfile.builder().code("vendor-user").name("vendor-user").build();
        User user = User.builder()
                .id(1L).username("vendor").fullName("vendor")
                .role(User.Role.MANAGER)
                .roleProfile(vendorProfile)
                .enabled(true)
                .build();
        // findByCodeIgnoreCase 默认 mock 返回 empty（@BeforeEach）→ 走未注册 placeholder 分支

        List<String> perms = dataScopeConfigService.getRoleMenuPermissions(user);

        assertThat(perms).isEmpty();
    }

    @Test
    void getRoleMenuPermissions_ShouldUseDbTaskBoardPermissionForBidOtherDept() {
        // 新行为：只从 OSS 缓存读取，不 fallback 到本地 DB
        // 缓存为空时返回空列表（即使本地 DB 有 task-board 权限）
        User user = User.builder()
                .id(2L).username("hanhui").fullName("hanhui")
                .role(User.Role.MANAGER)
                .enabled(true)
                .build();

        List<String> perms = dataScopeConfigService.getRoleMenuPermissions(user);

        // 缓存为空，返回空列表，不 fallback 到本地 DB
        assertThat(perms).isEmpty();
    }

    @Test
    void getRoleMenuPermissions_ShouldFallbackToStaffForPureLegacyUser() {
        // 新行为：纯 Legacy 用户也只从 OSS 缓存读取，缓存为空时返回空列表
        User user = User.builder()
                .id(3L).username("legacy").fullName("legacy")
                .role(User.Role.MANAGER)
                .enabled(true)
                .build();

        List<String> perms = dataScopeConfigService.getRoleMenuPermissions(user);

        // 缓存为空，返回空列表
        assertThat(perms).isEmpty();
    }

    @Test
    @DisplayName("OSS 用户 cache miss 时 getRoleCode 返回 null，不 fallback 到 DB 的 /bidAdmin")
    void getRoleCode_ShouldReturnNullForOssUserCacheMiss() {
        RoleProfile bidAdminProfile = RoleProfile.builder()
                .code(RoleProfileCatalog.BID_ADMIN_CODE).name("投标管理员").build();
        User ossUser = User.builder()
                .id(10L)
                .username("oss-user-01")
                .fullName("OSS用户")
                .role(User.Role.MANAGER)
                .roleProfile(bidAdminProfile)
                .externalOrgSourceApp("oss-app")
                .enabled(true)
                .build();

        String roleCode = dataScopeConfigService.getRoleCode(ossUser);

        assertThat(roleCode).isNull();
    }

    @Test
    @DisplayName("OSS 用户 cache miss 时 getRoleName 返回 null，不 fallback 到 DB 角色名或默认'员工'")
    void getRoleName_ShouldReturnNullForOssUserCacheMiss() {
        RoleProfile bidAdminProfile = RoleProfile.builder()
                .code(RoleProfileCatalog.BID_ADMIN_CODE).name("投标管理员").build();
        User ossUser = User.builder()
                .id(11L)
                .username("oss-user-02")
                .fullName("OSS用户2")
                .role(User.Role.MANAGER)
                .roleProfile(bidAdminProfile)
                .externalOrgSourceApp("oss-app")
                .enabled(true)
                .build();

        String roleName = dataScopeConfigService.getRoleName(ossUser);

        assertThat(roleName).isNull();
    }

    @Test
    @DisplayName("OSS 用户 cache hit 时 getRoleCode 正常返回缓存值")
    void getRoleCode_ShouldReturnCachedValueForOssUserCacheHit() {
        User ossUser = User.builder()
                .id(12L)
                .username("oss-user-03")
                .fullName("OSS用户3")
                .role(User.Role.MANAGER)
                .externalOrgSourceApp("oss-app")
                .enabled(true)
                .build();
        OssPermissionCache ossPermissionCache = new OssPermissionCache();
        ossPermissionCache.put(ossUser.getUsername(), RoleProfileCatalog.BID_ADMIN_CODE, List.of(), null);
        dataScopeConfigService = new DataScopeConfigService(systemSettingRepository, userRepository, roleProfileRepository,
                roleProfileBootstrap, new ObjectMapper(), ossPermissionCache);

        String roleCode = dataScopeConfigService.getRoleCode(ossUser);

        assertThat(roleCode).isEqualTo(RoleProfileCatalog.BID_ADMIN_CODE);
    }

    @Test
    @DisplayName("本地系统账号 cache miss 时 getRoleCode fallback 到 DB roleCode")
    void getRoleCode_ShouldFallbackToDbForLocalSystemAccountCacheMiss() {
        RoleProfile adminProfile = RoleProfile.builder()
                .code(RoleProfileCatalog.ADMIN_CODE).name("管理员").build();
        User adminUser = User.builder()
                .id(13L)
                .username("admin")
                .fullName("管理员")
                .role(User.Role.ADMIN)
                .roleProfile(adminProfile)
                .externalOrgSourceApp(null)
                .enabled(true)
                .build();

        String roleCode = dataScopeConfigService.getRoleCode(adminUser);

        assertThat(roleCode).isEqualTo(RoleProfileCatalog.ADMIN_CODE);
    }

    @Test
    @DisplayName("OSS 用户 cache hit 时 getRoleMenuPermissions 合并缓存权限与 DB RoleProfile 权限点")
    void getRoleMenuPermissions_ShouldReturnCachedPermissionsWhenCacheHit() {
        User user = User.builder()
                .id(14L).username("cached-user").fullName("Cached User")
                .role(User.Role.MANAGER)
                .externalOrgSourceApp("oss-app")
                .enabled(true)
                .build();
        OssPermissionCache ossPermissionCache = new OssPermissionCache();
        ossPermissionCache.put("cached-user", RoleProfileCatalog.BID_SPECIALIST_CODE, List.of("dashboard", "project-list"), null);
        DataScopeConfigService serviceWithCache = new DataScopeConfigService(
                systemSettingRepository, userRepository, roleProfileRepository,
                roleProfileBootstrap, new ObjectMapper(), ossPermissionCache);

        List<String> perms = serviceWithCache.getRoleMenuPermissions(user);

        // CO-438: 合并 OSS 缓存权限 + DB RoleProfile 管理权限点
        assertThat(perms).contains("dashboard", "project-list");
        assertThat(perms).contains(RoleProfileCatalog.PERFORMANCE_MANAGE_PERMISSION,
                RoleProfileCatalog.WAREHOUSE_MANAGE_PERMISSION,
                RoleProfileCatalog.PERSONNEL_MANAGE_PERMISSION);
    }

    @Test
    @DisplayName("OSS 用户 cache hit 时 getRoleName 返回缓存角色对应的角色名")
    void getRoleName_ShouldReturnCachedRoleNameWhenCacheHit() {
        User user = User.builder()
                .id(15L).username("cached-role-name").fullName("Cached Role Name User")
                .role(User.Role.MANAGER)
                .externalOrgSourceApp("oss-app")
                .enabled(true)
                .build();
        OssPermissionCache ossPermissionCache = new OssPermissionCache();
        ossPermissionCache.put("cached-role-name", RoleProfileCatalog.BID_ADMIN_CODE, List.of(), null);
        DataScopeConfigService serviceWithCache = new DataScopeConfigService(
                systemSettingRepository, userRepository, roleProfileRepository,
                roleProfileBootstrap, new ObjectMapper(), ossPermissionCache);

        String roleName = serviceWithCache.getRoleName(user);

        assertThat(roleName).isEqualTo("投标管理员");
    }

    @Test
    @DisplayName("getRoleMenuPermissions 对 null 用户返回空列表")
    void getRoleMenuPermissions_ShouldReturnEmptyForNullUser() {
        List<String> perms = dataScopeConfigService.getRoleMenuPermissions(null);
        assertThat(perms).isEmpty();
    }

    @Test
    @DisplayName("getRoleCode 对 null 用户返回 null")
    void getRoleCode_ShouldReturnNullForNullUser() {
        String roleCode = dataScopeConfigService.getRoleCode(null);
        assertThat(roleCode).isNull();
    }

    @Test
    @DisplayName("getRoleName 对 null 用户返回 默认'员工'")
    void getRoleName_ShouldReturnEmployeeForNullUser() {
        String roleName = dataScopeConfigService.getRoleName(null);
        assertThat(roleName).isEqualTo("员工");
    }

    @Test
    @DisplayName("本地系统账号 cache miss 时 getRoleName fallback 到 DB 角色名")
    void getRoleName_ShouldFallbackToDbForLocalSystemAccountCacheMiss() {
        RoleProfile adminProfile = RoleProfile.builder()
                .code(RoleProfileCatalog.ADMIN_CODE).name("系统管理员").build();
        User adminUser = User.builder()
                .id(16L)
                .username("sysadmin")
                .fullName("系统管理员")
                .role(User.Role.ADMIN)
                .roleProfile(adminProfile)
                .externalOrgSourceApp(null)
                .enabled(true)
                .build();
        when(roleProfileRepository.findByCodeIgnoreCase(RoleProfileCatalog.ADMIN_CODE))
                .thenReturn(Optional.of(adminProfile));

        String roleName = dataScopeConfigService.getRoleName(adminUser);

        assertThat(roleName).isEqualTo("系统管理员");
    }

    @Test
    @DisplayName("本地系统账号 cache miss 时 getRoleMenuPermissions fallback 到 DB 权限")
    void getRoleMenuPermissions_ShouldFallbackToDbForLocalSystemAccountCacheMiss() {
        RoleProfile adminProfile = RoleProfile.builder()
                .code(RoleProfileCatalog.ADMIN_CODE)
                .name("管理员")
                .build();
        adminProfile.setMenuPermissions(List.of("dashboard", "settings", "audit"));
        User adminUser = User.builder()
                .id(17L)
                .username("local-admin")
                .fullName("本地管理员")
                .role(User.Role.ADMIN)
                .roleProfile(adminProfile)
                .externalOrgSourceApp(null)
                .enabled(true)
                .build();
        when(roleProfileRepository.findByCodeIgnoreCase(RoleProfileCatalog.ADMIN_CODE))
                .thenReturn(Optional.of(adminProfile));

        List<String> perms = dataScopeConfigService.getRoleMenuPermissions(adminUser);

        assertThat(perms).containsExactlyInAnyOrder("dashboard", "settings", "audit");
    }
}
