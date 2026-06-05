package com.xiyu.bid.versionhistory;

import com.xiyu.bid.audit.service.AuditLogService;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.versionhistory.dto.DocumentVersionDTO;
import com.xiyu.bid.versionhistory.dto.VersionCreateRequest;
import com.xiyu.bid.versionhistory.entity.DocumentVersion;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VersionHistoryServiceCreateQueryTest extends AbstractVersionHistoryServiceTest {

    @Test
    void createVersion_WithValidData_ShouldReturnSavedVersion() {
        when(repository.getNextVersionNumber(100L)).thenReturn(1);
        when(repository.findCurrentVersionByProjectId(100L)).thenReturn(Optional.empty());
        when(repository.save(any(DocumentVersion.class))).thenReturn(testVersion);

        DocumentVersionDTO result = versionHistoryService.createVersion(createRequest);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getProjectId()).isEqualTo(100L);
        assertThat(result.getVersionNumber()).isEqualTo(1);
        assertThat(result.getContent()).isEqualTo("Initial content");
        assertThat(result.getIsCurrent()).isTrue();

        verify(repository).save(any(DocumentVersion.class));
        verify(auditLogService).log(any(AuditLogService.AuditLogEntry.class));
    }

    @Test
    void createVersion_WhenProjectOutsideCurrentUserScope_ShouldThrowAccessDeniedBeforeSaving() {
        doThrow(new AccessDeniedException("权限不足，无法访问该项目"))
                .when(projectAccessScopeService).assertCurrentUserCanAccessProject(100L);

        assertThatThrownBy(() -> versionHistoryService.createVersion(createRequest))
                .isInstanceOf(AccessDeniedException.class);

        verify(projectAccessScopeService).assertCurrentUserCanAccessProject(100L);
        verify(repository, never()).save(any(DocumentVersion.class));
    }

    @Test
    void createVersion_WithNullProjectId_ShouldThrowException() {
        VersionCreateRequest invalidRequest = VersionCreateRequest.builder()
                .projectId(null)
                .content("Content")
                .createdBy(1L)
                .build();

        assertThatThrownBy(() -> versionHistoryService.createVersion(invalidRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Project ID is required");

        verify(repository, never()).save(any(DocumentVersion.class));
    }

    @Test
    void createVersion_WithNullContent_ShouldThrowException() {
        VersionCreateRequest invalidRequest = VersionCreateRequest.builder()
                .projectId(100L)
                .content(null)
                .createdBy(1L)
                .build();

        assertThatThrownBy(() -> versionHistoryService.createVersion(invalidRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Content cannot be empty");

        verify(repository, never()).save(any(DocumentVersion.class));
    }

    @Test
    void createVersion_WithEmptyContent_ShouldThrowException() {
        VersionCreateRequest invalidRequest = VersionCreateRequest.builder()
                .projectId(100L)
                .content("   ")
                .createdBy(1L)
                .build();

        assertThatThrownBy(() -> versionHistoryService.createVersion(invalidRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Content cannot be empty");

        verify(repository, never()).save(any(DocumentVersion.class));
    }

    @Test
    void createVersion_WithNullCreatedBy_ShouldThrowException() {
        VersionCreateRequest invalidRequest = VersionCreateRequest.builder()
                .projectId(100L)
                .content("Content")
                .createdBy(null)
                .build();

        assertThatThrownBy(() -> versionHistoryService.createVersion(invalidRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Created by is required");

        verify(repository, never()).save(any(DocumentVersion.class));
    }

    @Test
    void createVersion_WithExistingCurrentVersion_ShouldMarkOldAsNotCurrent() {
        when(repository.getNextVersionNumber(100L)).thenReturn(2);
        when(repository.findCurrentVersionByProjectId(100L)).thenReturn(Optional.of(testVersion));
        when(repository.save(any(DocumentVersion.class))).thenReturn(testVersion2);

        versionHistoryService.createVersion(createRequest);

        verify(repository, org.mockito.Mockito.times(2)).save(any(DocumentVersion.class));
        verify(auditLogService).log(any(AuditLogService.AuditLogEntry.class));
    }

    @Test
    void createVersion_WithNoChangeSummary_ShouldUseDefault() {
        VersionCreateRequest requestWithoutSummary = VersionCreateRequest.builder()
                .projectId(100L)
                .content("Content")
                .createdBy(1L)
                .changeSummary(null)
                .build();

        when(repository.getNextVersionNumber(100L)).thenReturn(1);
        when(repository.findCurrentVersionByProjectId(100L)).thenReturn(Optional.empty());
        when(repository.save(any(DocumentVersion.class))).thenAnswer(invocation -> {
            DocumentVersion version = invocation.getArgument(0);
            version.setId(1L);
            return version;
        });

        DocumentVersionDTO result = versionHistoryService.createVersion(requestWithoutSummary);

        assertThat(result.getChangeSummary()).isEqualTo("Version 1");
    }

    @Test
    void createVersion_WithVeryLongContent_ShouldHandleCorrectly() {
        String longContent = "A".repeat(100000);
        VersionCreateRequest request = VersionCreateRequest.builder()
                .projectId(100L)
                .content(longContent)
                .createdBy(1L)
                .build();

        when(repository.getNextVersionNumber(100L)).thenReturn(1);
        when(repository.findCurrentVersionByProjectId(100L)).thenReturn(Optional.empty());
        when(repository.save(any(DocumentVersion.class))).thenAnswer(invocation -> {
            DocumentVersion version = invocation.getArgument(0);
            version.setId(1L);
            return version;
        });

        DocumentVersionDTO result = versionHistoryService.createVersion(request);

        assertThat(result.getContent()).hasSize(100000);
    }

    @Test
    void createVersion_WithSpecialCharactersInContent_ShouldHandleCorrectly() {
        String specialContent = "Content with <script> tags\n& special chars: \"quotes\"\nNewlines\n\tTabs";
        VersionCreateRequest request = VersionCreateRequest.builder()
                .projectId(100L)
                .content(specialContent)
                .createdBy(1L)
                .build();

        when(repository.getNextVersionNumber(100L)).thenReturn(1);
        when(repository.findCurrentVersionByProjectId(100L)).thenReturn(Optional.empty());
        when(repository.save(any(DocumentVersion.class))).thenAnswer(invocation -> {
            DocumentVersion version = invocation.getArgument(0);
            version.setId(1L);
            return version;
        });

        DocumentVersionDTO result = versionHistoryService.createVersion(request);

        assertThat(result.getContent()).isEqualTo(specialContent);
    }

    @Test
    void getVersionsByProject_WithValidProjectId_ShouldReturnVersions() {
        List<DocumentVersion> versions = Arrays.asList(testVersion2, testVersion);
        when(repository.findByProjectIdOrderByCreatedAtDesc(100L)).thenReturn(versions);

        List<DocumentVersionDTO> result = versionHistoryService.getVersionsByProject(100L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getVersionNumber()).isEqualTo(2);
        assertThat(result.get(1).getVersionNumber()).isEqualTo(1);
    }

    @Test
    void getVersionsByProject_WhenProjectOutsideCurrentUserScope_ShouldThrowAccessDeniedBeforeQuery() {
        doThrow(new AccessDeniedException("权限不足，无法访问该项目"))
                .when(projectAccessScopeService).assertCurrentUserCanAccessProject(100L);

        assertThatThrownBy(() -> versionHistoryService.getVersionsByProject(100L))
                .isInstanceOf(AccessDeniedException.class);

        verify(projectAccessScopeService).assertCurrentUserCanAccessProject(100L);
        verify(repository, never()).findByProjectIdOrderByCreatedAtDesc(any());
    }

    @Test
    void getVersionsByProject_WithNoVersions_ShouldReturnEmptyList() {
        when(repository.findByProjectIdOrderByCreatedAtDesc(100L)).thenReturn(List.of());

        List<DocumentVersionDTO> result = versionHistoryService.getVersionsByProject(100L);

        assertThat(result).isEmpty();
    }

    @Test
    void getVersionsByProject_WithNullProjectId_ShouldThrowException() {
        assertThatThrownBy(() -> versionHistoryService.getVersionsByProject(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Project ID is required");
    }

    @Test
    void getVersion_WithValidVersionId_ShouldReturnVersion() {
        when(repository.findById(1L)).thenReturn(Optional.of(testVersion));

        DocumentVersionDTO result = versionHistoryService.getVersion(1L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getProjectId()).isEqualTo(100L);
        assertThat(result.getVersionNumber()).isEqualTo(1);
    }

    @Test
    void getVersion_WithInvalidVersionId_ShouldThrowException() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> versionHistoryService.getVersion(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Version not found with id: 999");
    }

    @Test
    void getVersion_WithNullVersionId_ShouldThrowException() {
        assertThatThrownBy(() -> versionHistoryService.getVersion(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Version ID is required");
    }

    @Test
    void getLatestVersion_WithValidProjectId_ShouldReturnCurrentVersion() {
        when(repository.findCurrentVersionByProjectId(100L)).thenReturn(Optional.of(testVersion));

        DocumentVersionDTO result = versionHistoryService.getLatestVersion(100L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getIsCurrent()).isTrue();
    }

    @Test
    void getLatestVersion_WithNoVersions_ShouldThrowException() {
        when(repository.findCurrentVersionByProjectId(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> versionHistoryService.getLatestVersion(100L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("No versions found for project: 100");
    }

    @Test
    void getLatestVersion_WithNullProjectId_ShouldThrowException() {
        assertThatThrownBy(() -> versionHistoryService.getLatestVersion(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Project ID is required");
    }
}
