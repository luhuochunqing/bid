package com.xiyu.bid.audit.service;

import com.xiyu.bid.entity.AuditLog;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class AuditLogMapper {

    public AuditLog toEntity(AuditLogService.AuditLogEntry entry,
                             AuditRequestContext context,
                             LocalDateTime timestamp) {
        return AuditLog.builder()
                .userId(truncate(entry.getUserId(), 255))
                .username(truncate(entry.getUsername(), 100))
                .action(truncate(entry.getAction(), 50))
                .entityType(truncate(entry.getEntityType(), 100))
                .entityId(truncate(entry.getEntityId(), 100))
                .description(truncate(entry.getDescription(), 500))
                .oldValue(entry.getOldValue())
                .newValue(entry.getNewValue())
                .ipAddress(truncate(context == null ? null : context.ipAddress(), 50))
                .userAgent(truncate(context == null ? null : context.userAgent(), 500))
                .success(entry.getSuccess() != null ? entry.getSuccess() : true)
                .errorMessage(entry.getErrorMessage())
                .timestamp(timestamp)
                .build();
    }

    String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
