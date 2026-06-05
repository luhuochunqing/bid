package com.xiyu.bid.roi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ROI分析DTO
 * 用于传输ROI分析数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ROIAnalysisDTO {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 项目ID
     */
    private Long projectId;

    /**
     * 分析日期
     */
    private LocalDateTime analysisDate;

    /**
     * 预估成本
     */
    private BigDecimal estimatedCost;

    /**
     * 预估收入
     */
    private BigDecimal estimatedRevenue;

    /**
     * 预估利润
     */
    private BigDecimal estimatedProfit;

    /**
     * ROI百分比
     */
    private BigDecimal roiPercentage;

    /**
     * 回收期（月）
     */
    private Integer paybackPeriodMonths;

    /**
     * 风险因素
     */
    private String riskFactors;

    /**
     * 假设条件
     */
    private String assumptions;
}
