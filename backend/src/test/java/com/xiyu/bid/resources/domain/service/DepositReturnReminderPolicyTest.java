package com.xiyu.bid.resources.domain.service;

import com.xiyu.bid.bidresult.entity.BidResultFetchResult;
import com.xiyu.bid.resources.domain.model.DepositReturnReminderDecision;
import com.xiyu.bid.resources.domain.model.DepositReturnTrackingSnapshot;
import com.xiyu.bid.resources.domain.valueobject.DepositReturnReminderStage;
import com.xiyu.bid.resources.entity.Expense;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DepositReturnReminderPolicyTest {

    private final DepositReturnReminderPolicy policy = new DepositReturnReminderPolicy();

    @Test
    @DisplayName("已确认开标结果且到期前阈值内时应提醒")
    void shouldRemindWhenDueSoon() {
        DepositReturnReminderDecision decision = policy.evaluate(
                new DepositReturnTrackingSnapshot(
                        1L,
                        2L,
                        Expense.ExpenseStatus.APPROVED,
                        LocalDate.of(2026, 4, 25),
                        null,
                        BidResultFetchResult.Result.WON
                ),
                7,
                LocalDate.of(2026, 4, 20),
                LocalDateTime.of(2026, 4, 20, 10, 0)
        );

        assertThat(decision.shouldRemind()).isTrue();
        assertThat(decision.stage()).isEqualTo(DepositReturnReminderStage.DUE_SOON);
        assertThat(decision.daysUntilDue()).isEqualTo(5);
    }

    @Test
    @DisplayName("已逾期且当天未提醒时应生成逾期提醒")
    void shouldRemindWhenOverdue() {
        DepositReturnReminderDecision decision = policy.evaluate(
                new DepositReturnTrackingSnapshot(
                        1L,
                        2L,
                        Expense.ExpenseStatus.RETURN_REQUESTED,
                        LocalDate.of(2026, 4, 10),
                        null,
                        BidResultFetchResult.Result.LOST
                ),
                7,
                LocalDate.of(2026, 4, 20),
                LocalDateTime.of(2026, 4, 20, 10, 0)
        );

        assertThat(decision.shouldRemind()).isTrue();
        assertThat(decision.stage()).isEqualTo(DepositReturnReminderStage.OVERDUE);
        assertThat(decision.overdueDays()).isEqualTo(10);
    }

    @Test
    @DisplayName("没有确认开标结果时不应提醒")
    void shouldNotRemindWithoutConfirmedBidResult() {
        DepositReturnReminderDecision decision = policy.evaluate(
                new DepositReturnTrackingSnapshot(
                        1L,
                        2L,
                        Expense.ExpenseStatus.APPROVED,
                        LocalDate.of(2026, 4, 25),
                        null,
                        null
                ),
                7,
                LocalDate.of(2026, 4, 20),
                LocalDateTime.of(2026, 4, 20, 10, 0)
        );

        assertThat(decision.shouldRemind()).isFalse();
    }

    @Test
    @DisplayName("当天已经提醒过时不应重复提醒")
    void shouldNotRemindTwiceInSameDay() {
        DepositReturnReminderDecision decision = policy.evaluate(
                new DepositReturnTrackingSnapshot(
                        1L,
                        2L,
                        Expense.ExpenseStatus.APPROVED,
                        LocalDate.of(2026, 4, 25),
                        LocalDateTime.of(2026, 4, 20, 9, 0),
                        BidResultFetchResult.Result.WON
                ),
                7,
                LocalDate.of(2026, 4, 20),
                LocalDateTime.of(2026, 4, 20, 10, 0)
        );

        assertThat(decision.shouldRemind()).isFalse();
    }
}
