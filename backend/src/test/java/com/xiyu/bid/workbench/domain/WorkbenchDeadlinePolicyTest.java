package com.xiyu.bid.workbench.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WorkbenchDeadlinePolicyTest {

    @Test
    @DisplayName("以2026-05-17周日为例，周一开始05-11，月始05-01，月末05-31")
    void shouldReturnTodayWeekMonthBounds() {
        LocalDate today = LocalDate.of(2026, 5, 17);

        WorkbenchDeadlinePolicy.TimeWindowBounds bounds = WorkbenchDeadlinePolicy.computeTimeWindows(today);

        assertThat(bounds.todayStart()).isEqualTo(LocalDate.of(2026, 5, 17).atStartOfDay());
        assertThat(bounds.todayEnd()).isEqualTo(LocalDate.of(2026, 5, 17).atTime(23, 59, 59, 999_999_999));
        assertThat(bounds.weekStart()).isEqualTo(LocalDate.of(2026, 5, 11).atStartOfDay());
        assertThat(bounds.weekEnd()).isEqualTo(LocalDate.of(2026, 5, 17).atTime(23, 59, 59, 999_999_999));
        assertThat(bounds.monthStart()).isEqualTo(LocalDate.of(2026, 5, 1).atStartOfDay());
        assertThat(bounds.monthEnd()).isEqualTo(LocalDate.of(2026, 5, 31).atTime(23, 59, 59, 999_999_999));
    }

    @Test
    @DisplayName("3个deadline分别落入today/week/month，计数为1/2/3")
    void shouldCountByWindow() {
        LocalDate today = LocalDate.of(2026, 5, 17);
        WorkbenchDeadlinePolicy.TimeWindowBounds bounds = WorkbenchDeadlinePolicy.computeTimeWindows(today);

        List<LocalDateTime> deadlines = Arrays.asList(
                LocalDateTime.of(2026, 5, 17, 10, 0),  // today
                LocalDateTime.of(2026, 5, 12, 10, 0),  // this week (not today)
                LocalDateTime.of(2026, 5, 3, 10, 0)    // this month (not this week)
        );

        WorkbenchDeadlinePolicy.WindowCounts counts = WorkbenchDeadlinePolicy.countByTimeWindow(deadlines, bounds);

        assertThat(counts.todayCount()).isEqualTo(1);
        assertThat(counts.weekCount()).isEqualTo(2);
        assertThat(counts.monthCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("空列表返回全零")
    void shouldHandleEmptyDeadlines() {
        LocalDate today = LocalDate.of(2026, 5, 17);
        WorkbenchDeadlinePolicy.TimeWindowBounds bounds = WorkbenchDeadlinePolicy.computeTimeWindows(today);

        WorkbenchDeadlinePolicy.WindowCounts counts = WorkbenchDeadlinePolicy.countByTimeWindow(
                Collections.emptyList(), bounds);

        assertThat(counts).isEqualTo(WorkbenchDeadlinePolicy.WindowCounts.ZERO);
    }

    @Test
    @DisplayName("含null的列表跳过null正确计数")
    void shouldHandleNullDeadlines() {
        LocalDate today = LocalDate.of(2026, 5, 17);
        WorkbenchDeadlinePolicy.TimeWindowBounds bounds = WorkbenchDeadlinePolicy.computeTimeWindows(today);

        List<LocalDateTime> deadlines = Arrays.asList(
                LocalDateTime.of(2026, 5, 17, 10, 0),
                null,
                LocalDateTime.of(2026, 5, 3, 10, 0)
        );

        WorkbenchDeadlinePolicy.WindowCounts counts = WorkbenchDeadlinePolicy.countByTimeWindow(deadlines, bounds);

        assertThat(counts.todayCount()).isEqualTo(1);
        assertThat(counts.weekCount()).isEqualTo(1);
        assertThat(counts.monthCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("三类deadline各一条，验证完整聚合")
    void shouldBuildDeadlineStats() {
        LocalDate today = LocalDate.of(2026, 5, 17);

        List<LocalDateTime> registrationDeadlines = List.of(
                LocalDateTime.of(2026, 5, 17, 9, 0));
        List<LocalDateTime> bidOpeningDeadlines = List.of(
                LocalDateTime.of(2026, 5, 12, 14, 0));
        List<LocalDateTime> depositDeadlines = List.of(
                LocalDateTime.of(2026, 5, 3, 18, 0));

        WorkbenchDeadlinePolicy.WorkbenchDeadlineStats stats = WorkbenchDeadlinePolicy.buildDeadlineStats(
                today, registrationDeadlines, bidOpeningDeadlines, depositDeadlines);

        assertThat(stats.registrationDeadline().counts().todayCount()).isEqualTo(1);
        assertThat(stats.registrationDeadline().counts().weekCount()).isEqualTo(1);
        assertThat(stats.registrationDeadline().counts().monthCount()).isEqualTo(1);

        assertThat(stats.bidOpening().counts().todayCount()).isEqualTo(0);
        assertThat(stats.bidOpening().counts().weekCount()).isEqualTo(1);
        assertThat(stats.bidOpening().counts().monthCount()).isEqualTo(1);

        assertThat(stats.depositDeadline().counts().todayCount()).isEqualTo(0);
        assertThat(stats.depositDeadline().counts().weekCount()).isEqualTo(0);
        assertThat(stats.depositDeadline().counts().monthCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("P1: 周窗口跨月（月初周六）— weekStart 落在上月，仍能正确按周计数")
    void shouldCountWeekDeadlinesAcrossMonthBoundaryAtStartOfMonth() {
        // today = 2026-08-01 (Saturday) → week = Jul 27 ~ Aug 2
        LocalDate today = LocalDate.of(2026, 8, 1);
        WorkbenchDeadlinePolicy.TimeWindowBounds bounds = WorkbenchDeadlinePolicy.computeTimeWindows(today);

        assertThat(bounds.weekStart()).isEqualTo(LocalDate.of(2026, 7, 27).atStartOfDay());
        assertThat(bounds.weekEnd()).isEqualTo(LocalDate.of(2026, 8, 2).atTime(java.time.LocalTime.MAX));
        assertThat(bounds.monthStart()).isEqualTo(LocalDate.of(2026, 8, 1).atStartOfDay());

        List<LocalDateTime> deadlines = Arrays.asList(
                LocalDateTime.of(2026, 7, 28, 10, 0), // in week, NOT in month
                LocalDateTime.of(2026, 8, 1, 10, 0),  // today, in week, in month
                LocalDateTime.of(2026, 8, 15, 10, 0)  // in month, NOT in week
        );

        WorkbenchDeadlinePolicy.WindowCounts counts = WorkbenchDeadlinePolicy.countByTimeWindow(deadlines, bounds);

        assertThat(counts.todayCount()).isEqualTo(1);
        assertThat(counts.weekCount())
                .as("must include the Jul 28 deadline that falls in this week but previous month")
                .isEqualTo(2);
        assertThat(counts.monthCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("P1: 周窗口跨月（月末周二）— weekEnd 落在下月，仍能正确按周计数")
    void shouldCountWeekDeadlinesAcrossMonthBoundaryAtEndOfMonth() {
        // today = 2026-06-30 (Tuesday) → week = Jun 29 ~ Jul 5
        LocalDate today = LocalDate.of(2026, 6, 30);
        WorkbenchDeadlinePolicy.TimeWindowBounds bounds = WorkbenchDeadlinePolicy.computeTimeWindows(today);

        assertThat(bounds.weekStart()).isEqualTo(LocalDate.of(2026, 6, 29).atStartOfDay());
        assertThat(bounds.weekEnd()).isEqualTo(LocalDate.of(2026, 7, 5).atTime(java.time.LocalTime.MAX));
        assertThat(bounds.monthEnd()).isEqualTo(LocalDate.of(2026, 6, 30).atTime(java.time.LocalTime.MAX));

        List<LocalDateTime> deadlines = Arrays.asList(
                LocalDateTime.of(2026, 6, 30, 10, 0), // today, in week, in month
                LocalDateTime.of(2026, 7, 3, 10, 0),  // in week, NOT in month
                LocalDateTime.of(2026, 6, 5, 10, 0)   // in month, NOT in week
        );

        WorkbenchDeadlinePolicy.WindowCounts counts = WorkbenchDeadlinePolicy.countByTimeWindow(deadlines, bounds);

        assertThat(counts.todayCount()).isEqualTo(1);
        assertThat(counts.weekCount())
                .as("must include the Jul 3 deadline that falls in this week but next month")
                .isEqualTo(2);
        assertThat(counts.monthCount()).isEqualTo(2);
    }
}
