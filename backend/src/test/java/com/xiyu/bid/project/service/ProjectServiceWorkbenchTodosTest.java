package com.xiyu.bid.project.service;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.project.dto.ProjectDTO;
import com.xiyu.bid.project.entity.BidDocumentReviewEntity;
import com.xiyu.bid.project.entity.ProjectLeadAssignment;
import com.xiyu.bid.project.repository.BidDocumentReviewRepository;
import com.xiyu.bid.project.repository.ProjectLeadAssignmentRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.security.EffectiveRoleResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 工作台角色化改造 BE-3：ProjectService.getWorkbenchTodos 按角色分支返回测试。
 * 覆盖 spec.md §3 模块3 的 3 种角色分支 + fail-closed + 其他角色。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjectServiceWorkbenchTodosTest {

    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectLeadAssignmentRepository projectLeadAssignmentRepository;
    @Mock private BidDocumentReviewRepository bidDocumentReviewRepository;
    @Mock private ProjectCurrentUserLookupService currentUserLookupService;
    @Mock private EffectiveRoleResolver effectiveRoleResolver;
    @Mock private UserDetails userDetails;

    private ProjectService projectService;
    private final User currentUser = mock(User.class);

    @BeforeEach
    void setUp() {
        projectService = new ProjectService(
                projectRepository,
                null,  // projectAccessScopeService
                null,  // demoModeService
                null,  // demoDataProvider
                null,  // demoFusionService
                null,  // projectImportService
                null,  // projectQueryService
                projectLeadAssignmentRepository,
                bidDocumentReviewRepository,
                currentUserLookupService,
                effectiveRoleResolver
        );
        when(currentUserLookupService.requireUser(userDetails)).thenReturn(currentUser);
        when(currentUser.getId()).thenReturn(1L);
        when(currentUser.getUsername()).thenReturn("testuser");
    }

    @Test
    void adminLeadRole_returnsInitiatedProjectsPlusReviewerProjects() {
        // /bidAdmin 属于 GLOBAL_ACCESS_ROLES，走 admin_lead 分支
        when(effectiveRoleResolver.resolveRoleCode(currentUser)).thenReturn(RoleProfileCatalog.BID_ADMIN_CODE);
        Project initiated = newProject(10L, "已立项项目", "INITIATED");
        when(projectRepository.findByStageIn(List.of("INITIATED"))).thenReturn(List.of(initiated));
        BidDocumentReviewEntity review = BidDocumentReviewEntity.builder().projectId(20L).build();
        when(bidDocumentReviewRepository.findByReviewerId(1L)).thenReturn(List.of(review));
        Project reviewerProject = newProject(20L, "审核项目", "DRAFTING");
        when(projectRepository.findAllById(any())).thenReturn(List.of(initiated, reviewerProject));

        List<ProjectDTO> result = projectService.getWorkbenchTodos(userDetails);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ProjectDTO::getId).containsExactlyInAnyOrder(10L, 20L);
    }

    @Test
    void bidTeamRole_returnsLeadProjectsExcludingClosed() {
        when(effectiveRoleResolver.resolveRoleCode(currentUser)).thenReturn(RoleProfileCatalog.BID_SPECIALIST_CODE);
        ProjectLeadAssignment primary = ProjectLeadAssignment.builder().projectId(10L).build();
        ProjectLeadAssignment secondary = ProjectLeadAssignment.builder().projectId(20L).build();
        when(projectLeadAssignmentRepository.findByPrimaryLeadUserId(1L)).thenReturn(List.of(primary));
        when(projectLeadAssignmentRepository.findBySecondaryLeadUserId(1L)).thenReturn(List.of(secondary));
        Project active = newProject(10L, "进行中项目", "DRAFTING");
        Project closed = newProject(20L, "已结项项目", "CLOSED");
        // bid-Team 分支会调用两次 findAllById：第一次用于过滤 CLOSED，第二次用于最终返回
        when(projectRepository.findAllById(any()))
                .thenReturn(List.of(active, closed))  // 第一次：过滤 CLOSED 后 projectIds={10}
                .thenReturn(List.of(active));          // 第二次：最终返回

        List<ProjectDTO> result = projectService.getWorkbenchTodos(userDetails);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(10L);
        assertThat(result.get(0).getStage()).isEqualTo("DRAFTING");
    }

    @Test
    void projectLeaderRole_returnsInitiatedAndRetrospectiveAndReviewerProjects() {
        when(effectiveRoleResolver.resolveRoleCode(currentUser)).thenReturn(RoleProfileCatalog.SALES_CODE);
        Project initiated = newProject(10L, "待立项", "INITIATED");
        Project retrospective = newProject(20L, "待结项", "RETROSPECTIVE");
        when(projectRepository.findByStageIn(List.of("INITIATED", "RETROSPECTIVE")))
                .thenReturn(List.of(initiated, retrospective));
        BidDocumentReviewEntity review = BidDocumentReviewEntity.builder().projectId(30L).build();
        when(bidDocumentReviewRepository.findByReviewerId(1L)).thenReturn(List.of(review));
        Project reviewerProject = newProject(30L, "审核项目", "DRAFTING");
        when(projectRepository.findAllById(any()))
                .thenReturn(List.of(initiated, retrospective, reviewerProject));

        List<ProjectDTO> result = projectService.getWorkbenchTodos(userDetails);

        assertThat(result).hasSize(3);
        assertThat(result).extracting(ProjectDTO::getId).containsExactlyInAnyOrder(10L, 20L, 30L);
    }

    @Test
    void nullRoleCode_returnsEmptyList_failClosed() {
        // OSS 缓存未命中时 roleCode=null，fail-closed 返回空列表
        when(effectiveRoleResolver.resolveRoleCode(currentUser)).thenReturn(null);

        List<ProjectDTO> result = projectService.getWorkbenchTodos(userDetails);

        assertThat(result).isEmpty();
    }

    @Test
    void otherRole_returnsEmptyList() {
        // bid-otherDept 等其他角色不展示项目待办
        when(effectiveRoleResolver.resolveRoleCode(currentUser))
                .thenReturn(RoleProfileCatalog.BID_OTHER_DEPT_CODE);

        List<ProjectDTO> result = projectService.getWorkbenchTodos(userDetails);

        assertThat(result).isEmpty();
    }

    private Project newProject(Long id, String name, String stage) {
        return Project.builder().id(id).name(name).stage(stage).build();
    }
}
