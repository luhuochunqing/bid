package com.xiyu.bid.audit.service;

import com.xiyu.bid.audit.dto.AuditLogItemDTO;
import com.xiyu.bid.entity.AuditLog;
import com.xiyu.bid.entity.User;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
public class AuditLogItemMapper {

    static final DateTimeFormatter AUDIT_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public AuditLogItemDTO toItemDto(AuditLog auditLog, User user) {
        return AuditLogItemDTO.builder()
                .id(auditLog.getId())
                .time(auditLog.getTimestamp() == null ? null : auditLog.getTimestamp().format(AUDIT_TIME_FORMATTER))
                .operator(resolveOperator(auditLog, user))
                .department(user == null || user.getDepartmentName() == null ? "-" : user.getDepartmentName())
                .role(resolveRole(user))
                .actionType(normalizeAction(auditLog.getAction()))
                .module(normalizeModule(auditLog.getEntityType()))
                .target(resolveTarget(auditLog))
                .detail(auditLog.getDescription())
                .ip(auditLog.getIpAddress())
                .status(Boolean.TRUE.equals(auditLog.getSuccess()) ? "success" : "failed")
                .build();
    }

    String normalizeModule(String entityType) {
        if (entityType == null || entityType.isBlank()) {
            return "system";
        }
        String normalized = entityType.toLowerCase(Locale.ROOT);
        if (normalized.contains("project")) {
            return "project";
        }
        if (normalized.contains("tender") || normalized.contains("bidding")) {
            return "bidding";
        }
        if (normalized.contains("qualification")) {
            return "qualification";
        }
        if (normalized.contains("expense") || normalized.contains("fee")) {
            return "expense";
        }
        if (normalized.contains("account") || normalized.contains("bar")) {
            return "account";
        }
        if (normalized.contains("template") || normalized.contains("case")) {
            return "knowledge";
        }
        if (normalized.contains("analytics") || normalized.contains("ai")) {
            return "analytics";
        }
        if (normalized.contains("document") || normalized.contains("archive") || normalized.contains("export")) {
            return "document";
        }
        if (normalized.contains("task")) {
            return "task";
        }
        return "system";
    }

    private String resolveOperator(AuditLog auditLog, User user) {
        String name = null;
        if (user != null && user.getFullName() != null && !user.getFullName().isBlank()) {
            name = user.getFullName();
        } else if (auditLog.getUsername() != null && !auditLog.getUsername().isBlank()) {
            name = auditLog.getUsername();
        }
        if (name == null) {
            return "未知用户";
        }
        String workNo = user != null && user.getUsername() != null ? user.getUsername() : "";
        if (!workNo.isBlank()) {
            return name + "（" + workNo + "）";
        }
        return name;
    }

    private String resolveRole(User user) {
        if (user == null || user.getRole() == null) {
            return "unknown";
        }
        // SAFE: 审计日志落库展示字段。审计日志要求"操作发生时"的角色快照，与 OSS 缓存当下值无关。
        // 故有意读取 DB 当前 roleCode 反映日志时刻的真实角色（与行为追溯场景一致）。CO-373 治理范围外。
        return user.getRoleCode().toLowerCase(Locale.ROOT);
    }

    private String normalizeAction(String action) {
        return action == null ? "unknown" : action.toLowerCase(Locale.ROOT);
    }

    private String resolveTarget(AuditLog auditLog) {
        if (auditLog.getEntityId() != null && !auditLog.getEntityId().isBlank()) {
            return auditLog.getEntityId();
        }
        if (auditLog.getEntityType() != null && !auditLog.getEntityType().isBlank()) {
            return auditLog.getEntityType();
        }
        return "-";
    }
}
