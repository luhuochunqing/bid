package com.xiyu.bid.alerts.service;

import com.xiyu.bid.alerts.entity.AlertHistory;

import java.time.LocalDateTime;

final class AlertLifecyclePolicy {

    void acknowledge(AlertHistory alertHistory, LocalDateTime now) {
        if (Boolean.TRUE.equals(alertHistory.getResolved())) {
            throw new IllegalStateException("Resolved alert cannot be acknowledged again");
        }
        if (alertHistory.getAcknowledgedAt() == null) {
            alertHistory.setAcknowledgedAt(now);
        }
    }

    void resolve(AlertHistory alertHistory, LocalDateTime now) {
        if (Boolean.TRUE.equals(alertHistory.getResolved())) {
            throw new IllegalStateException("Resolved alert cannot be resolved again");
        }
        acknowledge(alertHistory, now);
        alertHistory.setResolved(true);
        alertHistory.setResolvedAt(now);
    }
}
