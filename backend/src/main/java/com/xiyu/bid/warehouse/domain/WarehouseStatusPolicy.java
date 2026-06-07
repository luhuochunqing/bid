package com.xiyu.bid.warehouse.domain;

import java.time.LocalDate;

public enum WarehouseStatusPolicy {

    ;

    public static WarehouseStatus determine(LocalDate endDate) {
        return determine(endDate, LocalDate.now());
    }

    public static WarehouseStatus determine(LocalDate endDate, LocalDate currentDate) {
        if (endDate == null) return WarehouseStatus.IN_USE;
        if (endDate.isBefore(currentDate)) return WarehouseStatus.EXPIRED;
        if (endDate.isBefore(currentDate.plusDays(31))) return WarehouseStatus.EXPIRING;
        return WarehouseStatus.IN_USE;
    }

    private WarehouseStatusPolicy() {}
}
