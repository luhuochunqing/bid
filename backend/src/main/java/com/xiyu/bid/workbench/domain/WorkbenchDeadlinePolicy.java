package com.xiyu.bid.workbench.domain;

import lombok.experimental.UtilityClass;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Collection;

@UtilityClass
public class WorkbenchDeadlinePolicy {

    public record TimeWindowBounds(
            LocalDateTime todayStart, LocalDateTime todayEnd,
            LocalDateTime weekStart, LocalDateTime weekEnd,
            LocalDateTime monthStart, LocalDateTime monthEnd
    ) {}

    public record WindowCounts(long todayCount, long weekCount, long monthCount) {
        public static final WindowCounts ZERO = new WindowCounts(0, 0, 0);
    }

    public record DeadlineTypeStats(WindowCounts counts) {}

    public record WorkbenchDeadlineStats(
            DeadlineTypeStats registrationDeadline,
            DeadlineTypeStats bidOpening,
            DeadlineTypeStats depositDeadline
    ) {}

    public static TimeWindowBounds computeTimeWindows(LocalDate today) {
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime todayEnd = today.atTime(LocalTime.MAX);

        LocalDate weekStartDate = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEndDate = weekStartDate.plusDays(6);
        LocalDateTime weekStart = weekStartDate.atStartOfDay();
        LocalDateTime weekEnd = weekEndDate.atTime(LocalTime.MAX);

        LocalDate monthStartDate = today.withDayOfMonth(1);
        LocalDate monthEndDate = today.withDayOfMonth(today.lengthOfMonth());
        LocalDateTime monthStart = monthStartDate.atStartOfDay();
        LocalDateTime monthEnd = monthEndDate.atTime(LocalTime.MAX);

        return new TimeWindowBounds(
                todayStart, todayEnd,
                weekStart, weekEnd,
                monthStart, monthEnd
        );
    }

    public static WindowCounts countByTimeWindow(Collection<LocalDateTime> deadlines, TimeWindowBounds bounds) {
        long todayCount = 0;
        long weekCount = 0;
        long monthCount = 0;

        for (LocalDateTime deadline : deadlines) {
            if (deadline == null) {
                continue;
            }
            if (!deadline.isBefore(bounds.todayStart) && !deadline.isAfter(bounds.todayEnd)) {
                todayCount++;
            }
            if (!deadline.isBefore(bounds.weekStart) && !deadline.isAfter(bounds.weekEnd)) {
                weekCount++;
            }
            if (!deadline.isBefore(bounds.monthStart) && !deadline.isAfter(bounds.monthEnd)) {
                monthCount++;
            }
        }

        return new WindowCounts(todayCount, weekCount, monthCount);
    }

    public static WorkbenchDeadlineStats buildDeadlineStats(LocalDate today,
            Collection<LocalDateTime> registrationDeadlines,
            Collection<LocalDateTime> bidOpeningDeadlines,
            Collection<LocalDateTime> depositDeadlines) {
        TimeWindowBounds bounds = computeTimeWindows(today);

        WindowCounts registrationCounts = countByTimeWindow(registrationDeadlines, bounds);
        WindowCounts bidOpeningCounts = countByTimeWindow(bidOpeningDeadlines, bounds);
        WindowCounts depositCounts = countByTimeWindow(depositDeadlines, bounds);

        return new WorkbenchDeadlineStats(
                new DeadlineTypeStats(registrationCounts),
                new DeadlineTypeStats(bidOpeningCounts),
                new DeadlineTypeStats(depositCounts)
        );
    }
}
