package com.xiyu.bid.analytics.model;

import java.math.BigDecimal;

public record ProjectTypeAggregate(
    String projectType,
    Long projectCount,
    Long activeProjectCount,
    Long wonCount,
    BigDecimal totalAmount,
    Double percentage,
    Double winRate
) {
}