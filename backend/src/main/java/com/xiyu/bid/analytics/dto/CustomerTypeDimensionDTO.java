package com.xiyu.bid.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerTypeDimensionDTO {
    private String customerType;
    private Long projectCount;
    private Long activeProjectCount;
    private Long wonCount;
    private BigDecimal totalAmount;
    private Double percentage;
    private Double winRate;
}
