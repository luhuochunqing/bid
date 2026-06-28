package com.xiyu.bid.service;

import com.xiyu.bid.admin.service.DataScopeAccessProfile;
import com.xiyu.bid.admin.service.DataScopeConfigService;
import com.xiyu.bid.admin.service.ProjectGroupService;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.RoleProfile;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.matrixcollaboration.repository.CrmCustomerPermissionRepository;
import com.xiyu.bid.matrixcollaboration.repository.ProjectMemberRepository;
import com.xiyu.bid.project.entity.BidDocumentReviewEntity;
import com.xiyu.bid.project.repository.BidDocumentReviewRepository;
import com.xiyu.bid.project.repository.ProjectInitiationDetailsRepository;
import com.xiyu.bid.project.repository.ProjectLeadAssignmentRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TaskRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.security.EffectiveRoleResolver;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
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

    @Mock
    private ProjectLeadAssignmentRepository leadAssignmentRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private BidDocumentReviewRepository bidDocumentReviewRepository;

    @Mock
    private ProjectInitiationDetailsRepository initiationDetailsRepository;

    @Mock
    private EffectiveRoleResolver effectiveRoleResolver;

    private ProjectAccessScopeService projectAccessScopeService;

    @BeforeEach
    void setUp() {
        projectAccessScopeService = new ProjectAccessScopeService(userRepository, projectRepository, dataScopeConfigService, projectGroupService, projectMemberRepository, crmCustomerPermissionRepository, leadAssignmentRepository, initiationDetailsRepository, taskRepository, bidDocumentReviewRepository, effectiveRoleResolver);
        // CO-373：默认模拟 LOCAL_USER 解析路径——回退到实体 roleCode
        lenient().when(effectiveRoleResolver.resolveRoleCode(any(User.class)))
                .thenAnswer(inv -> inv.<User>getArgument(0).getRoleCode());
        SecurityContextHolder.clearContext();
    }

    @Test
    void getAllowedProjectIds_ShouldReturnSortedIdsForNonAdminUser() {
        User user = User.builder()
                .id(601L)
                .username("staff-user")
                .role(User.Role.MANAGER)
                .enabled(true)
                .build();

        when(dataScopeConfigService.getAccessProfile(user)).thenReturn(DataScopeAccessProfile.builder()
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
                .role(User.Role.MANAGER)
                .enabled(true)
                .build();

        when(dataScopeConfigService.getAccessProfile(user)).thenReturn(DataScopeAccessProfile.builder()
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
                .role(User.Role.MANAGER)
                .enabled(true)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("staff-user", "N/A", List.of())
        );

        when(userRepository.findByUsername("staff-user")).thenReturn(Optional.of(user));
        when(dataScopeConfigService.getAccessProfile(user)).thenReturn(DataScopeAccessProfile.builder()
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
    void getAllowedProjectIds_ShouldIncludeAssignedTaskProjects() {
        // CO-293 P0: a task assignee gets project-level visibility for the assigned task's project.
        // Module/tab-level restrictions remain a follow-up concern and are not modeled here.
        User user = User.builder()
                .id(803L)
                .username("cross-dept-task-assignee")
                .role(User.Role.MANAGER)
                .roleProfile(RoleProfile.builder().code("bid-otherDept").name("跨部门协同人员").build())
                .enabled(true)
                .build();

        when(dataScopeConfigService.getAccessProfile(user)).thenReturn(DataScopeAccessProfile.builder()
                .dataScope("self")
                .build());
        when(projectRepository.findAccessibleProjectIdsByUserId(803L)).thenReturn(List.of());
        when(projectGroupService.getGrantedProjectIds(user)).thenReturn(List.of());
        when(projectMemberRepository.findByUserId(anyLong())).thenReturn(List.of());
        when(crmCustomerPermissionRepository.findByUserId(anyLong())).thenReturn(List.of());
        when(leadAssignmentRepository.findByPrimaryLeadUserId(803L)).thenReturn(List.of());
        when(taskRepository.findDistinctProjectIdsByAssigneeId(803L)).thenReturn(List.of(400L));

        assertThat(projectAccessScopeService.getAllowedProjectIds(user)).containsExactly(400L);
    }

    @Test
    void getAllowedProjectIds_SecondaryLeadShouldNotGetProjectVisibility() {
        // 对齐权限矩阵：副负责人不自动获得项目可见性
        // 投标项目负责人仅看主负责人项目（"自己的"），投标专员通过任务指派获得可见性（"参与的"）
        User user = User.builder()
                .id(801L)
                .username("secondary-lead")
                .role(User.Role.MANAGER)
                .enabled(true)
                .build();

        when(dataScopeConfigService.getAccessProfile(user)).thenReturn(DataScopeAccessProfile.builder()
                .dataScope("self")
                .build());
        when(projectRepository.findAccessibleProjectIdsByUserId(801L)).thenReturn(List.of());
        when(projectGroupService.getGrantedProjectIds(user)).thenReturn(List.of());
        when(projectMemberRepository.findByUserId(anyLong())).thenReturn(List.of());
        when(crmCustomerPermissionRepository.findByUserId(anyLong())).thenReturn(List.of());
        when(leadAssignmentRepository.findByPrimaryLeadUserId(801L)).thenReturn(List.of());
        when(taskRepository.findDistinctProjectIdsByAssigneeId(801L)).thenReturn(List.of());

        // 仅为副负责人时，不应看到该项目
        assertThat(projectAccessScopeService.getAllowedProjectIds(user)).isEmpty();
    }

    @Test
    void getAllowedProjectIds_ShouldDeduplicatePrimaryLeadWithTaskAssignmentProjects() {
        // 主负责人项目与任务指派项目重叠 → 通过 Set 去重
        User user = User.builder()
                .id(802L)
                .username("both-leads")
                .role(User.Role.MANAGER)
                .enabled(true)
                .build();

        when(dataScopeConfigService.getAccessProfile(user)).thenReturn(DataScopeAccessProfile.builder()
                .dataScope("self")
                .build());
        when(projectRepository.findAccessibleProjectIdsByUserId(802L)).thenReturn(List.of());
        when(projectGroupService.getGrantedProjectIds(user)).thenReturn(List.of());
        when(projectMemberRepository.findByUserId(anyLong())).thenReturn(List.of());
        when(crmCustomerPermissionRepository.findByUserId(anyLong())).thenReturn(List.of());
        when(leadAssignmentRepository.findByPrimaryLeadUserId(802L)).thenReturn(List.of(
                com.xiyu.bid.project.entity.ProjectLeadAssignment.builder().projectId(300L).build()
        ));
        when(taskRepository.findDistinctProjectIdsByAssigneeId(802L)).thenReturn(List.of(300L));

        assertThat(projectAccessScopeService.getAllowedProjectIds(user)).containsExactly(300L);
    }

    @Test
    void getAllowedProjectIds_ShouldIncludeReviewerProjects() {
        // CO-315: a bid document reviewer must be able to access the project for review.
        User user = User.builder()
                .id(901L)
                .username("bid-reviewer")
                .role(User.Role.MANAGER)
                .enabled(true)
                .build();

        when(dataScopeConfigService.getAccessProfile(user)).thenReturn(DataScopeAccessProfile.builder()
                .dataScope("self")
                .build());
        when(projectRepository.findAccessibleProjectIdsByUserId(901L)).thenReturn(List.of());
        when(projectGroupService.getGrantedProjectIds(user)).thenReturn(List.of());
        when(projectMemberRepository.findByUserId(anyLong())).thenReturn(List.of());
        when(crmCustomerPermissionRepository.findByUserId(anyLong())).thenReturn(List.of());
        when(leadAssignmentRepository.findByPrimaryLeadUserId(901L)).thenReturn(List.of());
        when(taskRepository.findDistinctProjectIdsByAssigneeId(901L)).thenReturn(List.of());
        when(bidDocumentReviewRepository.findByReviewerId(901L)).thenReturn(List.of(
                BidDocumentReviewEntity.builder().projectId(42L).reviewerId(901L).build()
        ));

        assertThat(projectAccessScopeService.getAllowedProjectIds(user)).containsExactly(42L);
    }

    @Test
    void assertCurrentUserCanAccessProject_ShouldRejectUnauthorizedProject() {
        User user = User.builder()
                .id(701L)
                .username("outsider-user")
                .role(User.Role.MANAGER)
                .enabled(true)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("outsider-user", "N/A", List.of())
        );

        when(userRepository.findByUsername("outsider-user")).thenReturn(Optional.of(user));
        when(dataScopeConfigService.getAccessProfile(user)).thenReturn(DataScopeAccessProfile.builder()
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
