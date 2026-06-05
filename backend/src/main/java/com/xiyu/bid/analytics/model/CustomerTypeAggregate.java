package com.xiyu.bid.analytics.model;

import java.math.BigDecimal;

public record CustomerTypeAggregate(
    String customerType,
    Long projectCount,
    Long activeProjectCount,
    Long wonCount,
    BigDecimal totalAmount,
    Double percentage,
    Double winRate
) {
}
