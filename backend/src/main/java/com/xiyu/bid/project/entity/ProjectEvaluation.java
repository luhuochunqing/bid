// Input: project_evaluation 表行
// Output: JPA 实体 - WS-C 评标主记录 (1:1 project_id)
// Pos: project/entity/ - 持久化模型（模块化）
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

import java.time.LocalDateTime;

@Entity
@Table(name = "project_evaluation")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false, unique = true)
    private Long projectId;

    @Column(name = "sub_stage", nullable = false, length = 32)
    private String subStage;

    @Column(name = "evaluation_started_at")
    private LocalDateTime evaluationStartedAt;

    @Column(name = "board_received_at")
    private LocalDateTime boardReceivedAt;

    @Column(name = "announced_at")
    private LocalDateTime announcedAt;

    @Column(length = 2048)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(columnDefinition = "TEXT")
    private String background;

    @Column(columnDefinition = "TEXT")
    private String competitors;

    @Column(name = "contract_period", length = 64)
    private String contractPeriod;

    @Column(name = "shortlisted_bidders")
    private Integer shortlistedBidders;

    @Column(name = "platform_fee", precision = 14, scale = 2)
    private java.math.BigDecimal platformFee;

    @Column(name = "previous_bid", columnDefinition = "TEXT")
    private String previousBid;

    @Column
    private Boolean recommendation;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (subStage == null) subStage = "IN_PROGRESS";
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
