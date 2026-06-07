package com.xiyu.bid.marketinsight.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 采购人模式 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaserPatternDTO {

    private String name;

    private String industry;

    private int frequency;

    private String period;

    private long avgBudget;

    private int opportunity;
}
