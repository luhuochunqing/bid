package com.xiyu.bid.project.service;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.project.dto.ProjectDTO;
import com.xiyu.bid.project.entity.ProjectInitiationDetails;
import com.xiyu.bid.project.repository.ProjectEvaluationRepository;
import com.xiyu.bid.project.repository.ProjectInitiationDetailsRepository;
import com.xiyu.bid.project.repository.ProjectLeadAssignmentRepository;
import com.xiyu.bid.project.repository.ProjectResultRepository;
import com.xiyu.bid.demo.service.DemoDataProvider;
import com.xiyu.bid.demo.service.DemoFusionService;
import com.xiyu.bid.demo.service.DemoModeService;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import com.xiyu.bid.tender.repository.TenderEvaluationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectQueryServiceTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectAccessScopeService projectAccessScopeService;
    @Mock
    private TenderRepository tenderRepository;
    @Mock
    private TenderEvaluationRepository tenderEvaluationRepository;
    @Mock
    private ProjectInitiationDetailsRepository projectInitiationDetailsRepository;
    @Mock
    private ProjectLeadAssignmentRepository projectLeadAssignmentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ProjectEvaluationRepository projectEvaluationRepository;
    @Mock
    private ProjectResultRepository projectResultRepository;
    @Mock
    private DemoModeService demoModeService;
    @Mock
    private DemoDataProvider demoDataProvider;
    @Mock
    private DemoFusionService demoFusionService;

    private ProjectQueryService createService() {
        return new ProjectQueryService(
                projectRepository,
                projectAccessScopeService,
                tenderRepository,
                tenderEvaluationRepository,
                projectInitiationDetailsRepository,
                projectLeadAssignmentRepository,
                userRepository,
                projectEvaluationRepository,
                projectResultRepository,
                demoModeService,
                demoDataProvider,
                demoFusionService);
    }

    private Project project(long id, Long managerId) {
        Project p = new Project();
        p.setId(id);
        p.setName("项目" + id);
        p.setManagerId(managerId);
        p.setCreatedAt(LocalDateTime.now());
        p.setStatus(Project.Status.PENDING_INITIATION);
        return p;
    }

    @Test
    @DisplayName("leaderDepartment 为空时应从项目 manager 用户反查部门回填")
    void shouldBackfillLeaderDepartmentFromManagerUserWhenEmpty() {
        Project project = project(1L, 99L);
        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(projectAccessScopeService.filterAccessibleProjects(any()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(demoModeService.isEnabled()).thenReturn(false);

        ProjectInitiationDetails details = new ProjectInitiationDetails();
        details.setProjectId(1L);
        details.setLeaderDepartment(null);
        when(projectInitiationDetailsRepository.findByProjectIdIn(List.of(1L)))
                .thenReturn(List.of(details));
        when(projectLeadAssignmentRepository.findByProjectIdIn(List.of(1L)))
                .thenReturn(List.of());
        when(projectResultRepository.findByProjectIdIn(any())).thenReturn(List.of());

        User manager = new User();
        manager.setId(99L);
        manager.setDepartmentName("华东事业部");
        when(userRepository.findByIdIn(Set.of(99L))).thenReturn(List.of(manager));

        ProjectQueryService service = createService();
        List<ProjectDTO> result = service.getAllProjects();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLeaderDepartment()).isEqualTo("华东事业部");
    }

    @Test
    @DisplayName("leaderDepartment 已有值时不被用户部门覆盖")
    void shouldNotOverrideLeaderDepartmentWhenAlreadyPresent() {
        Project project = project(1L, 99L);
        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(projectAccessScopeService.filterAccessibleProjects(any()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(demoModeService.isEnabled()).thenReturn(false);

        ProjectInitiationDetails details = new ProjectInitiationDetails();
        details.setProjectId(1L);
        details.setLeaderDepartment("已有部门");
        when(projectInitiationDetailsRepository.findByProjectIdIn(List.of(1L)))
                .thenReturn(List.of(details));
        when(projectLeadAssignmentRepository.findByProjectIdIn(List.of(1L)))
                .thenReturn(List.of());
        when(projectResultRepository.findByProjectIdIn(any())).thenReturn(List.of());

        User manager = new User();
        manager.setId(99L);
        manager.setDepartmentName("华东事业部");
        when(userRepository.findByIdIn(Set.of(99L))).thenReturn(List.of(manager));

        ProjectQueryService service = createService();
        List<ProjectDTO> result = service.getAllProjects();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLeaderDepartment()).isEqualTo("已有部门");
    }

    @Test
    @DisplayName("leaderDepartment 为空但 manager 用户无部门时保持为空")
    void shouldKeepLeaderDepartmentNullWhenManagerHasNoDepartment() {
        Project project = project(1L, 99L);
        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(projectAccessScopeService.filterAccessibleProjects(any()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(demoModeService.isEnabled()).thenReturn(false);

        ProjectInitiationDetails details = new ProjectInitiationDetails();
        details.setProjectId(1L);
        details.setLeaderDepartment(null);
        when(projectInitiationDetailsRepository.findByProjectIdIn(List.of(1L)))
                .thenReturn(List.of(details));
        when(projectLeadAssignmentRepository.findByProjectIdIn(List.of(1L)))
                .thenReturn(List.of());
        when(projectResultRepository.findByProjectIdIn(any())).thenReturn(List.of());

        User manager = new User();
        manager.setId(99L);
        manager.setDepartmentName(null);
        when(userRepository.findByIdIn(Set.of(99L))).thenReturn(List.of(manager));

        ProjectQueryService service = createService();
        List<ProjectDTO> result = service.getAllProjects();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLeaderDepartment()).isNull();
    }
}
