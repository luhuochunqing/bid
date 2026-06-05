// Input: project_closure 表行
// Output: JPA 实体 - WS-F 结项 + 保证金退回登记 + 审核流程（蓝图 §3.3.1.6）
// Pos: entity/ - 持久化模型
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "project_closure")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectClosure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    /** 保证金退回状态（NOT_RETURNED/FULLY_RETURNED/TRANSFERRED_TO_FEE/PARTIAL_RETURN_PARTIAL_TRANSFER/NA）。 */
    @Column(name = "deposit_return_status", length = 32)
    private String depositReturnStatus;

    /** 保证金退回日期（FULLY_RETURNED 时必填）。 */
    @Column(name = "deposit_return_date")
    private LocalDateTime depositReturnDate;

    /** 保证金退回凭证文档 ID。 */
    @Column(name = "deposit_return_evidence_id")
    private Long depositReturnEvidenceId;

    /** 转平台服务费金额（TRANSFERRED_TO_FEE/PARTIAL_RETURN_PARTIAL_TRANSFER 时）。 */
    @Column(name = "transfer_amount", precision = 18, scale = 2)
    private BigDecimal transferAmount;

    /** 实际退回金额（PARTIAL_RETURN_PARTIAL_TRANSFER 时）。 */
    @Column(name = "returned_amount", precision = 18, scale = 2)
    private BigDecimal returnedAmount;

    @Column(name = "archive_location", length = 512)
    private String archiveLocation;

    @Column(name = "stage_locked", nullable = false)
    @Builder.Default
    private Boolean stageLocked = Boolean.FALSE;

    @Column(length = 2048)
    private String notes;

    /** 审核状态：DRAFT/PENDING/APPROVED/REJECTED（V128 列）。 */
    @Column(name = "review_status", length = 32, nullable = false)
    @Builder.Default
    private String reviewStatus = "DRAFT";

    /** 审核人 ID（V128 列）。 */
    @Column(name = "reviewed_by")
    private Long reviewedBy;

    /** 审核时间（V128 列）。 */
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    /** 项目总结（V128 列，蓝图 §3.3.1.6 项目总结字段）。 */
    @Column(name = "project_summary", columnDefinition = "TEXT")
    private String projectSummary;

    /** 驳回原因（审核不通过时填写）。 */
    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    /** 结项时间（审核通过后设置）。 */
    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    /** 结项操作人 ID。 */
    @Column(name = "closed_by")
    private Long closedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (depositReturnStatus == null) depositReturnStatus = "NA";
        if (stageLocked == null) stageLocked = Boolean.FALSE;
        if (reviewStatus == null) reviewStatus = "DRAFT";
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
