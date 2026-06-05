package com.xiyu.bid.projectquality.controller;

import com.xiyu.bid.exception.GlobalExceptionHandler;
import com.xiyu.bid.projectquality.dto.ProjectQualityCheckResponse;
import com.xiyu.bid.projectquality.dto.ProjectQualityIssueResponse;
import com.xiyu.bid.projectquality.service.ProjectQualityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProjectQualityControllerTest {

    @Mock
    private ProjectQualityService projectQualityService;

    @InjectMocks
    private ProjectQualityController projectQualityController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(projectQualityController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getLatestShouldReturnNullWhenNoQualityCheckExists() throws Exception {
        when(projectQualityService.getLatest(12L)).thenReturn(null);

        mockMvc.perform(get("/api/projects/{projectId}/quality-checks/latest", 12L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(nullValue()));

        verify(projectQualityService).getLatest(12L);
    }

    @Test
    void runQualityCheckShouldReturnTheServicePayload() throws Exception {
        when(projectQualityService.runQualityCheck(12L)).thenReturn(buildResponse(81L, "COMPLETED", false));

        mockMvc.perform(post("/api/projects/{projectId}/quality-checks", 12L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(81))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        verify(projectQualityService).runQualityCheck(12L);
    }

    @Test
    void adoptIssueShouldReturnUpdatedPayload() throws Exception {
        when(projectQualityService.adoptIssue(eq(12L), eq(81L), eq(9001L)))
                .thenReturn(buildResponse(81L, "COMPLETED", false));

        mockMvc.perform(post("/api/projects/{projectId}/quality-checks/{checkId}/issues/{issueId}/adopt",
                        12L, 81L, 9001L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(81))
                .andExpect(jsonPath("$.data.issues[0].id").value(9001));

        verify(projectQualityService).adoptIssue(12L, 81L, 9001L);
    }

    @Test
    void ignoreIssueShouldReturnUpdatedPayload() throws Exception {
        when(projectQualityService.ignoreIssue(eq(12L), eq(81L), eq(9002L)))
                .thenReturn(buildResponse(81L, "COMPLETED", false));

        mockMvc.perform(post("/api/projects/{projectId}/quality-checks/{checkId}/issues/{issueId}/ignore",
                        12L, 81L, 9002L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(81));

        verify(projectQualityService).ignoreIssue(12L, 81L, 9002L);
    }

    private ProjectQualityCheckResponse buildResponse(Long id, String status, boolean empty) {
        return ProjectQualityCheckResponse.builder()
                .id(id)
                .projectId(12L)
                .documentId(55L)
                .documentName("投标文书初稿.docx")
                .status(status)
                .empty(empty)
                .summary("已完成质量检查")
                .checkedAt(LocalDateTime.of(2026, 4, 21, 12, 0))
                .issues(List.of(
                        ProjectQualityIssueResponse.builder()
                                .id(9001L)
                                .type("grammar")
                                .originalText("原文")
                                .suggestionText("建议")
                                .locationLabel("摘要")
                                .adopted(false)
                                .ignored(false)
                                .build()
                ))
                .build();
    }
}
