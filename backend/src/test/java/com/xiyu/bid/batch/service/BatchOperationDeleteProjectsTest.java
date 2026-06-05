package com.xiyu.bid.batch.service;

import com.xiyu.bid.entity.User;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BatchOperationDeleteProjectsTest extends AbstractBatchOperationServiceTest {

    @Test
    void batchDeleteProjects_AllSuccess() {
        List<Long> projectIds = Arrays.asList(1L, 2L);

        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject1));
        when(projectRepository.findById(2L)).thenReturn(Optional.of(testProject2));
        doNothing().when(projectRepository).deleteAll(anyList());

        var response = batchOperationService.batchDeleteProjects(projectIds, 1L, User.Role.MANAGER);

        assertTrue(response.getSuccess());
        assertEquals(2, response.getSuccessCount());
        assertEquals(0, response.getFailureCount());
        assertTrue(response.isAllSuccess());
    }

    @Test
    void batchDeleteProjects_PermissionCheck() {
        testProject1.setManagerId(999L);
        List<Long> projectIds = Collections.singletonList(1L);

        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject1));

        var response = batchOperationService.batchDeleteProjects(projectIds, 100L, User.Role.MANAGER);

        assertFalse(response.getSuccess());
        assertEquals(0, response.getSuccessCount());
        assertEquals(1, response.getFailureCount());
        assertEquals("PERMISSION_DENIED", response.getErrors().get(0).getErrorCode());
        verify(projectRepository, never()).deleteAll(anyList());
    }

    @Test
    void batchDeleteProjects_rejectsProjectOutsideDataScope() {
        List<Long> projectIds = Collections.singletonList(1L);

        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject1));
        doThrow(new org.springframework.security.access.AccessDeniedException("权限不足"))
                .when(projectAccessScopeService).assertCurrentUserCanAccessProject(1L);

        var response = batchOperationService.batchDeleteProjects(projectIds, 1L, User.Role.MANAGER);

        assertFalse(response.getSuccess());
        assertEquals(0, response.getSuccessCount());
        assertEquals(1, response.getFailureCount());
        assertEquals("PERMISSION_DENIED", response.getErrors().get(0).getErrorCode());
        verify(projectRepository, never()).deleteAll(anyList());
    }

    @Test
    void batchDeleteProjects_EmptyList() {
        assertThrows(IllegalArgumentException.class,
                () -> batchOperationService.batchDeleteProjects(Collections.emptyList(), 100L, User.Role.MANAGER));
    }
}
