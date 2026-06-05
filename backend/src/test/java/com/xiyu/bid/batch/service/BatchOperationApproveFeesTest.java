package com.xiyu.bid.batch.service;

import com.xiyu.bid.batch.dto.BatchApproveFeesRequest;
import com.xiyu.bid.fees.entity.Fee;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BatchOperationApproveFeesTest extends AbstractBatchOperationServiceTest {

    @Test
    void batchApproveFees_AllSuccess() {
        var request = BatchApproveFeesRequest.builder()
                .feeIds(Arrays.asList(1L, 2L))
                .paidBy("Finance Department")
                .build();

        when(feeRepository.findById(1L)).thenReturn(Optional.of(testFee1));
        when(feeRepository.findById(2L)).thenReturn(Optional.of(testFee2));
        when(feeRepository.saveAll(anyList())).thenReturn(Arrays.asList(testFee1, testFee2));

        var response = batchOperationService.batchApproveFees(request, 1L);

        assertTrue(response.getSuccess());
        assertEquals(2, response.getSuccessCount());
        assertEquals(0, response.getFailureCount());
        assertEquals(Fee.Status.PAID, testFee1.getStatus());
        assertEquals(Fee.Status.PAID, testFee2.getStatus());
        assertNotNull(testFee1.getPaymentDate());
        assertNotNull(testFee2.getPaymentDate());
        assertEquals("Finance Department", testFee1.getPaidBy());
        verify(feeRepository).saveAll(anyList());
    }

    @Test
    void batchApproveFees_OnlyPendingFeesCanBeApproved() {
        testFee1.setStatus(Fee.Status.PAID);
        var request = BatchApproveFeesRequest.builder()
                .feeIds(Arrays.asList(1L, 2L))
                .paidBy("Finance Department")
                .build();

        when(feeRepository.findById(1L)).thenReturn(Optional.of(testFee1));
        when(feeRepository.findById(2L)).thenReturn(Optional.of(testFee2));
        when(feeRepository.saveAll(anyList())).thenReturn(Collections.singletonList(testFee2));

        var response = batchOperationService.batchApproveFees(request, 1L);

        assertFalse(response.getSuccess());
        assertEquals(1, response.getSuccessCount());
        assertEquals(1, response.getFailureCount());
        assertEquals("INVALID_STATUS", response.getErrors().get(0).getErrorCode());
    }

    @Test
    void batchApproveFees_FeeNotFound_PartialFailure() {
        var request = BatchApproveFeesRequest.builder()
                .feeIds(Arrays.asList(1L, 999L))
                .build();

        when(feeRepository.findById(1L)).thenReturn(Optional.of(testFee1));
        when(feeRepository.findById(999L)).thenReturn(Optional.empty());
        when(feeRepository.saveAll(anyList())).thenReturn(Collections.singletonList(testFee1));

        var response = batchOperationService.batchApproveFees(request, 1L);

        assertFalse(response.getSuccess());
        assertEquals(1, response.getSuccessCount());
        assertEquals(1, response.getFailureCount());
        assertEquals("NOT_FOUND", response.getErrors().get(0).getErrorCode());
    }

    @Test
    void batchApproveFees_rejectsFeeOutsideProjectDataScope() {
        var request = BatchApproveFeesRequest.builder()
                .feeIds(Collections.singletonList(1L))
                .paidBy("Finance Department")
                .build();

        when(feeRepository.findById(1L)).thenReturn(Optional.of(testFee1));
        doThrow(new org.springframework.security.access.AccessDeniedException("权限不足"))
                .when(projectAccessScopeService).assertCurrentUserCanAccessProject(100L);

        var response = batchOperationService.batchApproveFees(request, 1L);

        assertFalse(response.getSuccess());
        assertEquals(0, response.getSuccessCount());
        assertEquals(1, response.getFailureCount());
        assertEquals("PERMISSION_DENIED", response.getErrors().get(0).getErrorCode());
        verify(feeRepository, never()).saveAll(anyList());
    }

    @Test
    void batchApproveFees_NullRequest_ThrowsException() {
        assertThrows(IllegalArgumentException.class,
                () -> batchOperationService.batchApproveFees(null, 1L));
    }

    @Test
    void batchApproveFees_EmptyList_ThrowsException() {
        var request = BatchApproveFeesRequest.builder()
                .feeIds(Collections.emptyList())
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> batchOperationService.batchApproveFees(request, 1L));
    }

    @Test
    void batchApproveFees_UsesDefaultPaidByWhenNotProvided() {
        var request = BatchApproveFeesRequest.builder()
                .feeIds(Collections.singletonList(1L))
                .paidBy(null)
                .payerId(1L)
                .build();

        when(feeRepository.findById(1L)).thenReturn(Optional.of(testFee1));
        when(feeRepository.saveAll(anyList())).thenReturn(Collections.singletonList(testFee1));

        var response = batchOperationService.batchApproveFees(request, 1L);

        assertTrue(response.getSuccess());
        assertNotNull(testFee1.getPaidBy());
        assertTrue(testFee1.getPaidBy().contains("System"));
    }

    @Test
    void batchApproveFees_SanitizesPaidByField() {
        String maliciousInput = "<script>alert('xss')</script>Finance Department";
        var request = BatchApproveFeesRequest.builder()
                .feeIds(Collections.singletonList(1L))
                .paidBy(maliciousInput)
                .build();

        when(feeRepository.findById(1L)).thenReturn(Optional.of(testFee1));
        when(feeRepository.saveAll(anyList())).thenReturn(Collections.singletonList(testFee1));

        var response = batchOperationService.batchApproveFees(request, 1L);

        assertTrue(response.getSuccess());
        assertEquals("Finance Department", testFee1.getPaidBy());
        assertFalse(testFee1.getPaidBy().contains("<script>"));
    }
}
