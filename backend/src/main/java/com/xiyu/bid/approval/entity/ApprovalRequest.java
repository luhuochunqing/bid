package com.xiyu.bid.approval.entity;

import com.xiyu.bid.approval.enums.ApprovalStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 审批请求实体
 */
@Entity
@Table(name = "approval_requests", indexes = {
    @Index(name = "idx_project_id", columnList = "projectId"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_requester_id", columnList = "requesterId"),
    @Index(name = "idx_created_at", columnList = "createdAt"),
    @Index(name = "idx_approval_type", columnList = "approvalType")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalRequest {

    /**
     * 唯一标识
     */
    @Id
    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    @Builder.Default
    private UUID id = UUID.randomUUID();

    /**
     * 关联的项目ID
     */
    @Column(name = "project_id", nullable = false)
    private Long projectId;

    /**
     * 项目名称 (冗余字段)
     */
    @Column(name = "project_name", length = 200)
    private String projectName;

    /**
     * 审批类型
     */
    @Column(name = "approval_type", nullable = false, length = 50)
    private String approvalType;

    /**
     * 审批状态
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ApprovalStatus status = ApprovalStatus.PENDING;

    /**
     * 申请人ID
     */
    @Column(name = "requester_id", nullable = false)
    private Long requesterId;

    /**
     * 申请人名称
     */
    @Column(name = "requester_name", nullable = false, length = 100)
    private String requesterName;

    /**
     * 当前审批人ID
     */
    @Column(name = "current_approver_id")
    private Long currentApproverId;

    /**
     * 当前审批人名称
     */
    @Column(name = "current_approver_name", length = 100)
    private String currentApproverName;

    /**
     * 优先级
     */
    @Column(name = "priority", nullable = false)
    @Builder.Default
    private Integer priority = 0;

    /**
     * 标题
     */
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /**
     * 描述/说明
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * 附件ID列表 (JSON格式存储)
     */
    @Column(name = "attachment_ids", columnDefinition = "TEXT")
    private String attachmentIds;

    /**
     * 提交时间
     */
    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    /**
     * 审批完成时间
     */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /**
     * 预期完成时间
     */
    @Column(name = "due_date")
    private LocalDateTime dueDate;

    /**
     * 是否已读 (审批人)
     */
    @Column(name = "is_read")
    @Builder.Default
    private Boolean isRead = false;

    /**
     * 关联的操作记录 - 通过查询获取，不作为持久化字段
     */
    @Transient
    @Builder.Default
    private List<ApprovalAction> actions = new ArrayList<>();

    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * 创建人ID
     */
    @Column(name = "created_by")
    private Long createdBy;

    /**
     * 更新人ID
     */
    @Column(name = "updated_by")
    private Long updatedBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (submittedAt == null) {
            submittedAt = LocalDateTime.now();
        }
        if (status == null) {
            status = ApprovalStatus.PENDING;
        }
        if (priority == null) {
            priority = 0;
        }
        if (isRead == null) {
            isRead = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * 检查是否可以审批
     */
    public boolean canBeApproved() {
        return status == ApprovalStatus.PENDING;
    }

    /**
     * 检查是否可以被申请人取消
     */
    public boolean canBeCancelledBy(Long userId) {
        return status == ApprovalStatus.PENDING && requesterId.equals(userId);
    }

    /**
     * 检查是否已超期
     */
    public boolean isOverdue() {
        return dueDate != null 
            && LocalDateTime.now().isAfter(dueDate) 
            && status == ApprovalStatus.PENDING;
    }

    /**
     * 检查是否临近截止 (24小时内)
     */
    public boolean isNearDueDate() {
        return dueDate != null
            && LocalDateTime.now().plusHours(24).isAfter(dueDate)
            && LocalDateTime.now().isBefore(dueDate)
            && status == ApprovalStatus.PENDING;
    }
}
