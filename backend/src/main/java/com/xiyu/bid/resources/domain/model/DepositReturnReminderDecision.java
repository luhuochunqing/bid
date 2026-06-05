package com.xiyu.bid.resources.domain.model;

import com.xiyu.bid.resources.domain.valueobject.DepositReturnReminderStage;

public record DepositReturnReminderDecision(
        boolean shouldRemind,
        DepositReturnReminderStage stage,
        long overdueDays,
        long daysUntilDue
) {

    public String relatedId(Long expenseId, String expectedReturnDate) {
        return String.format("DepositReturn:%s:%s:%s", expenseId, expectedReturnDate, stage.name());
    }
}
