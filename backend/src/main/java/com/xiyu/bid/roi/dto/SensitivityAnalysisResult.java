package com.xiyu.bid.roi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 敏感性分析结果DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SensitivityAnalysisResult {

    /**
     * 项目ID
     */
    private Long projectId;

    /**
     * 基础成本
     */
    private BigDecimal baseCost;

    /**
     * 基础收入
     */
    private BigDecimal baseRevenue;

    /**
     * 基础利润
     */
    private BigDecimal baseProfit;

    /**
     * 基础ROI
     */
    private BigDecimal baseROI;

    /**
     * 场景列表
     */
    private List<Scenario> scenarios;

    /**
     * 场景DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Scenario {

        /**
         * 成本变化百分比
         */
        private Double costVariation;

        /**
         * 收入变化百分比
         */
        private Double revenueVariation;

        /**
         * 调整后成本
         */
        private BigDecimal adjustedCost;

        /**
         * 调整后收入
         */
        private BigDecimal adjustedRevenue;

        /**
         * 调整后利润
         */
        private BigDecimal adjustedProfit;

        /**
         * 调整后ROI
         */
        private BigDecimal adjustedROI;

        /**
         * 场景描述
         */
        private String description;
    }
}
