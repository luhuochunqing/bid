package com.xiyu.bid.service;

import com.xiyu.bid.admin.service.DataScopeConfigService;
import com.xiyu.bid.admin.service.ProjectGroupService;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.matrixcollaboration.repository.CrmCustomerPermissionRepository;
import com.xiyu.bid.matrixcollaboration.repository.ProjectMemberRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectAccessScopeServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private DataScopeConfigService dataScopeConfigService;

    @Mock
    private ProjectGroupService projectGroupService;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private CrmCustomerPermissionRepository crmCustomerPermissionRepository;

    private ProjectAccessScopeService projectAccessScopeService;

    @BeforeEach
    void setUp() {
        projectAccessScopeService = new ProjectAccessScopeService(userRepository, projectRepository, dataScopeConfigService, projectGroupService, projectMemberRepository, crmCustomerPermissionRepository);
        SecurityContextHolder.clearContext();
    }

    @Test
    void getAllowedProjectIds_ShouldReturnSortedIdsForNonAdminUser() {
        User user = User.builder()
                .id(601L)
                .username("staff-user")
                .role(User.Role.STAFF)
                .enabled(true)
                .build();

        when(dataScopeConfigService.getAccessProfile(user)).thenReturn(DataScopeConfigService.AccessProfile.builder()
                .dataScope("self")
                .build());
        when(projectRepository.findAccessibleProjectIdsByUserId(601L)).thenReturn(List.of(9L, 3L, 5L));
        when(projectGroupService.getGrantedProjectIds(user)).thenReturn(List.of());
        when(projectMemberRepository.findByUserId(anyLong())).thenReturn(List.of());
        when(crmCustomerPermissionRepository.findByUserId(anyLong())).thenReturn(List.of());

        assertThat(projectAccessScopeService.getAllowedProjectIds(user)).containsExactly(3L, 5L, 9L);
    }

    @Test
    void getAllowedProjectIds_ShouldMergeDepartmentGrantedProjects() {
        User user = User.builder()
                .id(602L)
                .username("dept-user")
                .role(User.Role.STAFF)
                .enabled(true)
                .build();

        when(dataScopeConfigService.getAccessProfile(user)).thenReturn(DataScopeConfigService.AccessProfile.builder()
                .dataScope("dept")
                .allowedDepartmentCodes(List.of("TECH"))
                .explicitProjectIds(List.of(6L))
                .build());
        when(projectRepository.findAccessibleProjectIdsByUserId(602L)).thenReturn(List.of(3L));
        when(projectRepository.findAccessibleProjectIdsByDepartmentCodes(List.of("TECH"))).thenReturn(List.of(8L, 6L));
        when(projectGroupService.getGrantedProjectIds(user)).thenReturn(List.of(10L));
        when(projectMemberRepository.findByUserId(anyLong())).thenReturn(List.of());
        when(crmCustomerPermissionRepository.findByUserId(anyLong())).thenReturn(List.of());

        assertThat(projectAccessScopeService.getAllowedProjectIds(user)).containsExactly(3L, 6L, 8L, 10L);
    }

    @Test
    void filterAccessibleProjects_ShouldKeepOnlyVisibleProjectsForCurrentUser() {
        User user = User.builder()
                .id(601L)
                .username("staff-user")
                .role(User.Role.STAFF)
                .enabled(true)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("staff-user", "N/A", List.of())
        );

        when(userRepository.findByUsername("staff-user")).thenReturn(Optional.of(user));
        when(dataScopeConfigService.getAccessProfile(user)).thenReturn(DataScopeConfigService.AccessProfile.builder()
                .dataScope("self")
                .build());
        when(projectRepository.findAccessibleProjectIdsByUserId(601L)).thenReturn(List.of(1L));
        when(projectGroupService.getGrantedProjectIds(user)).thenReturn(List.of());
        when(projectMemberRepository.findByUserId(anyLong())).thenReturn(List.of());
        when(crmCustomerPermissionRepository.findByUserId(anyLong())).thenReturn(List.of());

        List<Project> filtered = projectAccessScopeService.filterAccessibleProjects(List.of(
                Project.builder().id(1L).name("可见项目").build(),
                Project.builder().id(2L).name("不可见项目").build()
        ));

        assertThat(filtered).extracting(Project::getId).containsExactly(1L);
    }

    @Test
    void assertCurrentUserCanAccessProject_ShouldRejectUnauthorizedProject() {
        User user = User.builder()
                .id(701L)
                .username("outsider-user")
                .role(User.Role.STAFF)
                .enabled(true)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("outsider-user", "N/A", List.of())
        );

        when(userRepository.findByUsername("outsider-user")).thenReturn(Optional.of(user));
        when(dataScopeConfigService.getAccessProfile(user)).thenReturn(DataScopeConfigService.AccessProfile.builder()
                .dataScope("self")
                .build());
        when(projectRepository.findAccessibleProjectIdsByUserId(701L)).thenReturn(List.of());
        when(projectGroupService.getGrantedProjectIds(user)).thenReturn(List.of());
        when(projectMemberRepository.findByUserId(anyLong())).thenReturn(List.of());
        when(crmCustomerPermissionRepository.findByUserId(anyLong())).thenReturn(List.of());

        assertThatThrownBy(() -> projectAccessScopeService.assertCurrentUserCanAccessProject(12L))
                .isInstanceOf(AccessDeniedException.class);
    }
}
