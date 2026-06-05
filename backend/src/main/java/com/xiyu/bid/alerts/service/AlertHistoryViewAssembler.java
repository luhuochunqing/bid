package com.xiyu.bid.alerts.service;

import com.xiyu.bid.alerts.dto.AlertHistoryResponse;
import com.xiyu.bid.alerts.entity.AlertHistory;
import com.xiyu.bid.alerts.entity.AlertRule;

final class AlertHistoryViewAssembler {
    private AlertHistoryViewAssembler() {
    }

    static AlertHistoryResponse toResponse(AlertHistory entity, AlertRule rule) {
        return AlertHistoryResponse.builder()
                .id(entity.getId())
                .ruleId(entity.getRuleId())
                .ruleName(rule == null ? "未知规则" : rule.getName())
                .alertType(rule == null || rule.getType() == null ? "SYSTEM" : rule.getType().name())
                .severity(entity.getLevel() == null ? "INFO" : entity.getLevel().name())
                .message(entity.getMessage())
                .projectName(entity.getRelatedId())
                .status(resolveStatus(entity))
                .relatedId(entity.getRelatedId())
                .createdAt(entity.getCreatedAt())
                .acknowledgedAt(entity.getAcknowledgedAt())
                .resolvedAt(entity.getResolvedAt())
                .build();
    }

    static String resolveStatus(AlertHistory entity) {
        if (Boolean.TRUE.equals(entity.getResolved())) {
            return "RESOLVED";
        }
        if (entity.getAcknowledgedAt() != null) {
            return "ACKNOWLEDGED";
        }
        return "ACTIVE";
    }
}
