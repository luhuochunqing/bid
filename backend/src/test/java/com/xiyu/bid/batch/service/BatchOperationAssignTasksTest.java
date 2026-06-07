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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BatchOperationAssignTasksTest extends AbstractBatchOperationServiceTest {

    @Test
    void batchAssignTasks_AllSuccess() {
        List<Long> taskIds = Arrays.asList(1L, 2L);
        Long assigneeId = 200L;

        when(taskRepository.findById(1L)).thenReturn(Optional.of(testTask1));
        when(taskRepository.findById(2L)).thenReturn(Optional.of(testTask2));
        when(taskRepository.saveAll(anyList())).thenReturn(Arrays.asList(testTask1, testTask2));

        var response = batchOperationService.batchAssignTasks(taskIds, assigneeId);

        assertTrue(response.getSuccess());
        assertEquals(2, response.getSuccessCount());
        assertEquals(0, response.getFailureCount());
        assertTrue(response.isAllSuccess());
    }

    @Test
    void batchAssignTasks_PartialFailure() {
        List<Long> taskIds = Arrays.asList(1L, 999L);
        Long assigneeId = 200L;

        when(taskRepository.findById(1L)).thenReturn(Optional.of(testTask1));
        when(taskRepository.findById(999L)).thenReturn(Optional.empty());
        when(taskRepository.saveAll(anyList())).thenReturn(Collections.singletonList(testTask1));

        var response = batchOperationService.batchAssignTasks(taskIds, assigneeId);

        assertFalse(response.getSuccess());
        assertEquals(1, response.getSuccessCount());
        assertEquals(1, response.getFailureCount());
    }

    @Test
    void batchAssignTasks_rejectsTaskOutsideProjectDataScope() {
        List<Long> taskIds = Collections.singletonList(1L);
        Long assigneeId = 200L;

        when(taskRepository.findById(1L)).thenReturn(Optional.of(testTask1));
        doThrow(new org.springframework.security.access.AccessDeniedException("权限不足"))
                .when(projectAccessScopeService).assertCurrentUserCanAccessProject(100L);

        var response = batchOperationService.batchAssignTasks(taskIds, assigneeId);

        assertFalse(response.getSuccess());
        assertEquals(0, response.getSuccessCount());
        assertEquals(1, response.getFailureCount());
        assertEquals("PERMISSION_DENIED", response.getErrors().get(0).getErrorCode());
        verify(taskRepository, never()).saveAll(anyList());
    }

    @Test
    void batchAssignTasks_EmptyList() {
        assertThrows(IllegalArgumentException.class,
                () -> batchOperationService.batchAssignTasks(Collections.emptyList(), 200L));
    }
}
