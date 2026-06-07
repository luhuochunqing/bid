package com.xiyu.bid.bidresult.core;

import java.math.BigDecimal;

public record CompetitorWinRow(
        Long competitorId,
        String competitorName,
        Integer skuCount,
        String category,
        String discount,
        String paymentTerms,
        BigDecimal winProbability
) {
}
