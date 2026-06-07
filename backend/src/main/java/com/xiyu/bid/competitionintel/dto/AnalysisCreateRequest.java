package com.xiyu.bid.competitionintel.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 创建竞争分析请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisCreateRequest {

    @NotNull(message = "Project ID is required")
    private Long projectId;

    private Long competitorId;

    @DecimalMin(value = "0.0", message = "Win probability cannot be negative")
    @DecimalMax(value = "100.0", message = "Win probability cannot exceed 100")
    private BigDecimal winProbability;

    @Size(max = 5000, message = "Competitive advantage must not exceed 5000 characters")
    private String competitiveAdvantage;

    @Size(max = 5000, message = "Recommended strategy must not exceed 5000 characters")
    private String recommendedStrategy;

    @Size(max = 5000, message = "Risk factors must not exceed 5000 characters")
    private String riskFactors;
}
