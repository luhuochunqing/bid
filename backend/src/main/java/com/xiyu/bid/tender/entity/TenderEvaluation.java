package com.xiyu.bid.tender.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 标讯项目评估实体（V119 重设计）。
 * <p>承载 7 个项目评估字段 + 评估状态（DRAFT/SUBMITTED）+ 投标建议 +
 * V118 起即存在的审核字段（reviewStatus/reviewer.../reviewedAt/reviewComment）。
 * <p>本实体为纯数据 + JPA 注解，不承载业务逻辑。
 *
 * <p>TODO(post-V119): consider replacing {@code @Data} with {@code @Getter} and
 * making fields final via a builder copy pattern so the entity stops violating
 * the Split-First mutability guard. Out of scope for V119.
 */
@Entity
@Table(name = "tender_evaluations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenderEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * M1: 乐观锁版本号。并发草稿保存场景使用 JPA @Version 防止丢失写入。
     */
    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    /** 关联的标讯 ID。 */
    @Column(name = "tender_id", nullable = false, unique = true)
    private Long tenderId;

    /** 评估表状态：DRAFT/SUBMITTED。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "evaluation_status", nullable = false, length = 20)
    @Builder.Default
    private EvaluationStatus evaluationStatus = EvaluationStatus.DRAFT;

    /** 建议是否投标（非必填）。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "bid_recommendation", length = 20)
    private BidRecommendation bidRecommendation;

    /** 评估表提交时间（DRAFT->SUBMITTED 时填充）。 */
    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    // ---------- 审核字段（V118 保留，V119 不动） ----------

    /** 评估人 ID。 */
    @Column(name = "evaluator_id")
    private Long evaluatorId;

    /** 评估人姓名。 */
    @Column(name = "evaluator_name", length = 100)
    private String evaluatorName;

    /** 评估时间。 */
    @Column(name = "evaluated_at")
    private LocalDateTime evaluatedAt;

    /** 审核状态。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", length = 20)
    @Builder.Default
    private ReviewStatus reviewStatus = ReviewStatus.PENDING;

    /** 审核人 ID。 */
    @Column(name = "reviewer_id")
    private Long reviewerId;

    /** 审核人姓名。 */
    @Column(name = "reviewer_name", length = 100)
    private String reviewerName;

    /** 审核时间。 */
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    /** 审核意见。 */
    @Column(name = "review_comment", length = 500)
    private String reviewComment;

    // ---------- V130 三段式重构新增字段 ----------

    /** 是否需要重新审核（已评估状态下重新编辑后设为 true）。 */
    @Column(name = "requires_review", nullable = false)
    private boolean requiresReview = false;

    /** 最后审核人 ID（与 reviewerId 类型一致）。 */
    @Column(name = "last_reviewed_by")
    private Long lastReviewedBy;

    /** 最后审核时间。 */
    @Column(name = "last_reviewed_at")
    private LocalDateTime lastReviewedAt;

    /** 评估轮次，每次重新编辑自增。 */
    @Column(name = "evaluation_round", nullable = false)
    @Builder.Default
    private Integer evaluationRound = 1;

    /** 创建时间（不可更新）。 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 更新时间。 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ---------- 生命周期钩子 ----------

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ---------- V130 三段式关系映射 ----------

    /** 基础信息段（一对一）。 */
    @OneToOne(mappedBy = "evaluation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private TenderEvaluationBasic basic;

    /** 客户信息段 EAV 行（一对多）。 */
    @OneToMany(mappedBy = "evaluation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<TenderEvaluationCustomerInfo> customerInfos;

    /** 投标负责人建议段（一对一，共享主键）。 */
    @OneToOne(mappedBy = "evaluation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private TenderEvaluationRecommendation recommendation;

    /** 评估表状态枚举。 */
    public enum EvaluationStatus {
        DRAFT,      // 草稿
        SUBMITTED   // 已提交
    }

    /** 建议是否投标枚举（M2: 移除 PENDING_REVIEW — 前端不再展示）。 */
    public enum BidRecommendation {
        RECOMMEND,        // 建议投标
        NOT_RECOMMEND     // 不建议投标
    }

    /** 审核状态枚举（V118 起即存在，V119 保留）。 */
    public enum ReviewStatus {
        PENDING,    // 待审核
        APPROVED,   // 已通过（投标）
        REJECTED    // 已拒绝（弃标）
    }
}
