package com.xiyu.bid.fees.service;

import com.xiyu.bid.fees.dto.FeeStatisticsDTO;

import java.math.BigDecimal;

final class FeeStatisticsFactory {

    private FeeStatisticsFactory() {
    }

    static FeeStatisticsDTO create(
            Long projectId,
            BigDecimal totalPending,
            BigDecimal totalPaid,
            BigDecimal totalReturned,
            BigDecimal totalCancelled) {
        BigDecimal safePending = valueOrZero(totalPending);
        BigDecimal safePaid = valueOrZero(totalPaid);
        BigDecimal safeReturned = valueOrZero(totalReturned);
        BigDecimal safeCancelled = valueOrZero(totalCancelled);

        return FeeStatisticsDTO.builder()
                .projectId(projectId)
                .totalPending(safePending)
                .totalPaid(safePaid)
                .totalReturned(safeReturned)
                .totalCancelled(safeCancelled)
                .grandTotal(safePending.add(safePaid).add(safeReturned).add(safeCancelled))
                .build();
    }

    private static BigDecimal valueOrZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
