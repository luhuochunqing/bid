package com.xiyu.bid.warehouse.domain;

import com.xiyu.bid.warehouse.infrastructure.WarehouseEntity;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 纯核心：仓库状态重新计算器 — 根据 endDate 与今天的关系，得出 IN_USE / EXPIRING / EXPIRED。
 * 已关仓（CLOSED）状态不在本计算器内，关仓是显式用户操作。
 */
public class WarehouseStatusCalculator {

    public static final long WARNING_DAYS = 30L;

    private WarehouseStatusCalculator() {
    }

    /**
     * 重新计算仓库状态（不修改入参）。
     */
    public static WarehouseStatus recompute(WarehouseEntity wh) {
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
