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

    /** 单窗口起止边界（CO-593：按 period 返回单个时间窗） */
    public record Window(LocalDateTime start, LocalDateTime end) {}

    public record WindowCounts(long todayCount, long weekCount, long monthCount) {
        public static final WindowCounts ZERO = new WindowCounts(0, 0, 0);
    }

    public record DeadlineTypeStats(WindowCounts counts) {}

    public record WorkbenchDeadlineStats(
            DeadlineTypeStats registrationDeadline,
            DeadlineTypeStats bidOpening,
            DeadlineTypeStats depositDeadline
    ) {}

    /**
     * 按 period 解析单个时间窗（CO-593）。
     *
     * <p>复用 {@link #computeTimeWindows} 内部逻辑，仅返回所选周期的起止边界。</p>
     */
    public static Window resolveWindow(LocalDate today, DeadlinePeriod period) {
        TimeWindowBounds bounds = computeTimeWindows(today);
        return switch (period) {
            case TODAY -> new Window(bounds.todayStart(), bounds.todayEnd());
            case WEEK -> new Window(bounds.weekStart(), bounds.weekEnd());
            case MONTH -> new Window(bounds.monthStart(), bounds.monthEnd());
        };
    }

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

        // 方案 B（思维链 H1）：接受差异，不去重。
        // stats（卡片计数）与列表（getDeadlineItems 按 date+name 去重）在重复数据下条数不一致，
        // 属刻意取舍——stats 层仅有时间戳、无 name 业务键，按时间戳去重会误并同一时刻的不同标讯，
        // 造成伪对齐与潜在少计。根治需数据层/推送层加固，此处不强求一致。
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
