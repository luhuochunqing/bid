package com.xiyu.bid.versionhistory;

import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.versionhistory.dto.VersionDiffDTO;
import com.xiyu.bid.versionhistory.entity.DocumentVersion;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

class VersionHistoryServiceCompareTest extends AbstractVersionHistoryServiceTest {

    @Test
    void compareVersions_WithValidVersionIds_ShouldReturnDifferences() {
        when(repository.findById(1L)).thenReturn(Optional.of(testVersion));
        when(repository.findById(2L)).thenReturn(Optional.of(testVersion2));

        VersionDiffDTO result = versionHistoryService.compareVersions(100L, 1L, 2L);

        assertThat(result).isNotNull();
        assertThat(result.getVersion1Id()).isEqualTo(1L);
        assertThat(result.getVersion2Id()).isEqualTo(2L);
        assertThat(result.getVersion1Number()).isEqualTo(1);
        assertThat(result.getVersion2Number()).isEqualTo(2);
        assertThat(result.getContent1()).isEqualTo("Initial content");
        assertThat(result.getContent2()).isEqualTo("Updated content");
        assertThat(result.getDifferences()).isNotEmpty();
    }

    @Test
    void compareVersions_WithSameContent_ShouldReturnEmptyDifferences() {
        DocumentVersion version1 = DocumentVersion.builder()
                .id(1L)
                .projectId(100L)
                .versionNumber(1)
                .content("Same content")
                .build();
        DocumentVersion version2 = DocumentVersion.builder()
                .id(2L)
                .projectId(100L)
                .versionNumber(2)
                .content("Same content")
                .build();

        when(repository.findById(1L)).thenReturn(Optional.of(version1));
        when(repository.findById(2L)).thenReturn(Optional.of(version2));

        VersionDiffDTO result = versionHistoryService.compareVersions(100L, 1L, 2L);

        assertThat(result.getDifferences()).isEmpty();
    }

    @Test
    void compareVersions_WithNullFirstVersionId_ShouldThrowException() {
        assertThatThrownBy(() -> versionHistoryService.compareVersions(100L, null, 2L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Both version IDs are required");
    }

    @Test
    void compareVersions_WithNullSecondVersionId_ShouldThrowException() {
        assertThatThrownBy(() -> versionHistoryService.compareVersions(100L, 1L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Both version IDs are required");
    }

    @Test
    void compareVersions_WithInvalidFirstVersionId_ShouldThrowException() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> versionHistoryService.compareVersions(100L, 999L, 2L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Version not found with id: 999");
    }

    @Test
    void compareVersions_WithInvalidSecondVersionId_ShouldThrowException() {
        when(repository.findById(1L)).thenReturn(Optional.of(testVersion));
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> versionHistoryService.compareVersions(100L, 1L, 999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Version not found with id: 999");
    }

    @Test
    void compareVersions_WithNullContentInVersion1_ShouldHandleCorrectly() {
        DocumentVersion version1 = DocumentVersion.builder()
                .id(1L)
                .projectId(100L)
                .versionNumber(1)
                .content(null)
                .build();
        when(repository.findById(1L)).thenReturn(Optional.of(version1));
        when(repository.findById(2L)).thenReturn(Optional.of(testVersion2));

        VersionDiffDTO result = versionHistoryService.compareVersions(100L, 1L, 2L);

        assertThat(result.getDifferences()).isNotEmpty();
        assertThat(result.getDifferences().get(0)).contains("Content added");
    }

    @Test
    void compareVersions_WithNullContentInVersion2_ShouldHandleCorrectly() {
        DocumentVersion version2 = DocumentVersion.builder()
                .id(2L)
                .projectId(100L)
                .versionNumber(2)
                .content(null)
                .build();
        when(repository.findById(1L)).thenReturn(Optional.of(testVersion));
        when(repository.findById(2L)).thenReturn(Optional.of(version2));

        VersionDiffDTO result = versionHistoryService.compareVersions(100L, 1L, 2L);

        assertThat(result.getDifferences()).isNotEmpty();
        assertThat(result.getDifferences().get(0)).contains("Content removed");
    }

    @Test
    void compareVersions_WithMultilineContent_ShouldDetectLineChanges() {
        DocumentVersion version1 = DocumentVersion.builder()
                .id(1L)
                .projectId(100L)
                .versionNumber(1)
                .content("Line 1\nLine 2\nLine 3")
                .build();
        DocumentVersion version2 = DocumentVersion.builder()
                .id(2L)
                .projectId(100L)
                .versionNumber(2)
                .content("Line 1\nModified Line 2\nLine 3")
                .build();

        when(repository.findById(1L)).thenReturn(Optional.of(version1));
        when(repository.findById(2L)).thenReturn(Optional.of(version2));

        VersionDiffDTO result = versionHistoryService.compareVersions(100L, 1L, 2L);

        assertThat(result.getDifferences()).hasSize(1);
        assertThat(result.getDifferences().get(0)).contains("Line 2");
    }

    @Test
    void compareVersions_WithEmptyStrings_ShouldReturnEmptyDifferences() {
        DocumentVersion version1 = DocumentVersion.builder()
                .id(1L)
                .projectId(100L)
                .versionNumber(1)
                .content("")
                .build();
        DocumentVersion version2 = DocumentVersion.builder()
                .id(2L)
                .projectId(100L)
                .versionNumber(2)
                .content("")
                .build();

        when(repository.findById(1L)).thenReturn(Optional.of(version1));
        when(repository.findById(2L)).thenReturn(Optional.of(version2));

        VersionDiffDTO result = versionHistoryService.compareVersions(100L, 1L, 2L);

        assertThat(result.getDifferences()).isEmpty();
    }
}
