package com.xiyu.bid.warehouse.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class WarehouseStatusCalculator {
    public static final long WARNING_DAYS = 30L;
    private WarehouseStatusCalculator() {}

    public static WarehouseStatus recompute(WarehouseReadModel wh) {
        return recompute(wh.getEndDate(), LocalDate.now());
    }

    public static WarehouseStatus recompute(LocalDate endDate, LocalDate today) {
        if (endDate == null) return WarehouseStatus.IN_USE;
        long remaining = ChronoUnit.DAYS.between(today, endDate);
        if (remaining <= 0) return WarehouseStatus.EXPIRED;
        if (remaining <= WARNING_DAYS) return WarehouseStatus.EXPIRING;
        return WarehouseStatus.IN_USE;
    }
}
