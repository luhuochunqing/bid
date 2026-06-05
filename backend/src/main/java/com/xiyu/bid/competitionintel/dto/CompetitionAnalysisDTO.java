package com.xiyu.bid.competitionintel.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 竞争分析数据传输对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompetitionAnalysisDTO {

    private Long id;
    private Long projectId;
    private Long competitorId;
    private LocalDateTime analysisDate;
    private BigDecimal winProbability;
    private String competitiveAdvantage;
    private String recommendedStrategy;
    private String riskFactors;
}
