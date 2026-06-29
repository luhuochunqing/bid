package com.xiyu.bid.projectworkflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.entity.Task;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.projectworkflow.dto.ProjectTaskStatusUpdateRequest;
import com.xiyu.bid.projectworkflow.dto.ProjectTaskViewDTO;
import com.xiyu.bid.repository.TaskRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.task.service.TaskHistoryRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
    private TaskRepository taskRepository;
    private ProjectWorkflowGuardService guardService;

    @BeforeEach
    void setUp() {
        taskRepository = mock(TaskRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        guardService = mock(ProjectWorkflowGuardService.class);
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

    // CO-413: REVIEW → TODO（驳回）时 reviewComment 必填，且持久化到 extendedFields.lastRejectReason
    @Test
    void updateProjectTaskStatus_rejectFromReviewWithoutReviewComment_throws422() {
        Task task = Task.builder().id(1L).projectId(10L).title("T").status(Task.Status.REVIEW).build();
        when(guardService.requireTask(10L, 1L)).thenReturn(task);
        ProjectTaskStatusUpdateRequest req = ProjectTaskStatusUpdateRequest.builder()
                .status(ProjectTaskStatusUpdateRequest.Status.TODO)
                .reviewComment("  ")
                .build();

        assertThatThrownBy(() -> service.updateProjectTaskStatus(10L, 1L, req, "reviewer"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("驳回任务时必须填写驳回原因");
    }

    @Test
    void updateProjectTaskStatus_rejectFromReviewWithReviewComment_persistsLastRejectReason() {
        Task task = Task.builder().id(1L).projectId(10L).title("T").status(Task.Status.REVIEW).build();
        when(guardService.requireTask(10L, 1L)).thenReturn(task);
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));
        ProjectTaskStatusUpdateRequest req = ProjectTaskStatusUpdateRequest.builder()
                .status(ProjectTaskStatusUpdateRequest.Status.TODO)
                .reviewComment("内容不完整，请补充技术参数")
                .build();

        ProjectTaskViewDTO dto = service.updateProjectTaskStatus(10L, 1L, req, "reviewer");

        assertThat(dto.getStatus()).isEqualTo("todo");
        assertThat(dto.getExtendedFields()).containsEntry("lastRejectReason", "内容不完整，请补充技术参数");
        assertThat(dto.getExtendedFields()).containsKey("lastRejectedAt");
    }

    @Test
    void updateProjectTaskStatus_nonRejectTransitionDoesNotWriteLastRejectReason() {
        Task task = Task.builder().id(2L).projectId(10L).title("T").status(Task.Status.TODO).build();
        when(guardService.requireTask(10L, 2L)).thenReturn(task);
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));
        ProjectTaskStatusUpdateRequest req = ProjectTaskStatusUpdateRequest.builder()
                .status(ProjectTaskStatusUpdateRequest.Status.REVIEW)
                .reviewComment("不应该被保存")
                .build();

        ProjectTaskViewDTO dto = service.updateProjectTaskStatus(10L, 2L, req, "assignee");

        assertThat(dto.getStatus()).isEqualTo("review");
        assertThat(dto.getExtendedFields()).doesNotContainKey("lastRejectReason");
    }
}
