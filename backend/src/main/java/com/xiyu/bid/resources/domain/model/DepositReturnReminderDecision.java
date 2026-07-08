package com.xiyu.bid.resources.domain.model;

import com.xiyu.bid.resources.domain.valueobject.DepositReturnReminderStage;

public record DepositReturnReminderDecision(
        boolean shouldRemind,
        DepositReturnReminderStage stage,
        long overdueDays,
        long daysUntilDue
) {

    public String relatedId(Long expenseId, String expectedReturnDate) {
        // 格式必须为 "EntityType:EntityId"（单冒号），多段信息放 payload
        return String.format("DepositReturn:%s", expenseId);
    }
}
