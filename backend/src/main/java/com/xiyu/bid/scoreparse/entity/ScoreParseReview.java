package com.xiyu.bid.scoreparse.entity;

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

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 评分解析人工复核审计实体（spec 059 Phase 5）。
 * <p>记录操作者对评分项的手工调整、保留原始分值或排除决断。
 */
@Entity
@Table(name = "score_parse_review")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreParseReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "parse_task_id", nullable = false)
    private Long parseTaskId;

    @Column(name = "fact_id", length = 64)
    private String factId;

    /** RETAIN_ORIGINAL / MANUAL_ADJUST / EXCLUDE */
    @Column(nullable = false, length = 32)
    private String action;

    @Column(name = "original_weight", precision = 6, scale = 2)
    private BigDecimal originalWeight;

    @Column(name = "adjusted_weight", precision = 6, scale = 2)
    private BigDecimal adjustedWeight;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "operator_id")
    private Long operatorId;

    @Column(name = "operator_name", length = 64)
    private String operatorName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
