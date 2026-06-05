package com.xiyu.bid.batch.service;

import com.xiyu.bid.batch.dto.BatchProjectUpdateRequest;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.User;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BatchOperationUpdateProjectsTest extends AbstractBatchOperationServiceTest {

    @Test
    void batchUpdateProjects_UpdateStatus_AllSuccess() {
        var request = BatchProjectUpdateRequest.builder()
                .projectIds(Arrays.asList(1L, 2L))
                .status(Project.Status.EVALUATING)
                .build();

        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject1));
        when(projectRepository.findById(2L)).thenReturn(Optional.of(testProject2));
        when(projectRepository.saveAll(anyList())).thenReturn(Arrays.asList(testProject1, testProject2));

        var response = batchOperationService.batchUpdateProjects(request, 1L, User.Role.MANAGER);

        assertTrue(response.getSuccess());
        assertEquals(2, response.getSuccessCount());
        assertEquals(0, response.getFailureCount());
        assertEquals(Project.Status.EVALUATING, testProject1.getStatus());
        assertEquals(Project.Status.EVALUATING, testProject2.getStatus());
        verify(projectRepository).saveAll(anyList());
    }

    @Test
    void batchUpdateProjects_UpdateManager_AllSuccess() {
        Long newManagerId = 999L;
        var request = BatchProjectUpdateRequest.builder()
                .projectIds(Arrays.asList(1L, 2L))
                .managerId(newManagerId)
                .build();

        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject1));
        when(projectRepository.findById(2L)).thenReturn(Optional.of(testProject2));
        when(projectRepository.saveAll(anyList())).thenReturn(Arrays.asList(testProject1, testProject2));

        var response = batchOperationService.batchUpdateProjects(request, 1L, User.Role.ADMIN);

        assertTrue(response.getSuccess());
        assertEquals(2, response.getSuccessCount());
        assertEquals(newManagerId, testProject1.getManagerId());
        assertEquals(newManagerId, testProject2.getManagerId());
        verify(projectRepository).saveAll(anyList());
    }

    @Test
    void batchUpdateProjects_UpdateStatusAndManager_AllSuccess() {
        Long newManagerId = 999L;
        var request = BatchProjectUpdateRequest.builder()
                .projectIds(Collections.singletonList(1L))
                .status(Project.Status.EVALUATING)
                .managerId(newManagerId)
                .build();

        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject1));
        when(projectRepository.saveAll(anyList())).thenReturn(Collections.singletonList(testProject1));

        var response = batchOperationService.batchUpdateProjects(request, 1L, User.Role.ADMIN);

        assertTrue(response.getSuccess());
        assertEquals(Project.Status.EVALUATING, testProject1.getStatus());
        assertEquals(newManagerId, testProject1.getManagerId());
    }

    @Test
    void batchUpdateProjects_NoFieldsToUpdate_ThrowsException() {
        var request = BatchProjectUpdateRequest.builder()
                .projectIds(Collections.singletonList(1L))
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> batchOperationService.batchUpdateProjects(request, 1L, User.Role.ADMIN));
    }

    @Test
    void batchUpdateProjects_NullRequest_ThrowsException() {
        assertThrows(IllegalArgumentException.class,
                () -> batchOperationService.batchUpdateProjects(null, 1L, User.Role.ADMIN));
    }

    @Test
    void batchUpdateProjects_PermissionDenied_ForNonManager() {
        testProject1.setManagerId(999L);
        var request = BatchProjectUpdateRequest.builder()
                .projectIds(Collections.singletonList(1L))
                .status(Project.Status.EVALUATING)
                .build();

        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject1));

        var response = batchOperationService.batchUpdateProjects(request, 1L, User.Role.MANAGER);

        assertFalse(response.getSuccess());
        assertEquals(0, response.getSuccessCount());
        assertEquals(1, response.getFailureCount());
        assertEquals("PERMISSION_DENIED", response.getErrors().get(0).getErrorCode());
        verify(projectRepository, never()).saveAll(anyList());
    }

    @Test
    void batchUpdateProjects_AdminCanUpdateAnyProject() {
        testProject1.setManagerId(999L);
        var request = BatchProjectUpdateRequest.builder()
                .projectIds(Collections.singletonList(1L))
                .status(Project.Status.EVALUATING)
                .build();

        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject1));
        when(projectRepository.saveAll(anyList())).thenReturn(Collections.singletonList(testProject1));

        var response = batchOperationService.batchUpdateProjects(request, 1L, User.Role.ADMIN);

        assertTrue(response.getSuccess());
        assertEquals(1, response.getSuccessCount());
        verify(projectRepository, times(1)).saveAll(anyList());
    }

    @Test
    void batchUpdateProjects_ProjectNotFound_PartialFailure() {
        var request = BatchProjectUpdateRequest.builder()
                .projectIds(Arrays.asList(1L, 999L))
                .status(Project.Status.EVALUATING)
                .build();

        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject1));
        when(projectRepository.findById(999L)).thenReturn(Optional.empty());
        when(projectRepository.saveAll(anyList())).thenReturn(Collections.singletonList(testProject1));

        var response = batchOperationService.batchUpdateProjects(request, 1L, User.Role.ADMIN);

        assertFalse(response.getSuccess());
        assertEquals(1, response.getSuccessCount());
        assertEquals(1, response.getFailureCount());
        assertEquals("NOT_FOUND", response.getErrors().get(0).getErrorCode());
    }
}
