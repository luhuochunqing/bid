package com.xiyu.bid.task.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 独立任务看板卡片项。
 *
 * <p>聚合来源包括普通项目任务（task 表）和项目工作流待办（如标书审核 bid_document_review）。
 * 前端按 {@code status} 字段分到不同看板列，按 {@code type} 区分跳转与操作。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskBoardItemDTO {

    /** 原始业务 ID（task.id 或 bid_document_review.id）。 */
    private Long id;

    /** 条目类型：TASK 或 BID_REVIEW。 */
    private String type;

    /** 卡片标题。 */
    private String title;

    /** 卡片描述。 */
    private String description;

    /** 看板列状态：TODO / REVIEW / COMPLETED。 */
    private String status;

    /** 优先级（TASK 时来自任务；BID_REVIEW 时统一为 HIGH）。 */
    private String priority;

    /** 截止时间。 */
    private LocalDateTime dueDate;

    /** 所属项目 ID。 */
    private Long projectId;

    /** 所属项目名称。 */
    private String projectName;

    /** 点击卡片后跳转的前端路径。 */
    private String targetUrl;

    /** 任务负责人姓名（TASK 时有值）。 */
    private String assigneeName;

    /** 任务负责人用户 ID（TASK 时有值，前端用于判断是否是当前用户）。 */
    private Long assigneeId;

    /** 标书审核提交人姓名（BID_REVIEW 时有值）。 */
    private String submitterName;

    /** 标书审核人用户 ID（BID_REVIEW 时有值，前端用于判断审核权限）。 */
    private Long reviewerId;

    /** Completion notes filled by assignee when submitting for review (TASK 时有值)。 */
    private String completionNotes;

    /** Task deliverables uploaded by assignee (TASK 时有值)。 */
    private java.util.List<TaskDeliverableDTO> deliverables;
}
