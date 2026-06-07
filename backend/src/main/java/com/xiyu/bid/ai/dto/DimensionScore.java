package com.xiyu.bid.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Dimension Score DTO.
 * Represents a score for a specific dimension
 * (e.g., Technical, Financial, Team).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DimensionScore {

    /**
     * Dimension name (e.g., "Technical", "Financial", "Team").
     */
    private String dimension;

    /**
     * Score for this dimension (0-100).
     */
    private Integer score;

    /**
     * Detailed explanation of the score.
     */
    private String details;
}
