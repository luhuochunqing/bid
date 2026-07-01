package com.xiyu.bid.task.service;

import com.xiyu.bid.entity.Task;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.project.entity.BidDocumentReviewEntity;
import com.xiyu.bid.task.dto.TaskBoardItemDTO;
import com.xiyu.bid.task.dto.TaskDeliverableDTO;
import com.xiyu.bid.task.dto.TaskDeliverableAssembler;
import com.xiyu.bid.task.entity.TaskDeliverable;

import java.util.List;
import java.util.Map;

/**
 * 任务看板条目映射器（纯核心）。
 *
 * <p>将 task 表记录和 bid_document_review 记录统一映射为 TaskBoardItemDTO，
 * 不包含任何 IO、数据库或时间副作用。</p>
 */
final class TaskBoardItemMapper {

    static final String TYPE_TASK = "TASK";
    static final String TYPE_BID_REVIEW = "BID_REVIEW";

    private static final String STATUS_TODO = "TODO";
    private static final String STATUS_REVIEW = "REVIEW";
    private static final String STATUS_COMPLETED = "COMPLETED";

    private static final String BID_REVIEW_STATUS_REVIEWING = "REVIEWING";
    private static final String BID_REVIEW_STATUS_APPROVED = "APPROVED";
    private static final String BID_REVIEW_STATUS_REJECTED = "REJECTED";

    private TaskBoardItemMapper() {
    }

    /**
     * 将任务记录映射为看板条目。
     */
    static TaskBoardItemDTO fromTask(Task task, Map<Long, String> projectNames, String assigneeName,
                                      List<TaskDeliverable> deliverables) {
        List<TaskDeliverableDTO> deliverableDTOs = deliverables == null
                ? List.of()
                : deliverables.stream().map(TaskDeliverableAssembler::toDTO).toList();
        return TaskBoardItemDTO.builder()
                .id(task.getId())
                .type(TYPE_TASK)
                .title(task.getTitle())
                .description(task.getDescription())
                .status(mapTaskStatus(task.getStatus()))
                .priority(task.getPriority() == null ? null : task.getPriority().name())
                .dueDate(task.getDueDate())
                .projectId(task.getProjectId())
                .projectName(projectNames.getOrDefault(task.getProjectId(), ""))
                .targetUrl(null)
                .assigneeName(assigneeName)
                .assigneeId(task.getAssigneeId())
                .completionNotes(task.getCompletionNotes())
                .deliverables(deliverableDTOs)
                .build();
    }

    /**
     * 将标书审核记录映射为看板条目。
     */
    static TaskBoardItemDTO fromBidReview(BidDocumentReviewEntity review,
                                          Map<Long, String> projectNames,
                                          Map<Long, String> userNames) {
        String projectName = projectNames.getOrDefault(review.getProjectId(), "");
        String submitterName = userNames.getOrDefault(review.getSubmittedBy(), "");
        return TaskBoardItemDTO.builder()
                .id(review.getId())
                .type(TYPE_BID_REVIEW)
                .title("标书审核：" + projectName)
                .description(buildReviewDescription(submitterName, review.getRejectReason()))
                .status(mapBidReviewStatus(review.getStatus()))
                .priority("HIGH")
                .dueDate(review.getReviewedAt())
                .projectId(review.getProjectId())
                .projectName(projectName)
                .targetUrl("/project/" + review.getProjectId() + "/drafting")
                .submitterName(submitterName)
                .reviewerId(review.getReviewerId())
                .build();
    }

    private static String mapTaskStatus(Task.Status status) {
        if (status == null) {
            return STATUS_TODO;
        }
        return switch (status) {
            case TODO -> STATUS_TODO;
            case REVIEW -> STATUS_REVIEW;
            case COMPLETED -> STATUS_COMPLETED;
        };
    }

    private static String mapBidReviewStatus(String status) {
        if (BID_REVIEW_STATUS_REVIEWING.equalsIgnoreCase(status)) {
            return STATUS_REVIEW;
        }
        if (BID_REVIEW_STATUS_APPROVED.equalsIgnoreCase(status)
                || BID_REVIEW_STATUS_REJECTED.equalsIgnoreCase(status)) {
            return STATUS_COMPLETED;
        }
        return STATUS_REVIEW;
    }

    private static String buildReviewDescription(String submitterName, String rejectReason) {
        StringBuilder sb = new StringBuilder();
        if (submitterName != null && !submitterName.isBlank()) {
            sb.append("提交人：").append(submitterName);
        }
        if (rejectReason != null && !rejectReason.isBlank()) {
            if (!sb.isEmpty()) {
                sb.append(" | ");
            }
            sb.append("驳回原因：").append(rejectReason);
        }
        return sb.toString();
    }

    /**
     * 判断标书审核是否仍在进行中（应出现在看板里）。
     */
    static boolean isActiveBidReview(BidDocumentReviewEntity review) {
        return review != null && BID_REVIEW_STATUS_REVIEWING.equalsIgnoreCase(review.getStatus());
    }

    /**
     * 判断任务是否应出现在看板里（三态模型下所有任务均展示）.
     */
    static boolean isVisibleTask(Task task) {
        return task != null;
    }

    static String fullNameOf(User user) {
        if (user == null) {
            return "";
        }
        String fullName = user.getFullName();
        return fullName == null || fullName.isBlank() ? user.getUsername() : fullName;
    }
}
