package com.xiyu.bid.competitionintel.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 创建竞争对手请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompetitorCreateRequest {

    @NotBlank(message = "Competitor name is required")
    @Size(max = 200, message = "Competitor name must not exceed 200 characters")
    private String name;

    @Size(max = 100, message = "Industry must not exceed 100 characters")
    private String industry;

    @Size(max = 5000, message = "Strengths must not exceed 5000 characters")
    private String strengths;

    @Size(max = 5000, message = "Weaknesses must not exceed 5000 characters")
    private String weaknesses;

    @DecimalMin(value = "0.0", message = "Market share cannot be negative")
    @DecimalMax(value = "100.0", message = "Market share cannot exceed 100")
    private BigDecimal marketShare;

    @DecimalMin(value = "0.0", message = "Bid range minimum cannot be negative")
    private BigDecimal typicalBidRangeMin;

    @DecimalMin(value = "0.0", message = "Bid range maximum cannot be negative")
    private BigDecimal typicalBidRangeMax;
}
