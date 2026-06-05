package com.xiyu.bid.audit.service;

import com.xiyu.bid.audit.core.AuditActionPolicy;
import com.xiyu.bid.entity.AuditLog;
import com.xiyu.bid.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditLogWriterTest {

    private final AuditLogRepository repository = mock(AuditLogRepository.class);
    private final AuditLogWriter writer = new AuditLogWriter(
            repository,
            new AuditActionPolicy(),
            new AuditLogMapper()
    );

    @Test
    void logSync_shouldSkipReadActionsAtWriteSource() {
        AuditLogService.AuditLogEntry entry = AuditLogService.AuditLogEntry.builder()
                .action("READ")
                .entityType("Template")
                .success(true)
                .build();

        AuditLog saved = writer.logSync(entry, new AuditRequestContext("127.0.0.1", "JUnit"));

        assertNull(saved);
        verify(repository, never()).save(any());
    }

    @Test
    void logSync_shouldPersistKeyChangeActionsWithRequestContext() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        AuditLogService.AuditLogEntry entry = AuditLogService.AuditLogEntry.builder()
                .userId("7")
                .username("audit-user")
                .action("UPDATE_STATUS")
                .entityType("Project")
                .entityId("100")
                .description("更新项目状态")
                .success(true)
                .build();

        AuditLog saved = writer.logSync(entry, new AuditRequestContext("10.0.0.1", "JUnit"));

        assertEquals("UPDATE_STATUS", saved.getAction());
        assertEquals("Project", saved.getEntityType());
        assertEquals("100", saved.getEntityId());
        assertEquals("10.0.0.1", saved.getIpAddress());
        assertEquals("JUnit", saved.getUserAgent());
    }
}
