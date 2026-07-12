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
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    @Mock
    private ProjectManagerDepartmentEnricher managerDepartmentEnricher;

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
                demoFusionService,
                managerDepartmentEnricher);
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
    @DisplayName("leaderDepartment 为空时应通过 department_code 关联 organization_departments 反查部门名回填")
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
        manager.setDepartmentCode("700498910");
        manager.setDepartmentName("");
        when(userRepository.findByIdIn(Set.of(99L))).thenReturn(List.of(manager));
        // mock enricher 返回 99L → "东部二区"
        when(managerDepartmentEnricher.buildManagerDepartmentMap(eq(Set.of(99L)), any()))
                .thenReturn(Map.of(99L, "东部二区"));

        ProjectQueryService service = createService();
        List<ProjectDTO> result = service.getAllProjects();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLeaderDepartment()).isEqualTo("东部二区");
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
        manager.setDepartmentCode("700498910");
        when(userRepository.findByIdIn(Set.of(99L))).thenReturn(List.of(manager));
        when(managerDepartmentEnricher.buildManagerDepartmentMap(eq(Set.of(99L)), any()))
                .thenReturn(Map.of(99L, "东部二区"));

        ProjectQueryService service = createService();
        List<ProjectDTO> result = service.getAllProjects();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLeaderDepartment()).isEqualTo("已有部门");
    }

    @Test
    @DisplayName("leaderDepartment 为空且 manager 用户无 department_code 时保持为空")
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
        manager.setDepartmentCode(null);
        when(userRepository.findByIdIn(Set.of(99L))).thenReturn(List.of(manager));
        // mock enricher 返回空 map（department_code 为空，无法反查）
        when(managerDepartmentEnricher.buildManagerDepartmentMap(eq(Set.of(99L)), any()))
                .thenReturn(Map.of());

        ProjectQueryService service = createService();
        List<ProjectDTO> result = service.getAllProjects();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLeaderDepartment()).isNull();
    }

    @Test
    @DisplayName("CO-578: enrichSingle 应为详情接口补充投标负责人和辅助人员姓名")
    void enrichSingle_shouldPopulateBiddingLeaderAndAssistantName() {
        ProjectDTO dto = ProjectDTO.builder().id(1L).managerId(99L).build();

        ProjectInitiationDetails details = new ProjectInitiationDetails();
        details.setProjectId(1L);
        details.setBiddingLeaderName("李四");
        details.setProjectLeaderName("张三");
        details.setLeaderDepartment("华东区");
        when(projectInitiationDetailsRepository.findByProjectIdIn(List.of(1L)))
                .thenReturn(List.of(details));

        com.xiyu.bid.project.entity.ProjectLeadAssignment assignment =
                com.xiyu.bid.project.entity.ProjectLeadAssignment.builder()
                        .projectId(1L)
                        .primaryLeadUserId(10L)
                        .secondaryLeadUserId(20L)
                        .build();
        when(projectLeadAssignmentRepository.findByProjectIdIn(List.of(1L)))
                .thenReturn(List.of(assignment));
        when(projectResultRepository.findByProjectIdIn(any())).thenReturn(List.of());

        User secondaryUser = new User();
        secondaryUser.setId(20L);
        secondaryUser.setFullName("王五");
        when(userRepository.findByIdIn(Set.of(10L, 20L, 99L)))
                .thenReturn(List.of(secondaryUser));
        when(managerDepartmentEnricher.buildManagerDepartmentMap(eq(Set.of(99L)), any()))
                .thenReturn(Map.of());

        ProjectQueryService service = createService();
        service.enrichSingle(dto);

        assertThat(dto.getBiddingLeaderName()).isEqualTo("李四");
        assertThat(dto.getSecondaryBiddingLeaderName()).isEqualTo("王五");
        assertThat(dto.getProjectLeaderName()).isEqualTo("张三");
        assertThat(dto.getLeaderDepartment()).isEqualTo("华东区");
    }

    @Test
    @DisplayName("CO-578: enrichSingle 无 assignment 时辅助人员姓名为 null")
    void enrichSingle_shouldReturnNullAssistantName_whenNoAssignment() {
        ProjectDTO dto = ProjectDTO.builder().id(2L).managerId(99L).build();

        ProjectInitiationDetails details = new ProjectInitiationDetails();
        details.setProjectId(2L);
        details.setBiddingLeaderName("赵六");
        when(projectInitiationDetailsRepository.findByProjectIdIn(List.of(2L)))
                .thenReturn(List.of(details));
        when(projectLeadAssignmentRepository.findByProjectIdIn(List.of(2L)))
                .thenReturn(List.of());
        when(projectResultRepository.findByProjectIdIn(any())).thenReturn(List.of());
        when(userRepository.findByIdIn(Set.of(99L)))
                .thenReturn(List.of());
        when(managerDepartmentEnricher.buildManagerDepartmentMap(eq(Set.of(99L)), any()))
                .thenReturn(Map.of());

        ProjectQueryService service = createService();
        service.enrichSingle(dto);

        assertThat(dto.getBiddingLeaderName()).isEqualTo("赵六");
        assertThat(dto.getSecondaryBiddingLeaderName()).isNull();
    }

    @Test
    @DisplayName("CO-578: enrichSingle 传入 null 或无 id 时安全返回")
    void enrichSingle_shouldDoNothing_whenDtoIsNullOrNullId() {
        ProjectQueryService service = createService();
        // 不抛异常即可
        service.enrichSingle(null);
        service.enrichSingle(ProjectDTO.builder().id(null).build());
    }
}
