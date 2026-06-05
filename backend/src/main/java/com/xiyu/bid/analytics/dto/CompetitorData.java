package com.xiyu.bid.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Competitor analysis data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompetitorData implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Competitor name or identifier
     */
    private String name;

    /**
     * Number of bids participated
     */
    private Long bidCount;

    /**
     * Number of wins
     */
    private Long winCount;

    /**
     * Win rate percentage
     */
    private Double winRate;

    /**
     * Total bid amount
     */
    private BigDecimal totalBidAmount;
}
