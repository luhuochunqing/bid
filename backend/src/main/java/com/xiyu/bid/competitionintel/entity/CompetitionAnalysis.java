package com.xiyu.bid.competitionintel.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 竞争分析实体
 * 记录针对特定项目的竞争对手分析，包括胜率预测、竞争策略建议等
 */
@Entity
@Table(name = "competition_analyses", indexes = {
    @Index(name = "idx_analysis_project", columnList = "project_id"),
    @Index(name = "idx_analysis_competitor", columnList = "competitor_id"),
    @Index(name = "idx_analysis_date", columnList = "analysis_date"),
    @Index(name = "idx_analysis_project_competitor", columnList = "project_id, competitor_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompetitionAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 关联的项目ID
     */
    @Column(name = "project_id", nullable = false)
    private Long projectId;

    /**
     * 关联的竞争对手ID（可为空，表示综合分析）
     */
    @Column(name = "competitor_id")
    private Long competitorId;

    /**
     * 分析日期
     */
    @Column(name = "analysis_date")
    private LocalDateTime analysisDate;

    /**
     * 胜率预测（0-100）
     */
    @Column(precision = 5, scale = 2)
    private BigDecimal winProbability;

    /**
     * 竞争优势分析
     */
    @Column(columnDefinition = "TEXT")
    private String competitiveAdvantage;

    /**
     * 建议策略
     */
    @Column(columnDefinition = "TEXT")
    private String recommendedStrategy;

    /**
     * 风险因素
     */
    @Column(columnDefinition = "TEXT")
    private String riskFactors;

    @PrePersist
    protected void onCreate() {
        if (analysisDate == null) {
            analysisDate = LocalDateTime.now();
        }
    }
}
