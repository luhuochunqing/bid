package com.xiyu.bid.marketinsight.entity;

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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 客户预测实体
 * 管理采购人商机预测的基础数据
 */
@Entity
@Table(name = "customer_predictions", indexes = {
    @Index(name = "idx_cp_purchaser_hash", columnList = "purchaser_hash"),
    @Index(name = "idx_cp_status", columnList = "status"),
    @Index(name = "idx_cp_opportunity_score", columnList = "opportunity_score")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerPrediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "purchaser_hash", nullable = false, length = 64)
    private String purchaserHash;

    @Column(name = "purchaser_name", nullable = false, length = 255)
    private String purchaserName;

    @Column(length = 50)
    private String industry;

    @Column(length = 100)
    private String region;

    @Column(name = "opportunity_score")
    private Integer opportunityScore;

    @Column(name = "predicted_category", length = 50)
    private String predictedCategory;

    @Column(name = "predicted_budget_min", precision = 14, scale = 2)
    private BigDecimal predictedBudgetMin;

    @Column(name = "predicted_budget_max", precision = 14, scale = 2)
    private BigDecimal predictedBudgetMax;

    @Column(name = "predicted_window", length = 20)
    private String predictedWindow;

    @Column(precision = 3, scale = 2)
    private BigDecimal confidence;

    @Column(name = "reasoning_summary", columnDefinition = "TEXT")
    private String reasoningSummary;

    @Column(name = "evidence_record_ids", length = 500)
    private String evidenceRecordIds;

    @Column(name = "main_categories", length = 500)
    private String mainCategories;

    @Column(name = "avg_budget", precision = 14, scale = 2)
    private BigDecimal avgBudget;

    @Column(name = "cycle_type", length = 50)
    private String cycleType;

    private Integer frequency;

    @Column(name = "period_months", length = 100)
    private String periodMonths;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CustomerPrediction.Status status = CustomerPrediction.Status.WATCH;

    @Column(name = "converted_project_id")
    private Long convertedProjectId;

    @Column(name = "sales_rep", length = 100)
    private String salesRep;

    @Column(name = "last_computed_at")
    private LocalDateTime lastComputedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * 商机状态枚举
     */
    public enum Status {
        WATCH,      // 观察中
        RECOMMEND,  // 推荐跟进
        CONVERTED,  // 已转化
        CANCELLED   // 已取消
    }
}
