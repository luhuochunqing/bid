package com.xiyu.bid.projectworkflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Task;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.projectworkflow.dto.ProjectScoreDraftGenerateRequest;
import com.xiyu.bid.projectworkflow.dto.ProjectScoreDraftUpdateRequest;
import com.xiyu.bid.projectworkflow.dto.ProjectTaskCreateRequest;
import com.xiyu.bid.projectworkflow.dto.ProjectTaskViewDTO;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.projectworkflow.repository.ProjectReminderRepository;
import com.xiyu.bid.projectworkflow.entity.ProjectScoreDraft;
import com.xiyu.bid.projectworkflow.repository.ProjectScoreDraftRepository;
import com.xiyu.bid.projectworkflow.repository.ProjectShareLinkRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TaskRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import com.xiyu.bid.task.service.TaskHistoryRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectWorkflowServiceTest {

    private ProjectRepository projectRepository;
    private TaskRepository taskRepository;
    private ProjectScoreDraftRepository projectScoreDraftRepository;
    private ProjectDocumentRepository projectDocumentRepository;
    private ProjectReminderRepository projectReminderRepository;
    private ProjectShareLinkRepository projectShareLinkRepository;
    private UserRepository userRepository;
    private ProjectWorkflowService service;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        taskRepository = mock(TaskRepository.class);
        projectScoreDraftRepository = mock(ProjectScoreDraftRepository.class);
        projectDocumentRepository = mock(ProjectDocumentRepository.class);
        projectReminderRepository = mock(ProjectReminderRepository.class);
        projectShareLinkRepository = mock(ProjectShareLinkRepository.class);
        userRepository = mock(UserRepository.class);
        ProjectAccessScopeService projectAccessScopeService = mock(ProjectAccessScopeService.class);
        ScoreDraftParserService scoreDraftParserService = mock(ScoreDraftParserService.class);
        ObjectMapper objectMapper = new ObjectMapper();

        ProjectWorkflowGuardService guardService = new ProjectWorkflowGuardService(
                projectRepository,
                projectAccessScopeService,
                taskRepository,
                projectDocumentRepository,
                projectScoreDraftRepository
        );
        ProjectTaskWorkflowService projectTaskWorkflowService = new ProjectTaskWorkflowService(guardService, taskRepository, userRepository, objectMapper, mock(TaskHistoryRecorder.class), mock(com.xiyu.bid.projectworkflow.service.ProjectTaskDeliverableCollector.class));
        ProjectDocumentWorkflowService projectDocumentWorkflowService = new ProjectDocumentWorkflowService(
                guardService,
                projectDocumentRepository,
                userRepository,
                new ProjectDocumentViewAssembler(),
                mock(ProjectDocumentBindingGateway.class)
        );
        ProjectDocumentUploadWorkflowService projectDocumentUploadWorkflowService =
                mock(ProjectDocumentUploadWorkflowService.class);
        ProjectDocumentFacade projectDocumentFacade = new ProjectDocumentFacade(
                projectDocumentWorkflowService,
                projectDocumentUploadWorkflowService
        );
        ProjectReminderWorkflowService projectReminderWorkflowService = new ProjectReminderWorkflowService(
                guardService,
                projectReminderRepository,
                userRepository,
                new ProjectReminderViewAssembler()
        );
        ProjectShareLinkWorkflowService projectShareLinkWorkflowService = new ProjectShareLinkWorkflowService(
                guardService,
                projectShareLinkRepository,
                userRepository,
                new ProjectShareLinkViewAssembler()
        );
        ProjectScoreDraftWorkflowService projectScoreDraftWorkflowService = new ProjectScoreDraftWorkflowService(
                guardService,
                projectScoreDraftRepository,
                scoreDraftParserService,
                projectTaskWorkflowService,
                objectMapper
        );
        service = new ProjectWorkflowService(
                projectTaskWorkflowService,
                projectDocumentFacade,
                projectReminderWorkflowService,
                projectShareLinkWorkflowService,
                projectScoreDraftWorkflowService
        );

        when(projectRepository.findById(1001L)).thenReturn(Optional.of(Project.builder()
                .id(1001L)
                .status(Project.Status.INITIATED)
                .deadline(java.time.LocalDate.of(2026, 5, 20))
                .build()));
    }

    @Test
    void getProjectTasks_ShouldAllowTerminalProjectRead() {
        when(projectRepository.findById(1001L)).thenReturn(Optional.of(Project.builder()
                .id(1001L)
                .status(Project.Status.WON)
                .build()));
        when(taskRepository.findByProjectId(1001L)).thenReturn(List.of(Task.builder()
                .id(3001L)
                .projectId(1001L)
                .title("归档任务")
                .status(Task.Status.COMPLETED)
                .priority(Task.Priority.MEDIUM)
                .build()));

        List<ProjectTaskViewDTO> tasks = service.getProjectTasks(1001L);

        assertThat(tasks).hasSize(1);
        assertThat(tasks.getFirst().getName()).isEqualTo("归档任务");
    }

    @Test
    void createProjectTask_ShouldAllowInitiatedProjectMutation() {
        when(projectRepository.findById(1001L)).thenReturn(Optional.of(Project.builder()
                .id(1001L)
                .status(Project.Status.INITIATED)
                .build()));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
            Task task = invocation.getArgument(0);
            task.setId(3002L);
            return task;
        });

        ProjectTaskViewDTO task = service.createProjectTask(1001L, ProjectTaskCreateRequest.builder()
                .title(" 启动后补充任务 ")
                .content("# 标题\n- 保留 Markdown")
                .extendedFields(java.util.Map.of("chapter", "扩展值ABC"))
                .priority(ProjectTaskCreateRequest.Priority.MEDIUM)
                .build());

        assertThat(task.getId()).isEqualTo(3002L);
        assertThat(task.getName()).isEqualTo("启动后补充任务");
        assertThat(task.getContent()).isEqualTo("# 标题\n- 保留 Markdown");
        assertThat(task.getExtendedFields()).containsEntry("chapter", "扩展值ABC");
    }

    @Test
    void createProjectTask_ShouldDefaultAssigneeToCreatorWhenRequestHasNoAssignee() {
        when(projectRepository.findById(1001L)).thenReturn(Optional.of(Project.builder()
                .id(1001L)
                .status(Project.Status.INITIATED)
                .build()));
        when(userRepository.findByUsername("creator")).thenReturn(Optional.of(User.builder()
                .id(9L)
                .username("creator")
                .fullName("测试用户")
                .email("creator@example.com")
                .password("pw")
                .role(User.Role.STAFF)
                .departmentCode("BID")
                .departmentName("投标管理部")
                .build()));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
            Task task = invocation.getArgument(0);
            task.setId(3003L);
            return task;
        });

        ProjectTaskViewDTO task = service.createProjectTask(1001L, ProjectTaskCreateRequest.builder()
                .title("默认责任人")
                .priority(ProjectTaskCreateRequest.Priority.MEDIUM)
                .build(), "creator");

        assertThat(task.getAssigneeId()).isEqualTo(9L);
        assertThat(task.getOwner()).isEqualTo("测试用户");
        assertThat(task.getDepartment()).isEqualTo("投标管理部");
        assertThat(task.getRoleName()).isEqualTo("员工");
    }

    @Test
    void createProjectTask_ShouldRejectTerminalProjectMutation() {
        when(projectRepository.findById(1001L)).thenReturn(Optional.of(Project.builder()
                .id(1001L)
                .status(Project.Status.WON)
                .build()));

        assertThatThrownBy(() -> service.createProjectTask(1001L, ProjectTaskCreateRequest.builder()
                .title("归档后新增任务")
                .priority(ProjectTaskCreateRequest.Priority.MEDIUM)
                .build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not in a valid state");
    }

    @Test
    void updateProjectScoreDraft_ShouldDeriveReadyStatusWhenAssigneeIsPresent() {
        ProjectScoreDraft draft = baseDraft(ProjectScoreDraft.Status.DRAFT);
        when(projectScoreDraftRepository.findById(2001L)).thenReturn(Optional.of(draft));
        when(projectScoreDraftRepository.save(any(ProjectScoreDraft.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.updateProjectScoreDraft(1001L, 2001L, ProjectScoreDraftUpdateRequest.builder()
                .assigneeId(3001L)
                .generatedTaskTitle("  整理商务资质  ")
                .generatedTaskDescription("  准备资质材料  ")
                .build());

        verify(projectScoreDraftRepository).save(draft);
        assertThat(draft.getAssigneeId()).isEqualTo(3001L);
        assertThat(draft.getGeneratedTaskTitle()).isEqualTo("整理商务资质");
        assertThat(draft.getGeneratedTaskDescription()).isEqualTo("准备资质材料");
        assertThat(draft.getStatus()).isEqualTo(ProjectScoreDraft.Status.READY);
    }

    @Test
    void updateProjectScoreDraft_ShouldRejectReadyDraftWithoutAssignee() {
        ProjectScoreDraft draft = baseDraft(ProjectScoreDraft.Status.DRAFT);
        when(projectScoreDraftRepository.findById(2001L)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.updateProjectScoreDraft(1001L, 2001L, ProjectScoreDraftUpdateRequest.builder()
                .status(ProjectScoreDraftUpdateRequest.Status.READY)
                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("生成正式任务前必须指定责任人");
    }

    @Test
    void updateProjectScoreDraft_ShouldKeepGeneratedDraftImmutable() {
        ProjectScoreDraft draft = baseDraft(ProjectScoreDraft.Status.GENERATED);
        when(projectScoreDraftRepository.findById(2001L)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.updateProjectScoreDraft(1001L, 2001L, ProjectScoreDraftUpdateRequest.builder()
                .assigneeId(3001L)
                .build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已生成正式任务的草稿不可修改");
    }

    @Test
    void generateTasksFromScoreDrafts_ShouldCreateTaskAndMarkDraftGenerated() {
        ProjectScoreDraft draft = baseDraft(ProjectScoreDraft.Status.READY);
        draft.setAssigneeName("王工");
        when(projectScoreDraftRepository.findById(2001L)).thenReturn(Optional.of(draft));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
            Task task = invocation.getArgument(0);
            task.setId(9001L);
            return task;
        });
        when(projectScoreDraftRepository.save(any(ProjectScoreDraft.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<ProjectTaskViewDTO> tasks = service.generateTasksFromScoreDrafts(1001L, ProjectScoreDraftGenerateRequest.builder()
                .draftIds(List.of(2001L))
                .build());

        assertThat(tasks).hasSize(1);
        assertThat(tasks.getFirst().getId()).isEqualTo(9001L);
        assertThat(tasks.getFirst().getName()).isEqualTo("准备商务响应文件");
        assertThat(tasks.getFirst().getOwner()).isEqualTo("王工");
        assertThat(draft.getGeneratedTaskId()).isEqualTo(9001L);
        assertThat(draft.getStatus()).isEqualTo(ProjectScoreDraft.Status.GENERATED);
        verify(projectScoreDraftRepository).save(draft);
    }

    private ProjectScoreDraft baseDraft(ProjectScoreDraft.Status status) {
        return ProjectScoreDraft.builder()
                .id(2001L)
                .projectId(1001L)
                .sourceFileName("评分标准.docx")
                .category("business")
                .scoreItemTitle("商务响应")
                .scoreRuleText("按响应完整度评分")
                .scoreValueText("最高10分")
                .taskAction("准备")
                .generatedTaskTitle("准备商务响应文件")
                .generatedTaskDescription("整理商务响应材料")
                .suggestedDeliverables("[]")
                .sourceTableIndex(0)
                .sourceRowIndex(0)
                .dueDate(LocalDateTime.of(2026, 5, 1, 18, 0))
                .status(status)
                .build();
    }
}
