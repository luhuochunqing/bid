package com.xiyu.bid.resources.domain.service;

import com.xiyu.bid.resources.domain.model.DepositReturnReminderDecision;
import com.xiyu.bid.resources.domain.model.DepositReturnTrackingSnapshot;
import com.xiyu.bid.resources.domain.valueobject.DepositReturnReminderStage;
import com.xiyu.bid.resources.entity.Expense;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public class DepositReturnReminderPolicy {

    public DepositReturnReminderDecision evaluate(
            DepositReturnTrackingSnapshot snapshot,
            int warnDays,
            LocalDate today,
            LocalDateTime now
    ) {
        if (!isReminderEligible(snapshot)) {
            return noReminder();
        }

        long daysUntilDue = ChronoUnit.DAYS.between(today, snapshot.expectedReturnDate());
        boolean remindedToday = snapshot.lastReminderAt() != null
                && snapshot.lastReminderAt().toLocalDate().isEqual(now.toLocalDate());

        if (daysUntilDue < 0 && !remindedToday) {
            return new DepositReturnReminderDecision(
                    true,
                    DepositReturnReminderStage.OVERDUE,
                    Math.abs(daysUntilDue),
                    daysUntilDue
            );
        }

        if (daysUntilDue <= warnDays && daysUntilDue >= 0 && !remindedToday) {
            return new DepositReturnReminderDecision(
                    true,
                    DepositReturnReminderStage.DUE_SOON,
                    0,
                    daysUntilDue
            );
        }

        return noReminder();
    }

    public boolean canSendManualReminder(DepositReturnTrackingSnapshot snapshot) {
        return isReminderEligible(snapshot);
    }

    private DepositReturnReminderDecision noReminder() {
        return new DepositReturnReminderDecision(
                false,
                DepositReturnReminderStage.DUE_SOON,
                0,
                Long.MAX_VALUE
        );
    }

    private boolean isReminderEligible(DepositReturnTrackingSnapshot snapshot) {
        if (snapshot == null
                || !snapshot.hasConfirmedBidResult()
                || snapshot.expectedReturnDate() == null) {
            return false;
        }

        return snapshot.expenseStatus() == Expense.ExpenseStatus.APPROVED
                || snapshot.expenseStatus() == Expense.ExpenseStatus.PAID
                || snapshot.expenseStatus() == Expense.ExpenseStatus.RETURN_REQUESTED;
    }
}
