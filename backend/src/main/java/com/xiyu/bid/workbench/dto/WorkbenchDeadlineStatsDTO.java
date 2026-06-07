package com.xiyu.bid.workbench.dto;

public record WorkbenchDeadlineStatsDTO(
    DeadlinePeriodStatsDTO registrationDeadline,
    DeadlinePeriodStatsDTO bidOpening,
    DeadlinePeriodStatsDTO depositDeadline
) {
    public record DeadlinePeriodStatsDTO(long todayCount, long weekCount, long monthCount) {}
}
