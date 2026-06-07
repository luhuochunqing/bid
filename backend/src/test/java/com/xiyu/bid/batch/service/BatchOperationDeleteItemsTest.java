package com.xiyu.bid.batch.service;

import com.xiyu.bid.entity.User;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BatchOperationDeleteItemsTest extends AbstractBatchOperationServiceTest {

    @Test
    void batchDeleteItems_TenderType() {
        List<Long> ids = Collections.singletonList(1L);

        when(tenderRepository.findById(1L)).thenReturn(Optional.of(testTender1));
        doNothing().when(tenderRepository).deleteAll(anyList());

        var response = batchOperationService.batchDeleteItems("tender", ids, 1L, User.Role.ADMIN);

        assertTrue(response.getSuccess());
        assertEquals(1, response.getSuccessCount());
        assertEquals(0, response.getFailureCount());
    }

    @Test
    void batchDeleteItems_TenderType_RestrictedToAdminOnly() {
        List<Long> ids = Collections.singletonList(1L);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> batchOperationService.batchDeleteItems("tender", ids, 1L, User.Role.MANAGER));
    }

    @Test
    void batchDeleteItems_TaskType() {
        List<Long> ids = Collections.singletonList(1L);

        when(taskRepository.findById(1L)).thenReturn(Optional.of(testTask1));
        doNothing().when(taskRepository).deleteAll(anyList());

        var response = batchOperationService.batchDeleteItems("task", ids, 1L, User.Role.MANAGER);

        assertTrue(response.getSuccess());
        assertEquals(1, response.getSuccessCount());
    }

    @Test
    void batchDeleteItems_ProjectType() {
        List<Long> ids = Collections.singletonList(1L);

        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject1));
        doNothing().when(projectRepository).deleteAll(anyList());

        var response = batchOperationService.batchDeleteItems("project", ids, 1L, User.Role.MANAGER);

        assertTrue(response.getSuccess());
        assertEquals(1, response.getSuccessCount());
    }

    @Test
    void batchDeleteItems_UnsupportedType() {
        assertThrows(IllegalArgumentException.class,
                () -> batchOperationService.batchDeleteItems("unsupported_type", Collections.singletonList(1L), 1L, User.Role.MANAGER));
    }

    @Test
    void batchDeleteItems_CaseInsensitive() {
        List<Long> ids = Collections.singletonList(1L);

        when(tenderRepository.findById(1L)).thenReturn(Optional.of(testTender1));
        doNothing().when(tenderRepository).deleteAll(anyList());

        var response = batchOperationService.batchDeleteItems("TENDER", ids, 1L, User.Role.ADMIN);

        assertTrue(response.getSuccess());
        assertEquals(1, response.getSuccessCount());
    }

    @Test
    void batchDeleteItems_ProjectTypeHonorsProjectOwnershipForManagers() {
        List<Long> ids = Collections.singletonList(1L);
        testProject1.setManagerId(999L);

        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject1));

        var response = batchOperationService.batchDeleteItems("project", ids, 1L, User.Role.MANAGER);

        assertFalse(response.getSuccess());
        assertEquals(0, response.getSuccessCount());
        assertEquals(1, response.getFailureCount());
        assertEquals("PERMISSION_DENIED", response.getErrors().get(0).getErrorCode());
        verify(projectRepository, never()).deleteAll(anyList());
    }

    @Test
    void batchDeleteItems_ProjectTypeAllowsAdminsAcrossProjects() {
        List<Long> ids = Collections.singletonList(1L);
        testProject1.setManagerId(999L);

        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject1));
        doNothing().when(projectRepository).deleteAll(anyList());

        var response = batchOperationService.batchDeleteItems("project", ids, 1L, User.Role.ADMIN);

        assertTrue(response.getSuccess());
        assertEquals(1, response.getSuccessCount());
        verify(projectRepository).deleteAll(anyList());
    }
}
