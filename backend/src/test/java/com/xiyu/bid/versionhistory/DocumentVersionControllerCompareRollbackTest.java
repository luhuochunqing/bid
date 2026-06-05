package com.xiyu.bid.versionhistory;

import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.versionhistory.dto.DocumentVersionDTO;
import com.xiyu.bid.versionhistory.dto.VersionDiffDTO;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DocumentVersionControllerCompareRollbackTest extends AbstractDocumentVersionControllerTest {

    @Test
    void compareVersions_WithValidVersionIds_ShouldReturn200() throws Exception {
        when(versionHistoryService.compareVersions(100L, 1L, 2L)).thenReturn(versionDiffDTO);

        mockMvc.perform(get("/api/documents/{projectId}/versions/{v1}/compare/{v2}", 100L, 1L, 2L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.version1Id").value(1))
                .andExpect(jsonPath("$.data.version2Id").value(2))
                .andExpect(jsonPath("$.data.version1Number").value(1))
                .andExpect(jsonPath("$.data.version2Number").value(2))
                .andExpect(jsonPath("$.data.differences").isArray());

        verify(versionHistoryService).compareVersions(100L, 1L, 2L);
    }

    @Test
    void compareVersions_WithSameVersionIds_ShouldReturn200() throws Exception {
        VersionDiffDTO sameDiff = VersionDiffDTO.builder()
                .version1Id(1L)
                .version2Id(1L)
                .version1Number(1)
                .version2Number(1)
                .content1("Same content")
                .content2("Same content")
                .differences(Collections.emptyList())
                .build();
        when(versionHistoryService.compareVersions(100L, 1L, 1L)).thenReturn(sameDiff);

        mockMvc.perform(get("/api/documents/{projectId}/versions/{v1}/compare/{v2}", 100L, 1L, 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.differences").isArray())
                .andExpect(jsonPath("$.data.differences.length()").value(0));

        verify(versionHistoryService).compareVersions(100L, 1L, 1L);
    }

    @Test
    void compareVersions_WithNonExistentVersion1_ShouldReturn404() throws Exception {
        when(versionHistoryService.compareVersions(100L, 999L, 2L))
                .thenThrow(new ResourceNotFoundException("Version not found with id: 999"));

        mockMvc.perform(get("/api/documents/{projectId}/versions/{v1}/compare/{v2}", 100L, 999L, 2L))
                .andExpect(status().isNotFound());

        verify(versionHistoryService).compareVersions(100L, 999L, 2L);
    }

    @Test
    void compareVersions_WithInvalidVersionId1_ShouldReturn400() throws Exception {
        mockMvc.perform(get("/api/documents/{projectId}/versions/{v1}/compare/{v2}", 100L, -1, 2L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.msg").value("Invalid version IDs"));

        verify(versionHistoryService, never()).compareVersions(any(), any(), any());
    }

    @Test
    void compareVersions_WithInvalidVersionId2_ShouldReturn400() throws Exception {
        mockMvc.perform(get("/api/documents/{projectId}/versions/{v1}/compare/{v2}", 100L, 1L, 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.msg").value("Invalid version IDs"));

        verify(versionHistoryService, never()).compareVersions(any(), any(), any());
    }

    @Test
    void compareVersions_WithReversedOrder_ShouldReturn200() throws Exception {
        VersionDiffDTO reversedDiff = VersionDiffDTO.builder()
                .version1Id(2L)
                .version2Id(1L)
                .version1Number(2)
                .version2Number(1)
                .content1("Updated content")
                .content2("Initial content")
                .differences(List.of("Content changed"))
                .build();

        when(versionHistoryService.compareVersions(100L, 2L, 1L)).thenReturn(reversedDiff);

        mockMvc.perform(get("/api/documents/{projectId}/versions/{v1}/compare/{v2}", 100L, 2L, 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.version1Id").value(2))
                .andExpect(jsonPath("$.data.version2Id").value(1));

        verify(versionHistoryService).compareVersions(100L, 2L, 1L);
    }

    @Test
    void rollbackToVersion_WithValidData_ShouldReturn200() throws Exception {
        when(versionHistoryService.rollbackToVersion(eq(100L), eq(1L), eq(1L))).thenReturn(testVersionDTO);

        mockMvc.perform(post("/api/documents/{projectId}/versions/{versionId}/rollback", 100L, 1L)
                        .param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.msg").value("Rolled back successfully"))
                .andExpect(jsonPath("$.data.id").value(1));

        verify(versionHistoryService).rollbackToVersion(eq(100L), eq(1L), eq(1L));
    }

    @Test
    void rollbackToVersion_WithNullUserId_ShouldReturn400() throws Exception {
        mockMvc.perform(post("/api/documents/{projectId}/versions/{versionId}/rollback", 100L, 1L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.msg").value("User ID is required"));

        verify(versionHistoryService, never()).rollbackToVersion(any(), any(), any());
    }

    @Test
    void rollbackToVersion_WithNonExistentVersion_ShouldReturn404() throws Exception {
        when(versionHistoryService.rollbackToVersion(eq(100L), eq(999L), eq(1L)))
                .thenThrow(new ResourceNotFoundException("Version not found with id: 999"));

        mockMvc.perform(post("/api/documents/{projectId}/versions/{versionId}/rollback", 100L, 999L)
                        .param("userId", "1"))
                .andExpect(status().isNotFound());

        verify(versionHistoryService).rollbackToVersion(eq(100L), eq(999L), eq(1L));
    }

    @Test
    void rollbackToVersion_WithDifferentUserId_ShouldReturn200() throws Exception {
        DocumentVersionDTO rolledBackVersion = DocumentVersionDTO.builder()
                .id(3L)
                .projectId(100L)
                .versionNumber(3)
                .content("Rolled back content")
                .changeSummary("Rollback to version 1")
                .createdBy(2L)
                .isCurrent(true)
                .build();

        when(versionHistoryService.rollbackToVersion(eq(100L), eq(1L), eq(2L))).thenReturn(rolledBackVersion);

        mockMvc.perform(post("/api/documents/{projectId}/versions/{versionId}/rollback", 100L, 1L)
                        .param("userId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.createdBy").value(2))
                .andExpect(jsonPath("$.data.changeSummary").value("Rollback to version 1"));

        verify(versionHistoryService).rollbackToVersion(eq(100L), eq(1L), eq(2L));
    }
}
