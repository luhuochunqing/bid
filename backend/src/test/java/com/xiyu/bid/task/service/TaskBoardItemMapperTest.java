package com.xiyu.bid.task.service;

import com.xiyu.bid.entity.Task;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.project.entity.BidDocumentReviewEntity;
import com.xiyu.bid.task.dto.TaskBoardItemDTO;
import com.xiyu.bid.task.entity.TaskDeliverable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TaskBoardItemMapper 纯核心测试。
 * 验证状态映射、字段映射和可见性判断。
 */
class TaskBoardItemMapperTest {

    @Test
    void mapTaskStatus_todo_mapsToTodo() {
        Task task = Task.builder().id(1L).projectId(10L).status(Task.Status.TODO).assigneeId(100L).build();
        TaskBoardItemDTO dto = TaskBoardItemMapper.fromTask(task, Map.of(10L, "项目"), "执行人", null);
        assertThat(dto.getStatus()).isEqualTo("TODO");
    }

    @Test
    void mapTaskStatus_review_mapsToReview() {
        Task task = Task.builder().id(1L).projectId(10L).status(Task.Status.REVIEW).assigneeId(100L).build();
        TaskBoardItemDTO dto = TaskBoardItemMapper.fromTask(task, Map.of(10L, "项目"), "执行人", null);
        assertThat(dto.getStatus()).isEqualTo("REVIEW");
    }

    @Test
    void mapTaskStatus_completed_mapsToCompleted() {
        Task task = Task.builder().id(1L).projectId(10L).status(Task.Status.COMPLETED).assigneeId(100L).build();
        TaskBoardItemDTO dto = TaskBoardItemMapper.fromTask(task, Map.of(10L, "项目"), "执行人", null);
        assertThat(dto.getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void mapTaskStatus_null_mapsToTodo() {
        Task task = Task.builder().id(1L).projectId(10L).status(null).assigneeId(100L).build();
        TaskBoardItemDTO dto = TaskBoardItemMapper.fromTask(task, Map.of(10L, "项目"), "执行人", null);
        assertThat(dto.getStatus()).isEqualTo("TODO");
    }

    @Test
    void fromTask_mapsAssigneeId() {
        Task task = Task.builder().id(1L).projectId(10L).status(Task.Status.TODO).assigneeId(42L).build();
        TaskBoardItemDTO dto = TaskBoardItemMapper.fromTask(task, Map.of(10L, "项目"), "执行人", null);
        assertThat(dto.getAssigneeId()).isEqualTo(42L);
    }

    @Test
    void fromTask_mapsCompletionNotes() {
        Task task = Task.builder().id(1L).projectId(10L).status(Task.Status.REVIEW).assigneeId(100L)
                .completionNotes("已完成，请审核").build();
        TaskBoardItemDTO dto = TaskBoardItemMapper.fromTask(task, Map.of(10L, "项目"), "执行人", null);
        assertThat(dto.getCompletionNotes()).isEqualTo("已完成，请审核");
    }

    @Test
    void fromTask_mapsDeliverables() {
        Task task = Task.builder().id(1L).projectId(10L).status(Task.Status.REVIEW).assigneeId(100L).build();
        TaskDeliverable del = TaskDeliverable.builder()
                .id(10L).taskId(1L).name("投标文件.pdf")
                .deliverableType(TaskDeliverable.DeliverableType.DOCUMENT)
                .version(1).uploaderName("张三").build();
        TaskBoardItemDTO dto = TaskBoardItemMapper.fromTask(task, Map.of(10L, "项目"), "执行人", List.of(del));
        assertThat(dto.getDeliverables()).hasSize(1);
        assertThat(dto.getDeliverables().get(0).getName()).isEqualTo("投标文件.pdf");
    }

    @Test
    void fromTask_nullDeliverables_returnsEmptyList() {
        Task task = Task.builder().id(1L).projectId(10L).status(Task.Status.TODO).assigneeId(100L).build();
        TaskBoardItemDTO dto = TaskBoardItemMapper.fromTask(task, Map.of(10L, "项目"), "执行人", null);
        assertThat(dto.getDeliverables()).isEmpty();
    }

    @Test
    void fromBidReview_mapsReviewerId() {
        BidDocumentReviewEntity review = BidDocumentReviewEntity.builder()
                .id(20L).projectId(10L).reviewerId(99L).submittedBy(2L).status("REVIEWING").build();
        TaskBoardItemDTO dto = TaskBoardItemMapper.fromBidReview(review, Map.of(10L, "项目"), Map.of(2L, "提交人"));
        assertThat(dto.getReviewerId()).isEqualTo(99L);
    }

    @Test
    void fromBidReview_mapsTargetUrlToDraftingPage() {
        BidDocumentReviewEntity review = BidDocumentReviewEntity.builder()
                .id(20L).projectId(10L).reviewerId(99L).submittedBy(2L).status("REVIEWING").build();
        TaskBoardItemDTO dto = TaskBoardItemMapper.fromBidReview(review, Map.of(10L, "项目"), Map.of(2L, "提交人"));
        assertThat(dto.getTargetUrl()).isEqualTo("/project/10/drafting");
    }

    @Test
    void isVisibleTask_normal_returnsTrue() {
        Task normal = Task.builder().id(1L).status(Task.Status.TODO).build();
        assertThat(TaskBoardItemMapper.isVisibleTask(normal)).isTrue();
    }
}
