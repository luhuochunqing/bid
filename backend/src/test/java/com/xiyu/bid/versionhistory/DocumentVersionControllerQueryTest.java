package com.xiyu.bid.versionhistory;

import com.xiyu.bid.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DocumentVersionControllerQueryTest extends AbstractDocumentVersionControllerTest {

    @Test
    void getVersionsByProject_WithValidProjectId_ShouldReturn200() throws Exception {
        when(versionHistoryService.getVersionsByProject(100L)).thenReturn(List.of(testVersionDTO2, testVersionDTO));

        mockMvc.perform(get("/api/documents/{projectId}/versions", 100L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(2))
                .andExpect(jsonPath("$.data[1].id").value(1));

        verify(versionHistoryService).getVersionsByProject(100L);
    }

    @Test
    void getVersionsByProject_WithEmptyVersions_ShouldReturn200WithEmptyArray() throws Exception {
        when(versionHistoryService.getVersionsByProject(100L)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/documents/{projectId}/versions", 100L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));

        verify(versionHistoryService).getVersionsByProject(100L);
    }

    @Test
    void getVersionsByProject_WithInvalidProjectId_ShouldReturn400() throws Exception {
        mockMvc.perform(get("/api/documents/{projectId}/versions", -1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.msg").value("Invalid project ID"));

        verify(versionHistoryService, never()).getVersionsByProject(any());
    }

    @Test
    void getVersionsByProject_WithZeroProjectId_ShouldReturn400() throws Exception {
        mockMvc.perform(get("/api/documents/{projectId}/versions", 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.msg").value("Invalid project ID"));

        verify(versionHistoryService, never()).getVersionsByProject(any());
    }

    @Test
    void getVersionsByProject_WithVeryLargeProjectId_ShouldReturn200() throws Exception {
        Long largeProjectId = Long.MAX_VALUE;
        when(versionHistoryService.getVersionsByProject(largeProjectId)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/documents/{projectId}/versions", largeProjectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());

        verify(versionHistoryService).getVersionsByProject(largeProjectId);
    }

    @Test
    void getLatestVersion_WithValidProjectId_ShouldReturn200() throws Exception {
        when(versionHistoryService.getLatestVersion(100L)).thenReturn(testVersionDTO);

        mockMvc.perform(get("/api/documents/{projectId}/versions/latest", 100L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.projectId").value(100))
                .andExpect(jsonPath("$.data.isCurrent").value(true));

        verify(versionHistoryService).getLatestVersion(100L);
    }

    @Test
    void getLatestVersion_WithNonExistentProject_ShouldReturn404() throws Exception {
        when(versionHistoryService.getLatestVersion(999L))
                .thenThrow(new ResourceNotFoundException("No versions found for project: 999"));

        mockMvc.perform(get("/api/documents/{projectId}/versions/latest", 999L))
                .andExpect(status().isNotFound());

        verify(versionHistoryService).getLatestVersion(999L);
    }

    @Test
    void getLatestVersion_WithInvalidProjectId_ShouldReturn400() throws Exception {
        mockMvc.perform(get("/api/documents/{projectId}/versions/latest", -1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.msg").value("Invalid project ID"));

        verify(versionHistoryService, never()).getLatestVersion(any());
    }

    @Test
    void getVersion_WithValidIds_ShouldReturn200() throws Exception {
        when(versionHistoryService.getVersion(100L, 1L)).thenReturn(testVersionDTO);

        mockMvc.perform(get("/api/documents/{projectId}/versions/{versionId}", 100L, 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.versionNumber").value(1));

        verify(versionHistoryService).getVersion(100L, 1L);
    }

    @Test
    void getVersion_WithNonExistentVersion_ShouldReturn404() throws Exception {
        when(versionHistoryService.getVersion(100L, 999L))
                .thenThrow(new ResourceNotFoundException("Version not found with id: 999"));

        mockMvc.perform(get("/api/documents/{projectId}/versions/{versionId}", 100L, 999L))
                .andExpect(status().isNotFound());

        verify(versionHistoryService).getVersion(100L, 999L);
    }

    @Test
    void getVersion_WithInvalidVersionId_ShouldReturn400() throws Exception {
        mockMvc.perform(get("/api/documents/{projectId}/versions/{versionId}", 100L, -1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.msg").value("Invalid version ID"));

        verify(versionHistoryService, never()).getVersion(any());
    }
}
