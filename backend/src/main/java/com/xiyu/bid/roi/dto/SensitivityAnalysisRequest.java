package com.xiyu.bid.roi.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 敏感性分析请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SensitivityAnalysisRequest {

    /**
     * 项目ID
     */
    @NotNull(message = "Project ID is required")
    private Long projectId;

    /**
     * 成本变化百分比列表（例如：-10.0, 0.0, 10.0）
     */
    @NotEmpty(message = "Cost variations are required")
    private List<Double> costVariations;

    /**
     * 收入变化百分比列表（例如：-10.0, 0.0, 10.0）
     */
    @NotEmpty(message = "Revenue variations are required")
    private List<Double> revenueVariations;
}
