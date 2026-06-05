package com.xiyu.bid.scoreanalysis.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 维度分数实体
 * 记录各个评估维度的详细得分
 */
@Entity
@Table(name = "dimension_scores", indexes = {
    @Index(name = "idx_dimension_analysis", columnList = "analysis_id"),
    @Index(name = "idx_dimension_name", columnList = "dimension_name")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DimensionScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 关联的分析ID
     */
    @Column(name = "analysis_id", nullable = false)
    private Long analysisId;

    /**
     * 维度名称
     * 标准维度：技术能力、财务实力、团队经验、历史业绩、合规性、价格竞争力
     */
    @Column(nullable = false, length = 100)
    private String dimensionName;

    /**
     * 维度分数 (0-100)
     */
    private Integer score;

    /**
     * 权重 (0-1)
     */
    private BigDecimal weight;

    /**
     * 评语/评论
     */
    @Column(columnDefinition = "TEXT")
    private String comments;

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

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
