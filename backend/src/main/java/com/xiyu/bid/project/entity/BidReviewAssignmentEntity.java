// Input: bid_review_assignment 表行
// Output: JPA 实体 - 标书审核人分配记录（多人审核）
// Pos: project/entity/ - JPA Entity, 框架适配类
package com.xiyu.bid.project.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 标书审核人分配记录。映射 bid_review_assignment 表。
 * <p>CO-483 + CO-484：每个审核人的独立决策记录（一对多：一条 bid_document_review 对应多条 assignment）。</p>
 */
@Entity
@Table(name = "bid_review_assignment")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BidReviewAssignmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联 bid_document_review.id */
    @Column(name = "review_id", nullable = false)
    private Long reviewId;

    /** 审核人用户 ID */
    @Column(name = "reviewer_id", nullable = false)
    private Long reviewerId;

    /** 决策：APPROVED / REJECTED / NULL(未决) */
    @Column(name = "decision", length = 20)
    private String decision;

    /** 驳回原因（驳回时填） */
    @Column(name = "comment", length = 1000)
    private String comment;

    /** 决策时间 */
    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
