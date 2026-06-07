package com.xiyu.bid.ai.dto;

import com.xiyu.bid.entity.Tender;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AI Analysis Response DTO
 * Contains the results of AI analysis for tenders and projects
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAnalysisResponse {

    /**
     * Overall score (0-100)
     */
    private Integer score;

    /**
     * Risk level assessment
     */
    private Tender.RiskLevel riskLevel;

    /**
     * Identified strengths
     */
    private List<String> strengths;

    /**
     * Identified weaknesses
     */
    private List<String> weaknesses;

    /**
     * Actionable recommendations
     */
    private List<String> recommendations;

    /**
     * Dimension-specific scores
     */
    private List<DimensionScore> dimensionScores;
}
