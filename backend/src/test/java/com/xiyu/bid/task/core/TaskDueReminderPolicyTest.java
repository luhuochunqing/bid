// Input: TaskDueReminderPolicy (pure core) + java.time
// Output: §1.1/§1.2 跳过规则 + 升级判定 + 去重 + 剩余天数计算 单元测试
// Pos: test/java/.../task/core - 纯核心单元测试，无 Spring 依赖
package com.xiyu.bid.task.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CO-533 任务到期/逾期提醒策略单元测试。
 *
 * <p>覆盖蓝图 §1.1 即将到期提醒与 §1.2 逾期/超期提醒的判定规则：
 * <ul>
 *   <li>跳过规则：COMPLETED / INVALID_DUE_DATE / OUT_OF_WINDOW / DEDUP_24H / NOT_OVERDUE</li>
 *   <li>升级判定：逾期 &gt; 7 天</li>
 *   <li>24h 去重：lastRemindedAt + 24h</li>
 *   <li>剩余天数计算：null dueDate 返回 Long.MAX_VALUE</li>
 * </ul>
 */
class TaskDueReminderPolicyTest {

    private final TaskDueReminderPolicy policy = new TaskDueReminderPolicy();

    // ============ shouldSkipDueSoon ============

    @Test
    @DisplayName("shouldSkipDueSoon - 已完成任务：COMPLETED")
    void shouldSkipDueSoon_CompletedTask_ShouldReturnCOMPLETED() {
        TaskDueReminderPolicy.TaskReminderState state = new TaskDueReminderPolicy.TaskReminderState(
                LocalDateTime.now().plusDays(2),
                "COMPLETED",
                null,
                null
        );
        TaskDueReminderPolicy.SkipReason reason =
                policy.shouldSkipDueSoon(state, LocalDate.now(), 3, LocalDateTime.now());
        assertThat(reason).isEqualTo(TaskDueReminderPolicy.SkipReason.COMPLETED);
    }

    @Test
    @DisplayName("shouldSkipDueSoon - 截止日期为空：INVALID_DUE_DATE")
    void shouldSkipDueSoon_NullDueDate_ShouldReturnINVALID_DUE_DATE() {
        TaskDueReminderPolicy.TaskReminderState state = new TaskDueReminderPolicy.TaskReminderState(
                null,
                "TODO",
                null,
                null
        );
        TaskDueReminderPolicy.SkipReason reason =
                policy.shouldSkipDueSoon(state, LocalDate.now(), 3, LocalDateTime.now());
        assertThat(reason).isEqualTo(TaskDueReminderPolicy.SkipReason.INVALID_DUE_DATE);
    }

    @Test
    @DisplayName("shouldSkipDueSoon - 剩余天数超过阈值：OUT_OF_WINDOW")
    void shouldSkipDueSoon_RemainingBeyondAlertDays_ShouldReturnOUT_OF_WINDOW() {
        TaskDueReminderPolicy.TaskReminderState state = new TaskDueReminderPolicy.TaskReminderState(
                LocalDateTime.now().plusDays(10),
                "TODO",
                null,
                null
        );
        TaskDueReminderPolicy.SkipReason reason =
                policy.shouldSkipDueSoon(state, LocalDate.now(), 3, LocalDateTime.now());
        assertThat(reason).isEqualTo(TaskDueReminderPolicy.SkipReason.OUT_OF_WINDOW);
    }

    @Test
    @DisplayName("shouldSkipDueSoon - 已过期（剩余天数<=0）：OUT_OF_WINDOW")
    void shouldSkipDueSoon_Overdue_ShouldReturnOUT_OF_WINDOW() {
        TaskDueReminderPolicy.TaskReminderState state = new TaskDueReminderPolicy.TaskReminderState(
                LocalDateTime.now().minusDays(1),
                "TODO",
                null,
                null
        );
        TaskDueReminderPolicy.SkipReason reason =
                policy.shouldSkipDueSoon(state, LocalDate.now(), 3, LocalDateTime.now());
        assertThat(reason).isEqualTo(TaskDueReminderPolicy.SkipReason.OUT_OF_WINDOW);
    }

    @Test
    @DisplayName("shouldSkipDueSoon - 24h 内已提醒过：DEDUP_24H")
    void shouldSkipDueSoon_RecentlyReminded_ShouldReturnDEDUP_24H() {
        LocalDateTime now = LocalDateTime.now();
        TaskDueReminderPolicy.TaskReminderState state = new TaskDueReminderPolicy.TaskReminderState(
                now.plusDays(2),
                "TODO",
                now.minusHours(3),
                null
        );
        TaskDueReminderPolicy.SkipReason reason =
                policy.shouldSkipDueSoon(state, now.toLocalDate(), 3, now);
        assertThat(reason).isEqualTo(TaskDueReminderPolicy.SkipReason.DEDUP_24H);
    }

    @Test
    @DisplayName("shouldSkipDueSoon - 全部通过：null（应继续发送）")
    void shouldSkipDueSoon_AllChecksPass_ShouldReturnNull() {
        LocalDateTime now = LocalDateTime.now();
        TaskDueReminderPolicy.TaskReminderState state = new TaskDueReminderPolicy.TaskReminderState(
                now.plusDays(2),
                "TODO",
                now.minusDays(2),
                null
        );
        TaskDueReminderPolicy.SkipReason reason =
                policy.shouldSkipDueSoon(state, now.toLocalDate(), 3, now);
        assertThat(reason).isNull();
    }

    @Test
    @DisplayName("shouldSkipDueSoon - REVIEW 状态且在窗口内：null（应继续发送）")
    void shouldSkipDueSoon_ReviewStatusInWindow_ShouldReturnNull() {
        LocalDateTime now = LocalDateTime.now();
        TaskDueReminderPolicy.TaskReminderState state = new TaskDueReminderPolicy.TaskReminderState(
                now.plusDays(1),
                "REVIEW",
                null,
                null
        );
        TaskDueReminderPolicy.SkipReason reason =
                policy.shouldSkipDueSoon(state, now.toLocalDate(), 3, now);
        assertThat(reason).isNull();
    }

    // ============ shouldSkipOverdue ============

    @Test
    @DisplayName("shouldSkipOverdue - 已完成任务：COMPLETED")
    void shouldSkipOverdue_CompletedTask_ShouldReturnCOMPLETED() {
        TaskDueReminderPolicy.TaskReminderState state = new TaskDueReminderPolicy.TaskReminderState(
                LocalDateTime.now().minusDays(1),
                "COMPLETED",
                null,
                null
        );
        TaskDueReminderPolicy.SkipReason reason =
                policy.shouldSkipOverdue(state, LocalDate.now(), LocalDateTime.now());
        assertThat(reason).isEqualTo(TaskDueReminderPolicy.SkipReason.COMPLETED);
    }

    @Test
    @DisplayName("shouldSkipOverdue - 截止日期为空：INVALID_DUE_DATE")
    void shouldSkipOverdue_NullDueDate_ShouldReturnINVALID_DUE_DATE() {
        TaskDueReminderPolicy.TaskReminderState state = new TaskDueReminderPolicy.TaskReminderState(
                null,
                "TODO",
                null,
                null
        );
        TaskDueReminderPolicy.SkipReason reason =
                policy.shouldSkipOverdue(state, LocalDate.now(), LocalDateTime.now());
        assertThat(reason).isEqualTo(TaskDueReminderPolicy.SkipReason.INVALID_DUE_DATE);
    }

    @Test
    @DisplayName("shouldSkipOverdue - 未逾期（剩余天数=0，当天到期）：NOT_OVERDUE")
    void shouldSkipOverdue_DueToday_ShouldReturnNOT_OVERDUE() {
        LocalDateTime now = LocalDateTime.now();
        TaskDueReminderPolicy.TaskReminderState state = new TaskDueReminderPolicy.TaskReminderState(
                now,
                "TODO",
                null,
                null
        );
        TaskDueReminderPolicy.SkipReason reason =
                policy.shouldSkipOverdue(state, now.toLocalDate(), now);
        assertThat(reason).isEqualTo(TaskDueReminderPolicy.SkipReason.NOT_OVERDUE);
    }

    @Test
    @DisplayName("shouldSkipOverdue - 未逾期（未来到期）：NOT_OVERDUE")
    void shouldSkipOverdue_FutureDueDate_ShouldReturnNOT_OVERDUE() {
        TaskDueReminderPolicy.TaskReminderState state = new TaskDueReminderPolicy.TaskReminderState(
                LocalDateTime.now().plusDays(2),
                "TODO",
                null,
                null
        );
        TaskDueReminderPolicy.SkipReason reason =
                policy.shouldSkipOverdue(state, LocalDate.now(), LocalDateTime.now());
        assertThat(reason).isEqualTo(TaskDueReminderPolicy.SkipReason.NOT_OVERDUE);
    }

    @Test
    @DisplayName("shouldSkipOverdue - 24h 内已提醒过：DEDUP_24H")
    void shouldSkipOverdue_RecentlyReminded_ShouldReturnDEDUP_24H() {
        LocalDateTime now = LocalDateTime.now();
        TaskDueReminderPolicy.TaskReminderState state = new TaskDueReminderPolicy.TaskReminderState(
                now.minusDays(3),
                "TODO",
                null,
                now.minusHours(5)
        );
        TaskDueReminderPolicy.SkipReason reason =
                policy.shouldSkipOverdue(state, now.toLocalDate(), now);
        assertThat(reason).isEqualTo(TaskDueReminderPolicy.SkipReason.DEDUP_24H);
    }

    @Test
    @DisplayName("shouldSkipOverdue - 全部通过：null（应继续发送）")
    void shouldSkipOverdue_AllChecksPass_ShouldReturnNull() {
        LocalDateTime now = LocalDateTime.now();
        TaskDueReminderPolicy.TaskReminderState state = new TaskDueReminderPolicy.TaskReminderState(
                now.minusDays(3),
                "TODO",
                null,
                now.minusDays(2)
        );
        TaskDueReminderPolicy.SkipReason reason =
                policy.shouldSkipOverdue(state, now.toLocalDate(), now);
        assertThat(reason).isNull();
    }

    // ============ shouldEscalate ============

    @Test
    @DisplayName("shouldEscalate - 逾期超过 7 天：true")
    void shouldEscalate_Overdue8Days_ShouldReturnTrue() {
        LocalDate today = LocalDate.now();
        LocalDateTime dueDate = today.minusDays(8).atStartOfDay();
        assertThat(policy.shouldEscalate(dueDate, today)).isTrue();
    }

    @Test
    @DisplayName("shouldEscalate - 逾期正好 7 天：false（未超过阈值）")
    void shouldEscalate_OverdueExactly7Days_ShouldReturnFalse() {
        LocalDate today = LocalDate.now();
        LocalDateTime dueDate = today.minusDays(7).atStartOfDay();
        assertThat(policy.shouldEscalate(dueDate, today)).isFalse();
    }

    @Test
    @DisplayName("shouldEscalate - 逾期 6 天：false")
    void shouldEscalate_Overdue6Days_ShouldReturnFalse() {
        LocalDate today = LocalDate.now();
        LocalDateTime dueDate = today.minusDays(6).atStartOfDay();
        assertThat(policy.shouldEscalate(dueDate, today)).isFalse();
    }

    @Test
    @DisplayName("shouldEscalate - 未逾期：false")
    void shouldEscalate_NotOverdue_ShouldReturnFalse() {
        LocalDate today = LocalDate.now();
        LocalDateTime dueDate = today.plusDays(3).atStartOfDay();
        assertThat(policy.shouldEscalate(dueDate, today)).isFalse();
    }

    @Test
    @DisplayName("shouldEscalate - 截止日期为空：false")
    void shouldEscalate_NullDueDate_ShouldReturnFalse() {
        assertThat(policy.shouldEscalate(null, LocalDate.now())).isFalse();
    }

    // ============ computeRemainingDays ============

    @Test
    @DisplayName("computeRemainingDays - null dueDate：Long.MAX_VALUE")
    void computeRemainingDays_NullDueDate_ShouldReturnMaxValue() {
        assertThat(policy.computeRemainingDays(null, LocalDate.now())).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    @DisplayName("computeRemainingDays - 未来 5 天：5")
    void computeRemainingDays_FutureDate_ShouldReturnPositive() {
        LocalDate today = LocalDate.now();
        LocalDateTime dueDate = today.plusDays(5).atStartOfDay();
        assertThat(policy.computeRemainingDays(dueDate, today)).isEqualTo(5L);
    }

    @Test
    @DisplayName("computeRemainingDays - 过去 3 天：-3")
    void computeRemainingDays_PastDate_ShouldReturnNegative() {
        LocalDate today = LocalDate.now();
        LocalDateTime dueDate = today.minusDays(3).atStartOfDay();
        assertThat(policy.computeRemainingDays(dueDate, today)).isEqualTo(-3L);
    }

    // ============ shouldRemindToday ============

    @Test
    @DisplayName("shouldRemindToday - 从未提醒（null）：true")
    void shouldRemindToday_NullLastReminded_ShouldReturnTrue() {
        assertThat(policy.shouldRemindToday(null, LocalDateTime.now())).isTrue();
    }

    @Test
    @DisplayName("shouldRemindToday - 3 小时前提醒过：false")
    void shouldRemindToday_Within24h_ShouldReturnFalse() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastReminded = now.minusHours(3);
        assertThat(policy.shouldRemindToday(lastReminded, now)).isFalse();
    }

    @Test
    @DisplayName("shouldRemindToday - 25 小时前提醒过：true")
    void shouldRemindToday_Beyond24h_ShouldReturnTrue() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastReminded = now.minusHours(25);
        assertThat(policy.shouldRemindToday(lastReminded, now)).isTrue();
    }
}
