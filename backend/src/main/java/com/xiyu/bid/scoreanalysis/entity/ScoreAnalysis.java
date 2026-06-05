package com.xiyu.bid.scoreanalysis.entity;

import com.xiyu.bid.scoreanalysis.RiskLevel;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 评分分析实体
 * 记录项目的综合评分分析结果
 */
@Entity
@Table(name = "score_analyses", indexes = {
    @Index(name = "idx_score_analysis_project", columnList = "project_id"),
    @Index(name = "idx_score_analysis_tender", columnList = "tender_id"),
    @Index(name = "idx_score_analysis_date", columnList = "analysis_date"),
    @Index(name = "idx_score_analysis_risk", columnList = "risk_level")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 关联的项目ID
     */
    @Column(name = "project_id")
    private Long projectId;

    /**
     * 关联的标讯ID
     */
    @Column(name = "tender_id")
    private Long tenderId;

    /**
     * 分析日期
     */
    @Column(name = "analysis_date")
    private LocalDateTime analysisDate;

    /**
     * 综合评分 (0-100)
     */
    @Column(name = "overall_score")
    private Integer overallScore;

    /**
     * 风险等级
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", length = 20)
    private RiskLevel riskLevel;

    /**
     * 分析人ID
     */
    @Column(name = "analyst_id")
    private Long analystId;

    /**
     * 是否由AI生成
     */
    @Column(name = "is_ai_generated")
    private Boolean isAiGenerated;

    /**
     * 分析总结
     */
    @Column(columnDefinition = "TEXT")
    private String summary;

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
        if (analysisDate == null) {
            analysisDate = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
