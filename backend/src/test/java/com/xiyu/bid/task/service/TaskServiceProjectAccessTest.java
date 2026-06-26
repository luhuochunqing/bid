package com.xiyu.bid.task.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.entity.Task;
import com.xiyu.bid.repository.TaskRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.service.RoleProfileService;
import com.xiyu.bid.project.notification.ProjectNotificationService;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.task.dto.TaskDTO;
import com.xiyu.bid.task.repository.TaskDeliverableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceProjectAccessTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProjectAccessScopeService projectAccessScopeService;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private RoleProfileService roleProfileService;

    @Mock
    private TaskHistoryRecorder taskHistoryRecorder;

    @Mock
    private ProjectNotificationService notificationService;

    @Mock
    private ProjectDocumentRepository projectDocumentRepository;

    @Mock
    private TaskDeliverableRepository taskDeliverableRepository;

    @Mock
    private TaskPermissionGuard taskPermissionGuard;

    private TaskService taskService;

    @BeforeEach
    void setUp() {
        TaskAssignmentSupport assignmentSupport = new TaskAssignmentSupport(
                userRepository,
                taskRepository,
                projectAccessScopeService,
                roleProfileService
        );
        taskService = new TaskService(
                taskRepository,
                projectAccessScopeService,
                projectRepository,
                assignmentSupport,
                new TaskDtoMapper(new ObjectMapper(), projectDocumentRepository, taskDeliverableRepository),
                taskHistoryRecorder,
                notificationService,
                userRepository,
                taskPermissionGuard
        );
    }

    @Test
    void getTaskByIdRejectsTaskFromInvisibleProject() {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task(1L, 20L, "不可见任务")));
        when(projectRepository.existsById(20L)).thenReturn(true);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of(10L));
        when(projectRepository.existsById(20L)).thenReturn(true);

        assertThatThrownBy(() -> taskService.getTaskById(1L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void updateTaskRejectsTaskFromInvisibleProjectBeforeSaving() {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task(1L, 20L, "不可见任务")));
        when(projectRepository.existsById(20L)).thenReturn(true);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of(10L));
        when(projectRepository.existsById(20L)).thenReturn(true);

        assertThatThrownBy(() -> taskService.updateTask(1L, TaskDTO.builder().title("新标题").build()))
                .isInstanceOf(AccessDeniedException.class);

        verify(taskRepository, never()).save(org.mockito.ArgumentMatchers.any(Task.class));
    }

    @Test
    void getAllTasksFiltersTasksFromInvisibleProjects() {
        when(taskRepository.findAll()).thenReturn(List.of(
                task(1L, 10L, "可见任务"),
                task(2L, 20L, "不可见任务")
        ));
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of(10L));

        List<TaskDTO> tasks = taskService.getAllTasks();

        assertThat(tasks).extracting(TaskDTO::getId).containsExactly(1L);
    }

    @Test
    void getUpcomingTasksFiltersTasksFromInvisibleProjects() {
        LocalDateTime beforeDate = LocalDateTime.now().plusDays(7);
        when(taskRepository.findByDueDateBefore(beforeDate)).thenReturn(List.of(
                task(1L, 10L, "可见即将到期"),
                task(2L, 20L, "不可见即将到期")
        ));
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of(10L));

        List<TaskDTO> tasks = taskService.getUpcomingTasks(beforeDate);

        assertThat(tasks).extracting(TaskDTO::getId).containsExactly(1L);
    }

    @Test
    void getOverdueTasksFiltersTasksFromInvisibleProjects() {
        when(taskRepository.findByDueDateBeforeAndStatusNot(
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.eq(Task.Status.COMPLETED)
        )).thenReturn(List.of(
                task(1L, 10L, "可见逾期"),
                task(2L, 20L, "不可见逾期")
        ));
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of(10L));

        List<TaskDTO> tasks = taskService.getOverdueTasks();

        assertThat(tasks).extracting(TaskDTO::getId).containsExactly(1L);
    }

    private Task task(Long id, Long projectId, String title) {
        return Task.builder()
                .id(id)
                .projectId(projectId)
                .title(title)
                .status(Task.Status.TODO)
                .priority(Task.Priority.MEDIUM)
                .build();
    }
}
