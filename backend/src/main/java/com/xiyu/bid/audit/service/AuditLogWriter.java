package com.xiyu.bid.audit.service;

import com.xiyu.bid.audit.core.AuditActionPolicy;
import com.xiyu.bid.entity.AuditLog;
import com.xiyu.bid.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogWriter {

    private final AuditLogRepository auditLogRepository;
    private final AuditActionPolicy auditActionPolicy;
    private final AuditLogMapper auditLogMapper;

    @Async("auditLogExecutor")
    public void logAsync(AuditLogService.AuditLogEntry entry, AuditRequestContext context) {
        try {
            saveIfRecordable(entry, context);
        } catch (RuntimeException e) {
            log.error("Failed to save audit log", e);
        }
    }

    public AuditLog logSync(AuditLogService.AuditLogEntry entry, AuditRequestContext context) {
        try {
            return saveIfRecordable(entry, context);
        } catch (RuntimeException e) {
            log.error("Failed to save audit log synchronously", e);
            throw new RuntimeException("Failed to create audit log", e);
        }
    }

    private AuditLog saveIfRecordable(AuditLogService.AuditLogEntry entry, AuditRequestContext context) {
        if (entry == null || !auditActionPolicy.shouldRecord(entry.getAction())) {
            log.debug("Skipped non-key audit action: {}", entry == null ? null : entry.getAction());
            return null;
        }
        AuditLog auditLog = auditLogMapper.toEntity(entry, context, LocalDateTime.now());
        AuditLog saved = auditLogRepository.save(auditLog);
        log.debug("Audit log saved: {}", saved.getAction());
        return saved;
    }
}
