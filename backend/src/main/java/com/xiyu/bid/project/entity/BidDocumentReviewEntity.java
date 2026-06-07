// Input: bid_document_review 表行
// Output: JPA 实体 - 标书审核记录
// Pos: project/entity/ - JPA Entity, 框架适配类
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

import java.time.LocalDateTime;

/**
 * 标书审核记录。映射 bid_document_review 表。
 * 每项目一条记录（项目维度标书审核），审核状态唯一。
 */
@Entity
@Table(name = "bid_document_review")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BidDocumentReviewEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 项目 ID（唯一约束） */
    @Column(name = "project_id", nullable = false, unique = true)
    private Long projectId;

    /** 审核人用户 ID */
    @Column(name = "reviewer_id", nullable = false)
    private Long reviewerId;

    /** 提交审核的用户 ID（投标负责人或辅助人员） */
    @Column(name = "submitted_by", nullable = false)
    private Long submittedBy;

    /** 审核状态：REVIEWING / APPROVED / REJECTED */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 驳回原因 */
    @Column(name = "reject_reason", length = 1000)
    private String rejectReason;

    /** 审核时间 */
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (status == null) {
            status = "REVIEWING";
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
