package com.xiyu.bid.service;

import com.xiyu.bid.admin.service.DataScopeConfigService;
import com.xiyu.bid.service.AdminUserQueryService;
import com.xiyu.bid.admin.settings.core.DepartmentGraphPolicy;
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

    private AdminUserService service;

    @BeforeEach
    void setUp() {
        service = new AdminUserService(userRepository, passwordEncoder, roleProfileService, dataScopeConfigService, adminUserQueryService);
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
        User user = User.builder().id(7L).username("alice").role(User.Role.STAFF).enabled(true).build();
        RoleProfile role = RoleProfile.builder().id(3L).code("staff").name("员工").enabled(true).build();
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
        User user = User.builder().id(7L).username("alice").role(User.Role.STAFF).enabled(true).build();
        RoleProfile role = RoleProfile.builder().id(3L).code("staff").name("员工").enabled(true).build();
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
}
