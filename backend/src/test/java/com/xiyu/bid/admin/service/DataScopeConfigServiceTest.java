package com.xiyu.bid.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.dto.DataScopeConfigResponse;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.RoleProfileRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.roleprofile.RoleProfileBootstrap;
import com.xiyu.bid.settings.entity.SystemSetting;
import com.xiyu.bid.settings.repository.SystemSettingRepository;
import org.junit.jupiter.api.BeforeEach;
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
        dataScopeConfigService = new DataScopeConfigService(systemSettingRepository, userRepository, roleProfileRepository, roleProfileBootstrap, new ObjectMapper());
        org.mockito.Mockito.lenient().when(roleProfileRepository.findByCodeIgnoreCase(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
        org.mockito.Mockito.lenient().when(roleProfileRepository.findAll()).thenReturn(List.of());
    }

    @Test
    void getConfig_ShouldMergeStoredRulesWithCurrentUsers() {
        User salesUser = User.builder()
                .id(1L)
                .username("alice")
                .fullName("Alice")
                .role(User.Role.STAFF)
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
                .role(User.Role.STAFF)
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

        DataScopeConfigService.AccessProfile profile = dataScopeConfigService.getAccessProfile(salesUser);

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

        DataScopeConfigService.AccessProfile profile = dataScopeConfigService.getAccessProfile(managerUser);

        assertThat(profile.getAllowedDepartmentCodes()).containsExactly("SALES", "SALES_EAST");
    }

    @Test
    void saveConfig_ShouldPersistNormalizedRules() {
        User salesUser = User.builder()
                .id(1L)
                .username("alice")
                .fullName("Alice")
                .role(User.Role.STAFF)
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
}
