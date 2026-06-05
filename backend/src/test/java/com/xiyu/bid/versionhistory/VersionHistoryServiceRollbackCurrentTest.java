package com.xiyu.bid.versionhistory;

import com.xiyu.bid.audit.service.AuditLogService;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.versionhistory.dto.DocumentVersionDTO;
import com.xiyu.bid.versionhistory.entity.DocumentVersion;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VersionHistoryServiceRollbackCurrentTest extends AbstractVersionHistoryServiceTest {

    @Test
    void rollbackToVersion_WithValidData_ShouldCreateNewVersion() {
        when(repository.findById(1L)).thenReturn(Optional.of(testVersion));
        when(repository.findCurrentVersionByProjectId(100L)).thenReturn(Optional.of(testVersion2));
        when(repository.getNextVersionNumber(100L)).thenReturn(3);
        when(repository.save(any(DocumentVersion.class))).thenAnswer(invocation -> {
            DocumentVersion version = invocation.getArgument(0);
            if (version.getId() == null) {
                version.setId(3L);
            }
            return version;
        });

        DocumentVersionDTO result = versionHistoryService.rollbackToVersion(100L, 1L, 1L);

        assertThat(result).isNotNull();
        assertThat(result.getVersionNumber()).isEqualTo(3);
        assertThat(result.getContent()).isEqualTo("Initial content");
        assertThat(result.getChangeSummary()).contains("Rollback to version 1");
        assertThat(result.getIsCurrent()).isTrue();

        verify(repository, atLeastOnce()).save(any(DocumentVersion.class));
        verify(auditLogService).log(any(AuditLogService.AuditLogEntry.class));
    }

    @Test
    void rollbackToVersion_WhenProjectOutsideCurrentUserScope_ShouldThrowAccessDeniedBeforeReadingVersion() {
        doThrow(new AccessDeniedException("权限不足，无法访问该项目"))
                .when(projectAccessScopeService).assertCurrentUserCanAccessProject(100L);

        assertThatThrownBy(() -> versionHistoryService.rollbackToVersion(100L, 1L, 1L))
                .isInstanceOf(AccessDeniedException.class);

        verify(projectAccessScopeService).assertCurrentUserCanAccessProject(100L);
        verify(repository, org.mockito.Mockito.never()).findById(any());
    }

    @Test
    void rollbackToVersion_WithNullProjectId_ShouldThrowException() {
        assertThatThrownBy(() -> versionHistoryService.rollbackToVersion(null, 1L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Project ID is required");
    }

    @Test
    void rollbackToVersion_WithNullVersionId_ShouldThrowException() {
        assertThatThrownBy(() -> versionHistoryService.rollbackToVersion(100L, null, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Version ID is required");
    }

    @Test
    void rollbackToVersion_WithNullUserId_ShouldThrowException() {
        assertThatThrownBy(() -> versionHistoryService.rollbackToVersion(100L, 1L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User ID is required");
    }

    @Test
    void rollbackToVersion_WithInvalidVersionId_ShouldThrowException() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> versionHistoryService.rollbackToVersion(100L, 999L, 1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Version not found with id: 999");
    }

    @Test
    void rollbackToVersion_WithVersionFromDifferentProject_ShouldThrowException() {
        DocumentVersion otherProjectVersion = DocumentVersion.builder()
                .id(1L)
                .projectId(200L)
                .versionNumber(1)
                .content("Content")
                .build();

        when(repository.findById(1L)).thenReturn(Optional.of(otherProjectVersion));

        assertThatThrownBy(() -> versionHistoryService.rollbackToVersion(100L, 1L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong to the specified project");
    }

    @Test
    void rollbackToVersion_WithNoCurrentVersion_ShouldThrowException() {
        when(repository.findById(1L)).thenReturn(Optional.of(testVersion));
        when(repository.findCurrentVersionByProjectId(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> versionHistoryService.rollbackToVersion(100L, 1L, 1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("No current version found for project: 100");
    }

    @Test
    void rollbackToVersion_ShouldMarkOldCurrentAsNotCurrent() {
        when(repository.findById(1L)).thenReturn(Optional.of(testVersion));
        when(repository.findCurrentVersionByProjectId(100L)).thenReturn(Optional.of(testVersion2));
        when(repository.getNextVersionNumber(100L)).thenReturn(3);
        when(repository.save(any(DocumentVersion.class))).thenAnswer(invocation -> {
            DocumentVersion version = invocation.getArgument(0);
            if (version.getId() == null) {
                version.setId(3L);
            }
            return version;
        });

        versionHistoryService.rollbackToVersion(100L, 1L, 1L);

        assertThat(testVersion2.getIsCurrent()).isFalse();
    }

    @Test
    void rollbackToVersion_WithLargeProjectId_ShouldHandleCorrectly() {
        Long largeProjectId = Long.MAX_VALUE;
        DocumentVersion largeProjectVersion = DocumentVersion.builder()
                .id(1L)
                .projectId(largeProjectId)
                .versionNumber(1)
                .content("Content")
                .build();

        when(repository.findById(1L)).thenReturn(Optional.of(largeProjectVersion));
        when(repository.findCurrentVersionByProjectId(largeProjectId)).thenReturn(Optional.of(testVersion2));
        when(repository.getNextVersionNumber(largeProjectId)).thenReturn(2);
        when(repository.save(any(DocumentVersion.class))).thenAnswer(invocation -> {
            DocumentVersion version = invocation.getArgument(0);
            if (version.getId() == null) {
                version.setId(2L);
            }
            return version;
        });

        DocumentVersionDTO result = versionHistoryService.rollbackToVersion(largeProjectId, 1L, 1L);

        assertThat(result.getProjectId()).isEqualTo(largeProjectId);
    }

    @Test
    void markAsCurrent_WithValidVersionId_ShouldUpdateCorrectly() {
        when(repository.findById(1L)).thenReturn(Optional.of(testVersion));
        when(repository.findCurrentVersionByProjectId(100L)).thenReturn(Optional.of(testVersion2));
        when(repository.save(any(DocumentVersion.class))).thenReturn(testVersion);

        versionHistoryService.markAsCurrent(1L);

        assertThat(testVersion.getIsCurrent()).isTrue();
        assertThat(testVersion2.getIsCurrent()).isFalse();
        verify(repository, atLeast(2)).save(any(DocumentVersion.class));
    }

    @Test
    void markAsCurrent_WithNullVersionId_ShouldThrowException() {
        assertThatThrownBy(() -> versionHistoryService.markAsCurrent(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Version ID is required");
    }

    @Test
    void markAsCurrent_WithInvalidVersionId_ShouldThrowException() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> versionHistoryService.markAsCurrent(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Version not found with id: 999");
    }

    @Test
    void markAsCurrent_WithNoExistingCurrentVersion_ShouldOnlyMarkTarget() {
        when(repository.findById(1L)).thenReturn(Optional.of(testVersion));
        when(repository.findCurrentVersionByProjectId(100L)).thenReturn(Optional.empty());
        when(repository.save(any(DocumentVersion.class))).thenReturn(testVersion);

        versionHistoryService.markAsCurrent(1L);

        assertThat(testVersion.getIsCurrent()).isTrue();
        verify(repository).save(testVersion);
    }

    @Test
    void markAsCurrent_WhenVersionIsAlreadyCurrent_ShouldNotDuplicateSave() {
        testVersion.setIsCurrent(true);
        when(repository.findById(1L)).thenReturn(Optional.of(testVersion));
        when(repository.findCurrentVersionByProjectId(100L)).thenReturn(Optional.of(testVersion));
        when(repository.save(any(DocumentVersion.class))).thenReturn(testVersion);

        versionHistoryService.markAsCurrent(1L);

        verify(repository, atLeast(1)).save(any(DocumentVersion.class));
    }
}
