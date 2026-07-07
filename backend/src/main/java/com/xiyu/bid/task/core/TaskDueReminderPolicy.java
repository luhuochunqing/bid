// Input: TaskReminderState (dueDate/status/lastRemindedAt/lastOverdueRemindedAt) + today + alertDays + now
// Output: SkipReason (COMPLETED/OUT_OF_WINDOW/DEDUP_24H/INVALID_DUE_DATE/NOT_OVERDUE) or null
// Pos: Pure Core/任务到期提醒策略 — CO-533 蓝图 §1.1/§1.2 跳过规则与升级判定
// 维护声明:
//   - 不依赖 Task entity、Repository、Spring；仅依赖显式参数与 java.time；
//   - 不修改入参，不抛业务异常（用 SkipReason 返回值表达）；
//   - 业务规则：24h 去重 / 已完成停止 / 逾期窗口 / 逾期>7天升级。
package com.xiyu.bid.task.core;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * CO-533 投标项目任务到期/逾期提醒纯核心策略。
 *
 * <p>对齐蓝图 §1.1 即将到期提醒与 §1.2 逾期/超期提醒的判定规则：
 * <ul>
 *   <li>已完成任务（status=COMPLETED）停止一切提醒</li>
 *   <li>即将到期窗口：dueDate - today 在 (0, alertDays] 区间</li>
 *   <li>逾期窗口：dueDate &lt; today</li>
 *   <li>24h 去重：lastRemindedAt/lastOverdueRemindedAt + 24h &gt; now 则跳过</li>
 *   <li>升级机制：逾期超过 7 天后追加投标管理员/投标组长接收人</li>
 * </ul>
 *
 * <p>纯核心：无状态、无依赖、无副作用。所有方法静态或实例方法均可，
 * 参数显式传入，不读数据库、不读系统时钟。
 */
public final class TaskDueReminderPolicy {

    /** 24 小时内同任务最多提醒 1 次。 */
    public static final int DEDUP_HOURS = 24;

    /** 逾期超过 7 天后升级接收人。 */
    public static final int ESCALATION_OVERDUE_DAYS = 7;

    /**
     * 即将到期扫描的跳过判定。
     *
     * @param state     任务提醒状态（dueDate/status/lastRemindedAt）
     * @param today     当前日期
     * @param alertDays 提前提醒天数（1..365）
     * @param now       当前时间（用于 24h 去重）
     * @return 跳过原因；null 表示应继续发送提醒
     */
    public SkipReason shouldSkipDueSoon(
            final TaskReminderState state,
            final LocalDate today,
            final int alertDays,
            final LocalDateTime now) {
        if (state == null) {
            return SkipReason.INVALID_DUE_DATE;
        }
        if (isCompleted(state.status())) {
            return SkipReason.COMPLETED;
        }
        if (state.dueDate() == null) {
            return SkipReason.INVALID_DUE_DATE;
        }
        long remaining = computeRemainingDays(state.dueDate(), today);
        if (remaining <= 0 || remaining > alertDays) {
            return SkipReason.OUT_OF_WINDOW;
        }
        if (!shouldRemindToday(state.lastRemindedAt(), now)) {
            return SkipReason.DEDUP_24H;
        }
        return null;
    }

    /**
     * 逾期扫描的跳过判定。
     *
     * @param state 任务提醒状态（dueDate/status/lastOverdueRemindedAt）
     * @param today 当前日期
     * @param now   当前时间（用于 24h 去重）
     * @return 跳过原因；null 表示应继续发送提醒
     */
    public SkipReason shouldSkipOverdue(
            final TaskReminderState state,
            final LocalDate today,
            final LocalDateTime now) {
        if (state == null) {
            return SkipReason.INVALID_DUE_DATE;
        }
        if (isCompleted(state.status())) {
            return SkipReason.COMPLETED;
        }
        if (state.dueDate() == null) {
            return SkipReason.INVALID_DUE_DATE;
        }
        long remaining = computeRemainingDays(state.dueDate(), today);
        if (remaining >= 0) {
            return SkipReason.NOT_OVERDUE;
        }
        if (!shouldRemindToday(state.lastOverdueRemindedAt(), now)) {
            return SkipReason.DEDUP_24H;
        }
        return null;
    }

    /**
     * 逾期升级判定：逾期天数是否已超过 7 天。
     *
     * @param dueDate 任务截止日期
     * @param today   当前日期
     * @return true 表示逾期已超过 7 天，应追加升级接收人
     */
    public boolean shouldEscalate(final LocalDateTime dueDate, final LocalDate today) {
        if (dueDate == null) {
            return false;
        }
        long overdueDays = -computeRemainingDays(dueDate, today);
        return overdueDays > ESCALATION_OVERDUE_DAYS;
    }

    /**
     * 计算剩余天数（today 到 dueDate）。
     *
     * @param dueDate 任务截止日期
     * @param today   当前日期
     * @return 剩余天数；负数表示已逾期；null dueDate 返回 Long.MAX_VALUE
     */
    public long computeRemainingDays(final LocalDateTime dueDate, final LocalDate today) {
        if (dueDate == null) {
            return Long.MAX_VALUE;
        }
        return ChronoUnit.DAYS.between(today, dueDate.toLocalDate());
    }

    /**
     * 24h 去重判定：上次提醒时间 + 24h 是否已超过当前时间。
     *
     * @param lastRemindedAt 上次提醒时间（null 表示从未提醒）
     * @param now            当前时间
     * @return true 表示可以再次提醒；false 表示 24h 内已提醒过
     */
    public boolean shouldRemindToday(final LocalDateTime lastRemindedAt, final LocalDateTime now) {
        if (lastRemindedAt == null) {
            return true;
        }
        return !now.isBefore(lastRemindedAt.plusHours(DEDUP_HOURS));
    }

    private static boolean isCompleted(final String status) {
        return "COMPLETED".equals(status);
    }

    /** 跳过原因枚举（仅做返回值，不抛异常）。 */
    public enum SkipReason {
        /** 任务已完成。 */
        COMPLETED,
        /** 不在提醒窗口（即将到期扫描用）。 */
        OUT_OF_WINDOW,
        /** 24 小时内已提醒过。 */
        DEDUP_24H,
        /** 截止日期或状态缺失。 */
        INVALID_DUE_DATE,
        /** 未逾期（逾期扫描用）。 */
        NOT_OVERDUE
    }

    /**
     * 任务提醒状态值对象（纯核心，不依赖 Task entity）。
     *
     * <p>由编排服务从 Task entity 提取字段构造，避免纯核心依赖 entity 包。
     */
    public record TaskReminderState(
            LocalDateTime dueDate,
            String status,
            LocalDateTime lastRemindedAt,
            LocalDateTime lastOverdueRemindedAt
    ) {
    }
}
