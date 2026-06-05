package com.xiyu.bid.approval.dto;

import com.xiyu.bid.approval.enums.ApprovalActionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 审批操作记录DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalActionDTO {

    /**
     * 操作ID
     */
    private UUID id;

    /**
     * 操作类型
     */
    private ApprovalActionType actionType;

    /**
     * 操作人ID
     */
    private Long actorId;

    /**
     * 操作人名称
     */
    private String actorName;

    /**
     * 操作意见
     */
    private String comment;

    /**
     * 操作时间
     */
    private LocalDateTime actionTime;

    /**
     * 操作前状态
     */
    private String previousStatus;

    /**
     * 操作后状态
     */
    private String newStatus;
}
