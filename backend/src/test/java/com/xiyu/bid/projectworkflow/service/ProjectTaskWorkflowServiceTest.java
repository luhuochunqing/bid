package com.xiyu.bid.projectworkflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.entity.Task;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.project.notification.ProjectNotificationService;
import com.xiyu.bid.projectworkflow.dto.ProjectDocumentDTO;
import com.xiyu.bid.projectworkflow.dto.ProjectTaskStatusUpdateRequest;
import com.xiyu.bid.projectworkflow.dto.ProjectTaskViewDTO;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.repository.TaskRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.task.dto.TaskDeliverableDTO;
import com.xiyu.bid.task.entity.TaskDeliverable;
import com.xiyu.bid.task.repository.TaskDeliverableRepository;
import com.xiyu.bid.task.service.TaskHistoryRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private TaskDeliverableRepository taskDeliverableRepository;
    private ProjectDocumentRepository projectDocumentRepository;

    @BeforeEach
    void setUp() {
        taskRepository = mock(TaskRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        guardService = mock(ProjectWorkflowGuardService.class);
        TaskHistoryRecorder taskHistoryRecorder = mock(TaskHistoryRecorder.class);
        ProjectTaskDeliverableCollector deliverableCollector = mock(ProjectTaskDeliverableCollector.class);
        NotificationApplicationService notificationService = mock(NotificationApplicationService.class);
        ProjectNotificationService projectNotificationService = mock(ProjectNotificationService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        taskDeliverableRepository = mock(TaskDeliverableRepository.class);
        projectDocumentRepository = mock(ProjectDocumentRepository.class);

        service = new ProjectTaskWorkflowService(
                guardService,
                taskRepository,
                userRepository,
                objectMapper,
                taskHistoryRecorder,
                deliverableCollector,
                notificationService,
                projectNotificationService,
                taskDeliverableRepository,
                projectDocumentRepository
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
                .hasMessageContaining("退回待办必须填写 reviewComment");
    }

    @Test
    void updateProjectTaskStatus_invalidTransitionFromTodoToCompleted_throws422() {
        Task task = Task.builder().id(99L).projectId(10L).title("T").status(Task.Status.TODO).build();
        when(guardService.requireTask(10L, 99L)).thenReturn(task);
        ProjectTaskStatusUpdateRequest req = ProjectTaskStatusUpdateRequest.builder()
                .status(ProjectTaskStatusUpdateRequest.Status.COMPLETED)
                .build();

        assertThatThrownBy(() -> service.updateProjectTaskStatus(10L, 99L, req, "user"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("不允许从");
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
        when(taskDeliverableRepository.countByTaskId(2L)).thenReturn(1L);
        ProjectTaskStatusUpdateRequest req = ProjectTaskStatusUpdateRequest.builder()
                .status(ProjectTaskStatusUpdateRequest.Status.REVIEW)
                .reviewComment("不应该被保存")
                .completionNotes("已完成，请审核")
                .build();

        ProjectTaskViewDTO dto = service.updateProjectTaskStatus(10L, 2L, req, "assignee");

        assertThat(dto.getStatus()).isEqualTo("review");
        assertThat(dto.getExtendedFields()).doesNotContainKey("lastRejectReason");
    }

    // ---------- CO-458: 提交审核验证 ----------

    @Test
    void co458_submitReviewWithoutDeliverables_throws422() {
        Task task = Task.builder().id(3L).projectId(10L).title("T").status(Task.Status.TODO).build();
        when(guardService.requireTask(10L, 3L)).thenReturn(task);
        when(taskDeliverableRepository.countByTaskId(3L)).thenReturn(0L);
        ProjectTaskStatusUpdateRequest req = ProjectTaskStatusUpdateRequest.builder()
                .status(ProjectTaskStatusUpdateRequest.Status.REVIEW)
                .completionNotes("已完成")
                .build();

        assertThatThrownBy(() -> service.updateProjectTaskStatus(10L, 3L, req, "assignee"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("提交审核时必须上传交付物");
    }

    @Test
    void co458_submitReviewWithoutCompletionNotes_throws422() {
        Task task = Task.builder().id(4L).projectId(10L).title("T").status(Task.Status.TODO).build();
        when(guardService.requireTask(10L, 4L)).thenReturn(task);
        when(taskDeliverableRepository.countByTaskId(4L)).thenReturn(1L);
        ProjectTaskStatusUpdateRequest req = ProjectTaskStatusUpdateRequest.builder()
                .status(ProjectTaskStatusUpdateRequest.Status.REVIEW)
                .completionNotes("  ")
                .build();

        assertThatThrownBy(() -> service.updateProjectTaskStatus(10L, 4L, req, "assignee"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("提交审核时必须填写完成情况");
    }

    @Test
    void co458_submitReviewWithDeliverablesAndNotes_persistsCompletionNotes() {
        Task task = Task.builder().id(5L).projectId(10L).title("T").status(Task.Status.TODO).build();
        when(guardService.requireTask(10L, 5L)).thenReturn(task);
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));
        when(taskDeliverableRepository.countByTaskId(5L)).thenReturn(2L);
        when(taskDeliverableRepository.findByTaskIdOrderByCreatedAtDesc(5L)).thenReturn(List.of());
        ProjectTaskStatusUpdateRequest req = ProjectTaskStatusUpdateRequest.builder()
                .status(ProjectTaskStatusUpdateRequest.Status.REVIEW)
                .completionNotes("  已完成全部交付内容，请审核  ")
                .build();

        ProjectTaskViewDTO dto = service.updateProjectTaskStatus(10L, 5L, req, "assignee");

        assertThat(dto.getStatus()).isEqualTo("review");
        assertThat(dto.getCompletionNotes()).isEqualTo("已完成全部交付内容，请审核");
    }

    // ---------- CO-460: toTaskView 必须返回 deliverables / attachments（治本，对齐独立任务）----------

    @Test
    void co460_toTaskView_returnsDeliverablesFromTaskDeliverableRepository() {
        Task task = Task.builder().id(500L).projectId(1L).title("带交付物的任务").build();
        TaskDeliverable d1 = TaskDeliverable.builder().id(9001L).taskId(500L).name("交付物.docx")
                .deliverableType(TaskDeliverable.DeliverableType.DOCUMENT).storagePath("/files/9001").version(1).build();
        when(taskDeliverableRepository.findByTaskIdOrderByCreatedAtDesc(500L)).thenReturn(List.of(d1));
        ProjectTaskViewDTO dto = service.toTaskView(task);
        assertThat(dto.getDeliverables()).hasSize(1);
        assertThat(dto.getDeliverables().get(0).getName()).isEqualTo("交付物.docx");
        assertThat(dto.getDeliverableCount()).isEqualTo(1);
    }

    @Test
    void co460_toTaskView_returnsAttachmentsFromProjectDocumentRepository() {
        Task task = Task.builder().id(501L).projectId(1L).title("带附件的任务").build();
        ProjectDocument attach = ProjectDocument.builder().id(8001L).projectId(1L).name("附件.pdf")
                .documentCategory("TASK_ATTACHMENT").linkedEntityType("TASK").linkedEntityId(501L).fileUrl("/files/8001").build();
        ProjectDocument irrelevant = ProjectDocument.builder().id(8002L).projectId(1L).name("标书.pdf")
                .documentCategory("BID_DOCUMENT").linkedEntityType("TASK").linkedEntityId(501L).build();
        when(projectDocumentRepository.findByLinkedEntityTypeAndLinkedEntityIdOrderByCreatedAtDesc(eq("TASK"), eq(501L)))
                .thenReturn(List.of(attach, irrelevant));
        ProjectTaskViewDTO dto = service.toTaskView(task);
        assertThat(dto.getAttachments()).hasSize(1);
        assertThat(dto.getAttachments().get(0).getName()).isEqualTo("附件.pdf");
        assertThat(dto.getAttachments()).allSatisfy(d -> assertThat(d.getDocumentCategory()).isEqualTo("TASK_ATTACHMENT"));
    }

    @Test
    void co460_toTaskView_emptyListsWhenNoDeliverablesOrAttachments() {
        Task task = Task.builder().id(502L).projectId(1L).title("空任务").build();
        when(taskDeliverableRepository.findByTaskIdOrderByCreatedAtDesc(502L)).thenReturn(List.of());
        when(projectDocumentRepository.findByLinkedEntityTypeAndLinkedEntityIdOrderByCreatedAtDesc(eq("TASK"), eq(502L))).thenReturn(List.of());
        ProjectTaskViewDTO dto = service.toTaskView(task);
        assertThat(dto.getDeliverables()).isEmpty();
        assertThat(dto.getAttachments()).isEmpty();
        assertThat(dto.getDeliverableCount()).isEqualTo(0);
    }

    @Test
    void co460_approve_returnsDtoWithDeliverables() {
        Task task = Task.builder().id(600L).projectId(10L).title("待审核任务").status(Task.Status.REVIEW).build();
        when(guardService.requireTask(10L, 600L)).thenReturn(task);
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));
        when(taskDeliverableRepository.findByTaskIdOrderByCreatedAtDesc(600L))
                .thenReturn(List.of(TaskDeliverable.builder().id(9100L).taskId(600L).name("成果.docx").deliverableType(TaskDeliverable.DeliverableType.DOCUMENT).build()));
        ProjectTaskStatusUpdateRequest req = ProjectTaskStatusUpdateRequest.builder().status(ProjectTaskStatusUpdateRequest.Status.COMPLETED).build();
        ProjectTaskViewDTO dto = service.updateProjectTaskStatus(10L, 600L, req, "reviewer");
        assertThat(dto.getStatus()).isEqualTo("done");
        assertThat(dto.getDeliverables()).hasSize(1);
        assertThat(dto.getDeliverables().get(0).getName()).isEqualTo("成果.docx");
    }
}
