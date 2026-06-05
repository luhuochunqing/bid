package com.xiyu.bid.businessqualification.domain.valueobject;

import java.time.LocalDateTime;

public record ReminderPolicy(
        boolean enabled,
        int reminderDays,
        LocalDateTime lastRemindedAt
) {

    public ReminderPolicy recordReminder(LocalDateTime remindedAt) {
        return new ReminderPolicy(enabled, reminderDays, remindedAt);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getReminderDays() {
        return reminderDays;
    }

    public LocalDateTime getLastRemindedAt() {
        return lastRemindedAt;
    }
}
