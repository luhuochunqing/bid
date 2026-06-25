package com.xiyu.bid.task.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.entity.Task;
import com.xiyu.bid.repository.TaskRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.service.RoleProfileService;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.project.notification.ProjectNotificationService;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.task.dto.TaskDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Verifies that the Markdown `content` field round-trips through TaskDTO ↔ Task entity
 * inside TaskService. Storage policy: content is stored as raw Markdown text; frontend
 * is responsible for render-time HTML sanitization (DOMPurify or equivalent).
 *
 * <p>We mirror {@link TaskServiceProjectAccessTest}'s mock-based pattern rather than
 * booting a full Spring context – the goal here is to catch mapper gaps in
 * {@code createTask} / {@code getTaskById}, which is a pure in-memory concern.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TaskService content 字段映射测试")
class TaskContentPersistenceTest {

    private static final String MARKDOWN = "# 任务详情\n- 步骤 1\n- 步骤 2";

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
    private TaskPermissionGuard taskPermissionGuard;

    private TaskService taskService;

    @BeforeEach
    void setUp() {
        when(projectRepository.existsById(any(Long.class))).thenReturn(true);
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
                new TaskDtoMapper(new ObjectMapper(), projectDocumentRepository),
                taskHistoryRecorder,
                notificationService,
                userRepository,
                taskPermissionGuard
        );
    }

    @Test
    @DisplayName("createTask 将 TaskDTO.content 写入 Task 实体并在返回 DTO 中保留")
    void createTaskPropagatesContentToEntityAndBackToDto() {
        when(projectRepository.existsById(10L)).thenReturn(true);
        // Echo whatever the service asks to save, but with an id and the original content preserved.
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
            Task toSave = invocation.getArgument(0);
            toSave.setId(99L);
            return toSave;
        });

        TaskDTO input = TaskDTO.builder()
                .projectId(10L)
                .title("写标书")
                .content(MARKDOWN)
                .build();

        TaskDTO created = taskService.createTask(input);

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        org.mockito.Mockito.verify(taskRepository).save(captor.capture());
        assertThat(captor.getValue().getContent())
                .as("Task entity must receive TaskDTO.content verbatim")
                .isEqualTo(MARKDOWN);
        assertThat(created.getContent())
                .as("returned DTO must echo content back to caller")
                .isEqualTo(MARKDOWN);
    }

    @Test
    @DisplayName("getTaskById 读出的 DTO 包含实体 content")
    void getTaskByIdExposesContent() {
        when(projectRepository.existsById(10L)).thenReturn(true);
        Task stored = Task.builder()
                .id(1L)
                .projectId(10L)
                .title("写标书")
                .content(MARKDOWN)
                .status(Task.Status.TODO)
                .priority(Task.Priority.MEDIUM)
                .build();
        when(taskRepository.findById(1L)).thenReturn(Optional.of(stored));

        TaskDTO loaded = taskService.getTaskById(1L);

        assertThat(loaded.getContent()).isEqualTo(MARKDOWN);
    }
}
