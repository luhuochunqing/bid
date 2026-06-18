package com.xiyu.bid.service;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.integration.organization.infrastructure.persistence.entity.OrganizationDepartmentEntity;
import com.xiyu.bid.integration.organization.infrastructure.persistence.repository.OrganizationDepartmentRepository;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserQueryServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganizationDepartmentRepository departmentRepository;

    private AdminUserQueryService service;

    @BeforeEach
    void setUp() {
        service = new AdminUserQueryService(userRepository, departmentRepository);
    }

    @Test
    void listUsersPage_filtersByExternalDeptId_whenDepartmentCodeGiven() {
        OrganizationDepartmentEntity dept = new OrganizationDepartmentEntity();
        dept.setDepartmentCode("0001");
        dept.setExternalDeptId("997091");
        dept.setSourceApp("ehsy");

        User matchingUser = User.builder().username("u1").departmentCode("997091").enabled(true).role(User.Role.STAFF).build();
        User nonMatchingUser = User.builder().username("u2").departmentCode("12345").enabled(true).role(User.Role.STAFF).build();

        when(departmentRepository.findBySourceAppAndDepartmentCode("ehsy", "0001"))
                .thenReturn(Optional.of(dept));
        when(userRepository.findAll()).thenReturn(List.of(matchingUser, nonMatchingUser));

        PaginatedResult<com.xiyu.bid.dto.AdminUserDTO> result =
                service.listUsersPage(1, 10, null, null, "0001", "ehsy");

        assertThat(result.list()).hasSize(1);
        assertThat(result.list().get(0).getUsername()).isEqualTo("u1");
        assertThat(result.totalCount()).isEqualTo(1);
    }

    @Test
    void listUsersPage_returnsEmpty_whenDepartmentCodeHasNoMapping() {
        when(departmentRepository.findBySourceAppAndDepartmentCode("ehsy", "0001"))
                .thenReturn(Optional.empty());

        PaginatedResult<com.xiyu.bid.dto.AdminUserDTO> result =
                service.listUsersPage(1, 10, null, null, "0001", "ehsy");

        assertThat(result.list()).isEmpty();
        assertThat(result.totalCount()).isEqualTo(0);
    }

    @Test
    void listUsersPage_filtersByDescendantExternalDeptIds_whenParentDepartmentClicked() {
        OrganizationDepartmentEntity parent = new OrganizationDepartmentEntity();
        parent.setDepartmentCode("0001");
        parent.setExternalDeptId("997091");
        parent.setSourceApp("ehsy");
        parent.setParentDepartmentCode("rootorg");

        OrganizationDepartmentEntity child = new OrganizationDepartmentEntity();
        child.setDepartmentCode("0002");
        child.setExternalDeptId("3084983");
        child.setSourceApp("ehsy");
        child.setParentDepartmentCode("0001");

        User parentUser = User.builder().username("u1").departmentCode("997091").enabled(true).role(User.Role.STAFF).build();
        User childUser = User.builder().username("u2").departmentCode("3084983").enabled(true).role(User.Role.STAFF).build();
        User otherUser = User.builder().username("u3").departmentCode("12345").enabled(true).role(User.Role.STAFF).build();

        when(departmentRepository.findBySourceAppAndDepartmentCode("ehsy", "0001"))
                .thenReturn(Optional.of(parent));
        when(departmentRepository.findBySourceAppAndEnabledTrueOrderByDepartmentCode("ehsy"))
                .thenReturn(List.of(parent, child));
        when(userRepository.findAll()).thenReturn(List.of(parentUser, childUser, otherUser));

        PaginatedResult<com.xiyu.bid.dto.AdminUserDTO> result =
                service.listUsersPage(1, 10, null, null, "0001", "ehsy");

        assertThat(result.list()).hasSize(2);
        assertThat(result.list().stream().map(com.xiyu.bid.dto.AdminUserDTO::getUsername))
                .containsExactlyInAnyOrder("u1", "u2");
        assertThat(result.totalCount()).isEqualTo(2);
    }

    @Test
    void listUsersPage_ignoresSourceApp_whenDepartmentCodeNotGiven() {
        User enabledUser = User.builder().username("enabled-user").enabled(true).role(User.Role.STAFF).build();
        User disabledUser = User.builder().username("disabled-user").enabled(false).role(User.Role.STAFF).build();

        when(userRepository.findAll()).thenReturn(List.of(enabledUser, disabledUser));

        PaginatedResult<com.xiyu.bid.dto.AdminUserDTO> noSourceApp =
                service.listUsersPage(1, 10, null, true, null, null);
        PaginatedResult<com.xiyu.bid.dto.AdminUserDTO> withEhsy =
                service.listUsersPage(1, 10, null, true, null, "ehsy");
        PaginatedResult<com.xiyu.bid.dto.AdminUserDTO> withOss =
                service.listUsersPage(1, 10, null, true, null, "oss");

        assertThat(noSourceApp.list()).hasSize(1);
        assertThat(withEhsy.list()).hasSize(1);
        assertThat(withOss.list()).hasSize(1);
        assertThat(noSourceApp.totalCount()).isEqualTo(1);
        assertThat(withEhsy.totalCount()).isEqualTo(1);
        assertThat(withOss.totalCount()).isEqualTo(1);
        assertThat(noSourceApp.list().get(0).getUsername()).isEqualTo("enabled-user");
    }
}
