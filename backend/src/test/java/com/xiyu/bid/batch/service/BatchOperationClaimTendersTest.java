package com.xiyu.bid.batch.service;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BatchOperationClaimTendersTest extends AbstractBatchOperationServiceTest {

    @Test
    void batchClaimTenders_AllSuccess() {
        List<Long> tenderIds = Arrays.asList(1L, 2L);
        Long userId = 100L;

        when(tenderRepository.findById(1L)).thenReturn(Optional.of(testTender1));
        when(tenderRepository.findById(2L)).thenReturn(Optional.of(testTender2));
        when(tenderRepository.saveAll(anyList())).thenReturn(Arrays.asList(testTender1, testTender2));

        var response = batchOperationService.batchClaimTenders(tenderIds, userId);

        assertTrue(response.getSuccess());
        assertEquals(2, response.getSuccessCount());
        assertEquals(0, response.getFailureCount());
        assertTrue(response.isAllSuccess());

        verify(tenderRepository).saveAll(anyList());
        verify(auditLogService).log(any());
    }

    @Test
    void batchClaimTenders_PartialFailure() {
        List<Long> tenderIds = Arrays.asList(1L, 2L, 999L);
        Long userId = 100L;

        when(tenderRepository.findById(1L)).thenReturn(Optional.of(testTender1));
        when(tenderRepository.findById(2L)).thenReturn(Optional.of(testTender2));
        when(tenderRepository.findById(999L)).thenReturn(Optional.empty());
        when(tenderRepository.saveAll(anyList())).thenReturn(Arrays.asList(testTender1, testTender2));

        var response = batchOperationService.batchClaimTenders(tenderIds, userId);

        assertFalse(response.getSuccess());
        assertEquals(2, response.getSuccessCount());
        assertEquals(1, response.getFailureCount());
    }

    @Test
    void batchClaimTenders_rejectsTenderLinkedToProjectOutsideDataScope() {
        List<Long> tenderIds = Collections.singletonList(1L);
        Long userId = 100L;

        when(tenderRepository.findById(1L)).thenReturn(Optional.of(testTender1));
        when(projectRepository.findByTenderId(1L)).thenReturn(Collections.singletonList(testProject1));
        doThrow(new org.springframework.security.access.AccessDeniedException("权限不足"))
                .when(projectAccessScopeService).assertCurrentUserCanAccessProject(1L);

        var response = batchOperationService.batchClaimTenders(tenderIds, userId);

        assertFalse(response.getSuccess());
        assertEquals(0, response.getSuccessCount());
        assertEquals(1, response.getFailureCount());
        assertEquals("PERMISSION_DENIED", response.getErrors().get(0).getErrorCode());
        verify(tenderRepository, never()).saveAll(anyList());
    }

    @Test
    void batchClaimTenders_EmptyList() {
        List<Long> tenderIds = Collections.emptyList();

        assertThrows(IllegalArgumentException.class,
                () -> batchOperationService.batchClaimTenders(tenderIds, 100L));
    }
}
