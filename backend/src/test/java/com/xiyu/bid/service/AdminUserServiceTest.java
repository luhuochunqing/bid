package com.xiyu.bid.service;

import com.xiyu.bid.admin.service.DataScopeConfigService;
import com.xiyu.bid.service.AdminUserQueryService;
import com.xiyu.bid.admin.settings.core.DepartmentGraphPolicy;
import com.xiyu.bid.crm.application.CrmAuthService;
import com.xiyu.bid.dto.UserOrganizationUpdateRequest;
import com.xiyu.bid.dto.AdminUserDTO;
import com.xiyu.bid.entity.RoleProfile;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RoleProfileService roleProfileService;

    @Mock
    private DataScopeConfigService dataScopeConfigService;
    @Mock
    private AdminUserQueryService adminUserQueryService;

    @Mock
    private CrmAuthService crmAuthService;

    private AdminUserService service;

    @BeforeEach
    void setUp() {
        service = new AdminUserService(userRepository, passwordEncoder, roleProfileService, dataScopeConfigService, adminUserQueryService, crmAuthService);
        org.mockito.Mockito.lenient().when(adminUserQueryService.toDto(any())).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            return AdminUserDTO.builder()
                    .id(u.getId())
                    .username(u.getUsername())
                    .fullName(u.getFullName())
                    .email(u.getEmail())
                    .phone(u.getPhone())
                    .departmentCode(u.getDepartmentCode())
                    .departmentName(u.getDepartmentName())
                    .roleId(u.getRoleProfile() == null ? null : u.getRoleProfile().getId())
                    .roleCode(u.getRoleCode())
                    .roleName(u.getRoleName())
                    .enabled(Boolean.TRUE.equals(u.getEnabled()))
                    .externalOrgUserId(u.getExternalOrgUserId())
                    .build();
        });
    }

    @Test
    void updateOrganization_ShouldResolveDepartmentNameFromDepartmentTree() {
        User user = User.builder().id(7L).username("alice").role(User.Role.MANAGER).enabled(true).build();
        RoleProfile role = RoleProfile.builder().id(3L).code("bid-Team").name("投标专员").enabled(true).build();
        UserOrganizationUpdateRequest request = new UserOrganizationUpdateRequest();
        request.setDepartmentCode("TECH");
        request.setRoleId(3L);
        request.setEnabled(true);

        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(roleProfileService.requireRoleProfile(3L)).thenReturn(role);
        when(dataScopeConfigService.getDepartmentGraph()).thenReturn(DepartmentGraphPolicy.buildGraph(List.of(
                new com.xiyu.bid.admin.settings.core.DepartmentNode("TECH", "技术部", null, 1)
        )));
        when(userRepository.save(user)).thenReturn(user);

        assertThat(service.updateOrganization(7L, request, "admin").getDepartmentName()).isEqualTo("技术部");
    }

    @Test
    void updateOrganization_ShouldRejectUnknownDepartmentForEnabledUser() {
        User user = User.builder().id(7L).username("alice").role(User.Role.MANAGER).enabled(true).build();
        RoleProfile role = RoleProfile.builder().id(3L).code("bid-Team").name("投标专员").enabled(true).build();
        UserOrganizationUpdateRequest request = new UserOrganizationUpdateRequest();
        request.setDepartmentCode("UNKNOWN");
        request.setRoleId(3L);
        request.setEnabled(true);

        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(roleProfileService.requireRoleProfile(3L)).thenReturn(role);
        when(dataScopeConfigService.getDepartmentGraph()).thenReturn(DepartmentGraphPolicy.buildGraph(List.of(
                new com.xiyu.bid.admin.settings.core.DepartmentNode("TECH", "技术部", null, 1)
        )));

        assertThatThrownBy(() -> service.updateOrganization(7L, request, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("有效部门");
    }

    @Test
    void updateStatus_ShouldAllowChangingOssUserEnabledStatus() {
        User user = User.builder()
                .id(7L)
                .username("alice")
                .role(User.Role.MANAGER)
                .enabled(true)
                .externalOrgSourceApp("oss")
                .externalOrgUserId("oss-alice")
                .build();
        com.xiyu.bid.dto.AdminUserStatusUpdateRequest request = new com.xiyu.bid.dto.AdminUserStatusUpdateRequest();
        request.setEnabled(false);

        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        AdminUserDTO dto = service.updateStatus(7L, request, "admin");
        assertThat(dto.getEnabled()).isFalse();
    }

    @Test
    void updateStatus_ShouldAllowChangingLocalUserEnabledStatus() {
        User user = User.builder()
                .id(7L)
                .username("alice")
                .role(User.Role.MANAGER)
                .enabled(true)
                .build();
        com.xiyu.bid.dto.AdminUserStatusUpdateRequest request = new com.xiyu.bid.dto.AdminUserStatusUpdateRequest();
        request.setEnabled(false);

        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        AdminUserDTO dto = service.updateStatus(7L, request, "admin");
        assertThat(dto.getEnabled()).isFalse();
    }

    /**
     * CO-152 Review D5-2 回归：修改 crmSalesNo 后应调用 logoutUser（主动失效），而非 handleUnauthorizedForUser（401 专用）。
     */
    @Test
    void updateUser_CrmSalesNoChanged_ShouldCallLogoutUserNotHandleUnauthorized() {
        User user = User.builder()
                .id(7L)
                .username("alice")
                .fullName("Alice")
                .role(User.Role.MANAGER)
                .enabled(true)
                .crmSalesNo("old-sales-no")
                .build();
        RoleProfile role = RoleProfile.builder().id(3L).code("bid-Team").name("投标专员").enabled(true).build();
        com.xiyu.bid.dto.AdminUserUpdateRequest request = new com.xiyu.bid.dto.AdminUserUpdateRequest();
        request.setUsername("alice");
        request.setEmail("alice@example.com");
        request.setFullName("Alice");
        request.setCrmSalesNo("new-sales-no");
        request.setRoleId(3L);
        request.setEnabled(true);

        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(roleProfileService.requireRoleProfile(3L)).thenReturn(role);
        when(userRepository.save(user)).thenReturn(user);

        service.updateUser(7L, request, "admin");

        // 关键断言：crmSalesNo 变更 → 调用 logoutUser（主动失效），不调用 handleUnauthorizedForUser
        verify(crmAuthService).logoutUser("alice");
        verify(crmAuthService, never()).handleUnauthorizedForUser(any());
    }

    /**
     * CO-152 Review D5-2 回归：crmSalesNo 未变更时不触发任何缓存失效。
     */
    @Test
    void updateUser_CrmSalesNoUnchanged_ShouldNotInvalidateCache() {
        User user = User.builder()
                .id(7L)
                .username("alice")
                .fullName("Alice")
                .role(User.Role.MANAGER)
                .enabled(true)
                .crmSalesNo("same-sales-no")
                .build();
        RoleProfile role = RoleProfile.builder().id(3L).code("bid-Team").name("投标专员").enabled(true).build();
        com.xiyu.bid.dto.AdminUserUpdateRequest request = new com.xiyu.bid.dto.AdminUserUpdateRequest();
        request.setUsername("alice");
        request.setEmail("alice@example.com");
        request.setFullName("Alice");
        request.setCrmSalesNo("same-sales-no");
        request.setRoleId(3L);
        request.setEnabled(true);

        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(roleProfileService.requireRoleProfile(3L)).thenReturn(role);
        when(userRepository.save(user)).thenReturn(user);

        service.updateUser(7L, request, "admin");

        // 关键断言：crmSalesNo 未变 → 不触发任何缓存失效
        verify(crmAuthService, never()).logoutUser(any());
        verify(crmAuthService, never()).handleUnauthorizedForUser(any());
    }
}
