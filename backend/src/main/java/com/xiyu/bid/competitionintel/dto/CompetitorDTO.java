package com.xiyu.bid.competitionintel.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 竞争对手数据传输对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompetitorDTO {

    private Long id;
    private String name;
    private String industry;
    private String strengths;
    private String weaknesses;
    private BigDecimal marketShare;
    private BigDecimal typicalBidRangeMin;
    private BigDecimal typicalBidRangeMax;
    private LocalDateTime createdAt;
}
