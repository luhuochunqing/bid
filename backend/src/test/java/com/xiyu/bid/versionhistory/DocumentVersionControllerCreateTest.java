package com.xiyu.bid.versionhistory;

import com.xiyu.bid.versionhistory.dto.DocumentVersionDTO;
import com.xiyu.bid.versionhistory.dto.VersionCreateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DocumentVersionControllerCreateTest extends AbstractDocumentVersionControllerTest {

    @Test
    void createVersion_WithValidData_ShouldReturn201() throws Exception {
        when(versionHistoryService.createVersion(any(VersionCreateRequest.class))).thenReturn(testVersionDTO);

        mockMvc.perform(post("/api/documents/{projectId}/versions", 100L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.msg").value("Version created successfully"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.projectId").value(100));

        verify(versionHistoryService).createVersion(any(VersionCreateRequest.class));
    }

    @Test
    void createVersion_WithMismatchedProjectId_ShouldReturn400() throws Exception {
        VersionCreateRequest mismatchedRequest = VersionCreateRequest.builder()
                .projectId(200L)
                .documentId("doc-001")
                .content("Content")
                .createdBy(1L)
                .build();

        mockMvc.perform(post("/api/documents/{projectId}/versions", 100L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mismatchedRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.msg").value("Project ID in path does not match request body"));

        verify(versionHistoryService, never()).createVersion(any());
    }

    @Test
    void createVersion_WithNullContent_ShouldReturn400() throws Exception {
        VersionCreateRequest invalidRequest = VersionCreateRequest.builder()
                .projectId(100L)
                .documentId("doc-001")
                .content(null)
                .createdBy(1L)
                .build();

        mockMvc.perform(post("/api/documents/{projectId}/versions", 100L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verify(versionHistoryService, never()).createVersion(any());
    }

    @Test
    void createVersion_WithEmptyContent_ShouldReturn400() throws Exception {
        VersionCreateRequest invalidRequest = VersionCreateRequest.builder()
                .projectId(100L)
                .documentId("doc-001")
                .content("")
                .createdBy(1L)
                .build();

        mockMvc.perform(post("/api/documents/{projectId}/versions", 100L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verify(versionHistoryService, never()).createVersion(any());
    }

    @Test
    void createVersion_WithNullCreatedBy_ShouldReturn400() throws Exception {
        VersionCreateRequest invalidRequest = VersionCreateRequest.builder()
                .projectId(100L)
                .documentId("doc-001")
                .content("Content")
                .createdBy(null)
                .build();

        mockMvc.perform(post("/api/documents/{projectId}/versions", 100L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verify(versionHistoryService, never()).createVersion(any());
    }

    @Test
    void createVersion_WithLargeContent_ShouldReturn201() throws Exception {
        String largeContent = "A".repeat(10000);
        VersionCreateRequest request = VersionCreateRequest.builder()
                .projectId(100L)
                .documentId("doc-001")
                .content(largeContent)
                .createdBy(1L)
                .build();

        DocumentVersionDTO largeVersionDTO = DocumentVersionDTO.builder()
                .id(1L)
                .projectId(100L)
                .content(largeContent)
                .versionNumber(1)
                .build();

        when(versionHistoryService.createVersion(any(VersionCreateRequest.class))).thenReturn(largeVersionDTO);

        mockMvc.perform(post("/api/documents/{projectId}/versions", 100L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").value(largeContent));

        verify(versionHistoryService).createVersion(any(VersionCreateRequest.class));
    }

    @Test
    void createVersion_WithSpecialCharactersInContent_ShouldReturn201() throws Exception {
        String specialContent = "Content with <script>alert('xss')</script>\n& special \"quotes\"";
        VersionCreateRequest request = VersionCreateRequest.builder()
                .projectId(100L)
                .documentId("doc-001")
                .content(specialContent)
                .createdBy(1L)
                .build();

        DocumentVersionDTO specialVersionDTO = DocumentVersionDTO.builder()
                .id(1L)
                .projectId(100L)
                .content(specialContent)
                .versionNumber(1)
                .build();

        when(versionHistoryService.createVersion(any(VersionCreateRequest.class))).thenReturn(specialVersionDTO);

        mockMvc.perform(post("/api/documents/{projectId}/versions", 100L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").value(specialContent));

        verify(versionHistoryService).createVersion(any(VersionCreateRequest.class));
    }

    @Test
    void createVersion_WithNullDocumentId_ShouldReturn201() throws Exception {
        VersionCreateRequest requestWithoutDocId = VersionCreateRequest.builder()
                .projectId(100L)
                .documentId(null)
                .content("Content")
                .createdBy(1L)
                .build();

        when(versionHistoryService.createVersion(any(VersionCreateRequest.class))).thenReturn(testVersionDTO);

        mockMvc.perform(post("/api/documents/{projectId}/versions", 100L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestWithoutDocId)))
                .andExpect(status().isCreated());

        verify(versionHistoryService).createVersion(any(VersionCreateRequest.class));
    }
}
