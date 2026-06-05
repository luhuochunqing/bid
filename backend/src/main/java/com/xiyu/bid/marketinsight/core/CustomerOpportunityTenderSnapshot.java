package com.xiyu.bid.marketinsight.core;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Pure snapshot of tender signals used by customer opportunity flows.
 */
public record CustomerOpportunityTenderSnapshot(
        Long tenderId,
        String tenderTitle,
        String purchaserName,
        String purchaserHash,
        String industry,
        BigDecimal budget,
        LocalDateTime createdAt) {
}
