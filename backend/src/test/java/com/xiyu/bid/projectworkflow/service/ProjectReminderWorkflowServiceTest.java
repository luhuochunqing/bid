package com.xiyu.bid.projectworkflow.service;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.projectworkflow.dto.ProjectReminderCreateRequest;
import com.xiyu.bid.projectworkflow.dto.ProjectReminderDTO;
import com.xiyu.bid.projectworkflow.entity.ProjectReminder;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.projectworkflow.repository.ProjectReminderRepository;
import com.xiyu.bid.projectworkflow.repository.ProjectScoreDraftRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TaskRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectReminderWorkflowServiceTest {

    private ProjectReminderRepository projectReminderRepository;
    private ProjectReminderWorkflowService service;

    @BeforeEach
    void setUp() {
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        ProjectAccessScopeService projectAccessScopeService = mock(ProjectAccessScopeService.class);
        TaskRepository taskRepository = mock(TaskRepository.class);
        ProjectDocumentRepository projectDocumentRepository = mock(ProjectDocumentRepository.class);
        ProjectScoreDraftRepository projectScoreDraftRepository = mock(ProjectScoreDraftRepository.class);
        projectReminderRepository = mock(ProjectReminderRepository.class);
        UserRepository userRepository = mock(UserRepository.class);

        ProjectWorkflowGuardService guardService = new ProjectWorkflowGuardService(
                projectRepository,
                projectAccessScopeService,
                taskRepository,
                projectDocumentRepository,
                projectScoreDraftRepository
        );
        service = new ProjectReminderWorkflowService(
                guardService,
                projectReminderRepository,
                userRepository,
                new ProjectReminderViewAssembler()
        );

        when(projectRepository.findById(1001L)).thenReturn(Optional.of(Project.builder().id(1001L).build()));
    }

    @Test
    void createProjectReminder_ShouldPersistAndApplyDefaults() {
        when(projectReminderRepository.save(any(ProjectReminder.class))).thenAnswer(invocation -> {
            ProjectReminder reminder = invocation.getArgument(0);
            reminder.setId(4001L);
            reminder.setCreatedAt(LocalDateTime.of(2026, 4, 19, 10, 0));
            return reminder;
        });

        ProjectReminderDTO dto = service.createProjectReminder(1001L, ProjectReminderCreateRequest.builder()
                .title(" 上传中标分析报告 ")
                .message(" 请在今日下班前完成上传 ")
                .remindAt(LocalDateTime.of(2026, 4, 20, 18, 0))
                .createdByName(" 李总 ")
                .build());

        assertThat(dto.getId()).isEqualTo(4001L);
        assertThat(dto.getTitle()).isEqualTo("上传中标分析报告");
        assertThat(dto.getMessage()).isEqualTo("请在今日下班前完成上传");
        assertThat(dto.getRecipient()).isEqualTo("项目负责人");
    }

    @Test
    void getProjectReminders_ShouldReturnDtosInRepositoryOrder() {
        when(projectReminderRepository.findByProjectIdOrderByRemindAtDesc(1001L)).thenReturn(List.of(
                ProjectReminder.builder()
                        .id(4002L)
                        .projectId(1001L)
                        .title("提醒一")
                        .message("准备材料")
                        .remindAt(LocalDateTime.of(2026, 4, 21, 9, 0))
                        .createdByName("张三")
                        .recipient("项目负责人")
                        .createdAt(LocalDateTime.of(2026, 4, 19, 9, 0))
                        .build()
        ));

        List<ProjectReminderDTO> reminders = service.getProjectReminders(1001L);

        assertThat(reminders).hasSize(1);
        assertThat(reminders.getFirst().getTitle()).isEqualTo("提醒一");
        verify(projectReminderRepository).findByProjectIdOrderByRemindAtDesc(1001L);
    }
}
