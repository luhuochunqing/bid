package com.xiyu.bid.projectworkflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.entity.Task;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.projectworkflow.dto.ProjectTaskViewDTO;
import com.xiyu.bid.repository.TaskRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.task.service.TaskHistoryRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * CO-370: 验证 {@link ProjectTaskWorkflowService#toTaskView(Task)} 映射包含
 * {@code completionNotes} 字段。
 *
 * <p>状态流转（TODO→REVIEW / REVIEW→COMPLETED / REVIEW→TODO 驳回）后前端用后端返回
 * 覆盖内存中的 task 对象，若 DTO 缺失 {@code completionNotes}，已填写的完成情况说明
 * 会被空值覆盖。
 */
class ProjectTaskWorkflowServiceTest {

    private ProjectTaskWorkflowService service;

    @BeforeEach
    void setUp() {
        TaskRepository taskRepository = mock(TaskRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        ProjectWorkflowGuardService guardService = mock(ProjectWorkflowGuardService.class);
        TaskHistoryRecorder taskHistoryRecorder = mock(TaskHistoryRecorder.class);
        ProjectTaskDeliverableCollector deliverableCollector = mock(ProjectTaskDeliverableCollector.class);
        NotificationApplicationService notificationService = mock(NotificationApplicationService.class);
        ObjectMapper objectMapper = new ObjectMapper();

        service = new ProjectTaskWorkflowService(
                guardService,
                taskRepository,
                userRepository,
                objectMapper,
                taskHistoryRecorder,
                deliverableCollector,
                notificationService
        );
    }

    @Test
    void toTaskView_mapsCompletionNotesFromTaskEntity() {
        Task task = Task.builder()
                .id(100L)
                .projectId(1L)
                .title("任务A")
                .completionNotes("已完成全部交付内容，请审核")
                .build();

        ProjectTaskViewDTO dto = service.toTaskView(task);

        assertThat(dto.getCompletionNotes()).isEqualTo("已完成全部交付内容，请审核");
    }

    @Test
    void toTaskView_completionNotesIsNullWhenTaskEntityHasNone() {
        Task task = Task.builder()
                .id(101L)
                .projectId(1L)
                .title("任务B")
                .completionNotes(null)
                .build();

        ProjectTaskViewDTO dto = service.toTaskView(task);

        assertThat(dto.getCompletionNotes()).isNull();
    }
}
