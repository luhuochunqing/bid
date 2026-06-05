package com.xiyu.bid.roi.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ROI分析实体
 * 用于存储项目的投资回报率分析数据
 */
@Entity
@Table(name = "roi_analyses", indexes = {
    @Index(name = "idx_roi_project", columnList = "project_id"),
    @Index(name = "idx_roi_analysis_date", columnList = "analysis_date")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ROIAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 关联的项目ID
     */
    @Column(name = "project_id", nullable = false)
    private Long projectId;

    /**
     * 分析日期
     */
    @Column(name = "analysis_date")
    private LocalDateTime analysisDate;

    /**
     * 预估成本
     */
    @Column(name = "estimated_cost", precision = 19, scale = 2)
    private BigDecimal estimatedCost;

    /**
     * 预估收入
     */
    @Column(name = "estimated_revenue", precision = 19, scale = 2)
    private BigDecimal estimatedRevenue;

    /**
     * 预估利润
     */
    @Column(name = "estimated_profit", precision = 19, scale = 2)
    private BigDecimal estimatedProfit;

    /**
     * ROI百分比
     */
    @Column(name = "roi_percentage", precision = 10, scale = 2)
    private BigDecimal roiPercentage;

    /**
     * 回收期（月）
     */
    @Column(name = "payback_period_months")
    private Integer paybackPeriodMonths;

    /**
     * 风险因素
     */
    @Column(columnDefinition = "TEXT")
    private String riskFactors;

    /**
     * 假设条件
     */
    @Column(columnDefinition = "TEXT")
    private String assumptions;

    /**
     * 创建人ID
     */
    @Column(name = "created_by")
    private Long createdBy;

    @PrePersist
    protected void onCreate() {
        analysisDate = LocalDateTime.now();
    }
}
