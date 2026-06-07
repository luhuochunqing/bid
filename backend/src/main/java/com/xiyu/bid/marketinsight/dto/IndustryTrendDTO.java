package com.xiyu.bid.marketinsight.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 行业趋势 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndustryTrendDTO {

    private String industry;

    private int count;

    private long amount;

    private long growth;

    private String trend;

    private int hotLevel;

    private String color;
}
