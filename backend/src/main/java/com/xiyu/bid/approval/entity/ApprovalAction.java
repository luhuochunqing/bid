package com.xiyu.bid.approval.entity;

import com.xiyu.bid.approval.enums.ApprovalActionType;
import com.xiyu.bid.approval.enums.ApprovalStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 审批操作记录实体
 * 记录审批流程中的所有操作，操作记录一旦创建不可修改
 */
@Entity
@Table(name = "approval_actions", indexes = {
    @Index(name = "idx_approval_request_id", columnList = "approval_request_id"),
    @Index(name = "idx_action_time", columnList = "actionTime"),
    @Index(name = "idx_actor_id", columnList = "actorId")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalAction {

    /**
     * 唯一标识
     */
    @Id
    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    @Builder.Default
    private UUID id = UUID.randomUUID();

    /**
     * 关联的审批请求ID
     */
    @Column(name = "approval_request_id", nullable = false)
    private UUID approvalRequestId;

    /**
     * 操作类型
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false)
    private ApprovalActionType actionType;

    /**
     * 操作人ID
     */
    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    /**
     * 操作人名称 (冗余字段，防止用户删除后无法追溯)
     */
    @Column(name = "actor_name", nullable = false, length = 100)
    private String actorName;

    /**
     * 操作意见或理由
     */
    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    /**
     * 操作时间
     */
    @Column(name = "action_time", nullable = false)
    private LocalDateTime actionTime;

    /**
     * 操作前的状态
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 20)
    private ApprovalStatus previousStatus;

    /**
     * 操作后的状态
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", length = 20)
    private ApprovalStatus newStatus;

    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (actionTime == null) {
            actionTime = LocalDateTime.now();
        }
    }
}
