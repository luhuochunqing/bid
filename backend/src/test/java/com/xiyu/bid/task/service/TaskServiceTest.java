package com.xiyu.bid.task.service;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Task;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.project.notification.ProjectNotificationService;
import com.xiyu.bid.repository.TaskRepository;
import com.xiyu.bid.task.dto.TaskAssignmentRequest;
import com.xiyu.bid.task.dto.TaskDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import com.xiyu.bid.admin.service.DataScopeConfigService;
import com.xiyu.bid.project.repository.BidDocumentReviewRepository;
import com.xiyu.bid.project.repository.ProjectLeadAssignmentRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;
    @Mock
    private TaskAssignmentSupport assignmentSupport;
    @Mock
    private TaskDtoMapper taskDtoMapper;
    @Mock
    private TaskPermissionGuard taskPermissionGuard;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ProjectNotificationService notificationService;
    @Mock
    private TaskHistoryRecorder taskHistoryRecorder;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectAccessScopeService projectAccessScopeService;
    @Mock
    private ProjectLeadAssignmentRepository leadAssignmentRepository;
    @Mock
    private BidDocumentReviewRepository bidDocumentReviewRepository;
    @Mock
    private DataScopeConfigService dataScopeConfigService;

    private TaskService taskService;

    @BeforeEach
    void setUp() {
        // 使用真实的 TaskNameResolver（包装 mock 的 userRepository 和 taskDtoMapper），
        // 让 toDTOWithNames/toDTOsWithNames 的调用能正确委托到 mocked taskDtoMapper。
        TaskNameResolver nameResolver = new TaskNameResolver(userRepository, taskDtoMapper);
        taskService = new TaskService(
                taskRepository,
                projectAccessScopeService,
                projectRepository,
                assignmentSupport,
                taskDtoMapper,
                taskHistoryRecorder,
                notificationService,
                userRepository,
                taskPermissionGuard,
                leadAssignmentRepository,
                bidDocumentReviewRepository,
                dataScopeConfigService,
                nameResolver
        );
    }

    @Test
    void createSystemTaskBypassesPermissionCheck() {
        TaskDTO taskDTO = TaskDTO.builder()
                .projectId(10L)
                .title("System Task")
                .build();

        TaskAssignmentSupport.AssignmentSnapshot snapshot = new TaskAssignmentSupport.AssignmentSnapshot(
                1L, "dept", "Dept Name", "role", "Role Name"
        );

        when(assignmentSupport.resolveAssignmentSnapshot(any(), isNull())).thenReturn(snapshot);
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
            Task t = invocation.getArgument(0);
            t.setId(100L);
            return t;
        });

        TaskDTO expectedDto = TaskDTO.builder().id(100L).build();
        when(userRepository.findById(any())).thenReturn(Optional.empty());
        when(userRepository.findByUsername(any())).thenReturn(Optional.empty());
        when(taskDtoMapper.toDTO(any(Task.class), isNull(), isNull())).thenReturn(expectedDto);

        TaskDTO result = taskService.createSystemTask(taskDTO);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(100L);

        // Verify taskPermissionGuard was NEVER called
        verify(taskPermissionGuard, never()).assertCanManageTask(any());
        
        // Verify createdBy is set to "system"
        verify(taskRepository).save(argThat(task -> "system".equals(task.getCreatedBy())));
    }

    @Test
    void createSystemTaskWithAssigneeSendsNotification() {
        Long assigneeId = 5L;
        Long projectId = 10L;
        TaskDTO taskDTO = TaskDTO.builder()
                .projectId(projectId)
                .title("System Task with assignee")
                .assigneeId(assigneeId)
                .build();

        TaskAssignmentSupport.AssignmentSnapshot snapshot = new TaskAssignmentSupport.AssignmentSnapshot(
                assigneeId, "dept", "Dept Name", "role", "Role Name"
        );

        when(assignmentSupport.resolveAssignmentSnapshot(any(), isNull())).thenReturn(snapshot);
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
            Task t = invocation.getArgument(0);
            t.setId(100L);
            return t;
        });

        TaskDTO expectedDto = TaskDTO.builder().id(100L).projectId(projectId).assigneeId(assigneeId).title("System Task with assignee").build();
        when(userRepository.findById(any())).thenReturn(Optional.empty());
        when(userRepository.findByUsername(any())).thenReturn(Optional.empty());
        when(taskDtoMapper.toDTO(any(Task.class), isNull(), isNull())).thenReturn(expectedDto);

        TaskDTO result = taskService.createSystemTask(taskDTO);

        assertThat(result).isNotNull();
        verify(notificationService).notifyTaskAssigned(eq(projectId), eq(100L), eq("System Task with assignee"), eq(assigneeId), eq(0L));
    }

    @Test
    void createTaskWithoutAssigneeDoesNotSendNotification() {
        Long projectId = 10L;
        TaskDTO taskDTO = TaskDTO.builder()
                .projectId(projectId)
                .title("System task without assignee")
                .build();

        TaskAssignmentSupport.AssignmentSnapshot snapshot = new TaskAssignmentSupport.AssignmentSnapshot(
                null, null, null, null, null
        );

        when(assignmentSupport.resolveAssignmentSnapshot(any(), isNull())).thenReturn(snapshot);
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
            Task t = invocation.getArgument(0);
            t.setId(100L);
            return t;
        });

        TaskDTO expectedDto = TaskDTO.builder().id(100L).projectId(projectId).assigneeId(null).build();
        when(taskDtoMapper.toDTO(any(Task.class), isNull(), isNull())).thenReturn(expectedDto);

        TaskDTO result = taskService.createSystemTask(taskDTO);

        assertThat(result).isNotNull();
        verify(notificationService, never()).notifyTaskAssigned(any(), any(), any(), any(), any());
    }

    @Test
    void testUpdateTaskStatusSendsNotification() {
        Long taskId = 100L;
        Long projectId = 10L;
        String taskTitle = "Test Task";
        Long assigneeId = 5L;
        String actorUsername = "admin";
        Long actorUserId = 1L;

        Task existingTask = Task.builder()
                .id(taskId)
                .projectId(projectId)
                .title(taskTitle)
                .status(Task.Status.TODO)
                .assigneeId(assigneeId)
                .build();

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(existingTask));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(projectRepository.existsById(projectId)).thenReturn(true);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(Project.builder().id(projectId).name("西安地铁项目").build()));
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(java.util.Collections.emptyList());

        User actor = new User();
        actor.setId(actorUserId);
        when(userRepository.findByUsername(actorUsername)).thenReturn(Optional.of(actor));

        TaskDTO expectedDto = TaskDTO.builder().id(taskId).build();
        when(taskDtoMapper.toDTO(any(Task.class), any(), any())).thenReturn(expectedDto);

        taskService.updateTaskStatus(taskId, Task.Status.REVIEW, actorUsername);

        verify(notificationService).notifyTaskStatusChanged(
                eq(projectId), eq(taskId), eq(taskTitle),
                eq("待处理"), eq("审核中"),
                eq(assigneeId), eq(actorUserId)
        );
    }
}
