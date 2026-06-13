package com.xiyu.bid.warehouse.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class WarehouseStatusCalculatorTest {

    @Test
    void nullEndDate_ShouldReturnInUse() {
        WarehouseStatus status = WarehouseStatusCalculator.recompute(null, LocalDate.of(2026, 6, 13));
        assertThat(status).isEqualTo(WarehouseStatus.IN_USE);
    }

    @Test
    void endDateIsToday_ShouldReturnExpired() {
        WarehouseStatus status = WarehouseStatusCalculator.recompute(
                LocalDate.of(2026, 6, 13), LocalDate.of(2026, 6, 13));
        assertThat(status).isEqualTo(WarehouseStatus.EXPIRED);
    }

    @Test
    void endDateInPast_ShouldReturnExpired() {
        WarehouseStatus status = WarehouseStatusCalculator.recompute(
                LocalDate.of(2026, 6, 12), LocalDate.of(2026, 6, 13));
        assertThat(status).isEqualTo(WarehouseStatus.EXPIRED);
    }

    @Test
    void endDateWithinWarningDays_ShouldReturnExpiring() {
        WarehouseStatus status = WarehouseStatusCalculator.recompute(
                LocalDate.of(2026, 6, 28), LocalDate.of(2026, 6, 13));
        assertThat(status).isEqualTo(WarehouseStatus.EXPIRING);
    }

    @Test
    void endDateExactlyAtWarningThreshold_ShouldReturnExpiring() {
        WarehouseStatus status = WarehouseStatusCalculator.recompute(
                LocalDate.of(2026, 7, 13), LocalDate.of(2026, 6, 13));
        assertThat(status).isEqualTo(WarehouseStatus.EXPIRING);
    }

    @Test
    void endDateBeyondWarningDays_ShouldReturnInUse() {
        WarehouseStatus status = WarehouseStatusCalculator.recompute(
                LocalDate.of(2026, 7, 14), LocalDate.of(2026, 6, 13));
        assertThat(status).isEqualTo(WarehouseStatus.IN_USE);
    }
}
