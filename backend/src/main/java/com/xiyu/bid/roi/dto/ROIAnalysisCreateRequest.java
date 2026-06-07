package com.xiyu.bid.roi.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * ROI分析创建请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ROIAnalysisCreateRequest {

    /**
     * 项目ID
     */
    @NotNull(message = "Project ID is required")
    private Long projectId;

    /**
     * 预估成本
     */
    @NotNull(message = "Estimated cost is required")
    @DecimalMin(value = "0.01", message = "Estimated cost must be greater than zero")
    private BigDecimal estimatedCost;

    /**
     * 预估收入
     */
    @NotNull(message = "Estimated revenue is required")
    private BigDecimal estimatedRevenue;

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

    /**
     * 创建人ID
     */
    private Long createdBy;
}
