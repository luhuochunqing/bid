package com.xiyu.bid.approval.dto;

import com.xiyu.bid.approval.enums.ApprovalStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 审批详情DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalDetailDTO {

    /**
     * 审批请求ID
     */
    private UUID id;

    /**
     * 项目ID
     */
    private Long projectId;

    /**
     * 项目名称
     */
    private String projectName;

    /**
     * 审批类型
     */
    private String approvalType;

    /**
     * 审批状态
     */
    private ApprovalStatus status;

    /**
     * 状态描述
     */
    private String statusDescription;

    /**
     * 申请人ID
     */
    private Long requesterId;

    /**
     * 申请人名称
     */
    private String requesterName;

    /**
     * 当前审批人ID
     */
    private Long currentApproverId;

    /**
     * 当前审批人名称
     */
    private String currentApproverName;

    /**
     * 优先级
     */
    private Integer priority;

    /**
     * 标题
     */
    private String title;

    /**
     * 描述
     */
    private String description;

    /**
     * 提交时间
     */
    private LocalDateTime submittedAt;

    /**
     * 完成时间
     */
    private LocalDateTime completedAt;

    /**
     * 预期完成时间
     */
    private LocalDateTime dueDate;

    /**
     * 是否已读
     */
    private Boolean isRead;

    /**
     * 是否超期
     */
    private Boolean isOverdue;

    /**
     * 是否临近截止
     */
    private Boolean isNearDueDate;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 操作记录列表
     */
    private List<ApprovalActionDTO> actions;

    /**
     * 耗时（小时）
     */
    private Long processingHours;
}
