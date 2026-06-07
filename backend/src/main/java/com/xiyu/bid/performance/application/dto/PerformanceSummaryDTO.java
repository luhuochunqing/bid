package com.xiyu.bid.performance.application.dto;

import java.math.BigDecimal;

public record PerformanceSummaryDTO(
        long totalCount,
        BigDecimal totalAmount,
        long signedCount,
        long executingCount,
        long completedCount
) {}
