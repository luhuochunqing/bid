package com.xiyu.bid.batch.service;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.audit.service.AuditLogService;
import com.xiyu.bid.audit.service.IAuditLogService;
import com.xiyu.bid.batch.dto.BatchOperationResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class BatchOperationLogServiceTest {

    private final IAuditLogService auditLogService = mock(IAuditLogService.class);
    private final BatchOperationLogService service = new BatchOperationLogService(auditLogService);

    @Test
    void record_shouldWriteSingleReadableSummaryEntry() {
        BatchOperationResponse response = BatchOperationResponse.builder()
                .successCount(2)
                .failureCount(0)
                .successIds(List.of(11L, 12L))
                .build();

        service.record(response, "PROJECT", "UPDATE", 7L);

        AuditLogService.AuditLogEntry entry = captureEntry();
        assertEquals("PROJECT", entry.getEntityType());
        assertEquals("UPDATE", entry.getAction());
        assertEquals("11", entry.getEntityId());
        assertEquals("7", entry.getUserId());
        assertTrue(entry.getSuccess());
        assertEquals("Batch update: 2 success, 0 failed. IDs: 11,12", entry.getDescription());
    }

    @Test
    void record_shouldMarkPartialFailureAsFailed() {
        BatchOperationResponse response = BatchOperationResponse.builder()
                .successCount(1)
                .failureCount(1)
                .successIds(List.of(11L))
                .build();

        service.record(response, "TENDER", "CLAIM", 7L);

        assertFalse(captureEntry().getSuccess());
    }

    @Test
    void batchFacade_shouldNotTriggerAuditableAspectAgain() {
        List<String> annotatedMethods = List.of(BatchOperationService.class.getDeclaredMethods()).stream()
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> method.isAnnotationPresent(Auditable.class))
                .map(Method::getName)
                .sorted()
                .toList();

        assertTrue(
                annotatedMethods.isEmpty(),
                "Batch facade delegates to BatchOperationLogService.record; remove @Auditable from: " + annotatedMethods
        );
    }

    private AuditLogService.AuditLogEntry captureEntry() {
        ArgumentCaptor<AuditLogService.AuditLogEntry> captor =
                ArgumentCaptor.forClass(AuditLogService.AuditLogEntry.class);
        verify(auditLogService, times(1)).log(captor.capture());
        return captor.getValue();
    }
}
