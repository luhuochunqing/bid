package com.xiyu.bid.projecttenderbreakdown.controller;

import com.xiyu.bid.biddraftagent.application.BidTenderDocumentImportAppService;
import com.xiyu.bid.biddraftagent.application.BidUploadedTenderDocumentReuseAppService;
import com.xiyu.bid.biddraftagent.application.TenderBreakdownReadiness;
import com.xiyu.bid.biddraftagent.domain.TenderRequirementProfile;
import com.xiyu.bid.biddraftagent.dto.BidTenderDocumentDTO;
import com.xiyu.bid.biddraftagent.dto.BidTenderDocumentParseDTO;
import com.xiyu.bid.apikey.infrastructure.ApiKeyAuthenticationFilter;
import com.xiyu.bid.auth.JwtAuthenticationFilter;
import com.xiyu.bid.config.RateLimitFilter;
import com.xiyu.bid.config.SecurityConfig;
import com.xiyu.bid.projecttenderbreakdown.application.ProjectTenderBreakdownReadinessService;
import com.xiyu.bid.service.ProjectAccessScopeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ProjectTenderBreakdownController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthenticationFilter.class, RateLimitFilter.class,
                        ApiKeyAuthenticationFilter.class}
        ))
@AutoConfigureMockMvc(addFilters = false)
class ProjectTenderBreakdownControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BidTenderDocumentImportAppService importAppService;

    @MockBean
    private BidUploadedTenderDocumentReuseAppService uploadedReuseAppService;

    @MockBean
    private ProjectAccessScopeService projectAccessScopeService;

    @MockBean
    private ProjectTenderBreakdownReadinessService readinessService;

    @Test
    @WithMockUser(roles = "STAFF")
    void parseTenderBreakdown_shouldPersistProjectRequirementSnapshotWithoutStartingDraftRun() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "招标文件.docx",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                "招标正文".getBytes()
        );
        BidTenderDocumentParseDTO result = BidTenderDocumentParseDTO.builder()
                .message("招标文件已解析，已更新招标要求快照")
                .document(BidTenderDocumentDTO.builder()
                        .id(501L)
                        .projectId(12L)
                        .tenderId(22L)
                        .name("招标文件.docx")
                        .snapshotId(601L)
                        .extractedTextLength(128)
                        .build())
                .requirementProfile(new TenderRequirementProfile(
                        "项目名称",
                        null,
                        null,
                        null,
                        List.of("资格要求"),
                        List.of("技术要求"),
                        List.of("商务要求"),
                        List.of("评分标准"),
                        null,
                        List.of("投标材料"),
                        List.of("风险提示"),
                        List.of(),
                        List.of()
                ))
                .build();
        when(importAppService.parseTenderDocument(eq(12L), any())).thenReturn(result);

        mockMvc.perform(multipart("/api/projects/{projectId}/tender-breakdown", 12L)
                        .file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.document.snapshotId").value(601))
                .andExpect(jsonPath("$.data.requirementProfile.technicalRequirements[0]").value("技术要求"));

        verify(projectAccessScopeService).assertCurrentUserCanAccessProject(12L);
        verify(importAppService).parseTenderDocument(eq(12L), any());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void getReadiness_missingDeepSeekKey_shouldReturnActionableGuidance() throws Exception {
        when(readinessService.readiness(12L)).thenReturn(TenderBreakdownReadiness.missingDeepSeekKey());

        mockMvc.perform(get("/api/projects/{projectId}/tender-breakdown/readiness", 12L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.ready").value(false))
                .andExpect(jsonPath("$.data.providerName").value("DeepSeek"))
                .andExpect(jsonPath("$.data.envKey").value("DEEPSEEK_API_KEY"))
                .andExpect(jsonPath("$.data.settingsPath").value("/settings"))
                .andExpect(jsonPath("$.data.message").value("DeepSeek API Key 未配置。请管理员到系统设置 → AI 模型配置中填写 DeepSeek provider key，或在服务端设置 DEEPSEEK_API_KEY 后重启。"));

        verify(projectAccessScopeService).assertCurrentUserCanAccessProject(12L);
        verify(readinessService).readiness(12L);
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void getLatestParsedTenderBreakdown_shouldReturnReusableSnapshotWithoutUpload() throws Exception {
        BidTenderDocumentParseDTO result = BidTenderDocumentParseDTO.builder()
                .message("已复用已解析的招标文件")
                .document(BidTenderDocumentDTO.builder()
                        .id(501L)
                        .projectId(12L)
                        .tenderId(22L)
                        .name("招标文件.docx")
                        .snapshotId(601L)
                        .extractedTextLength(128)
                        .build())
                .requirementProfile(new TenderRequirementProfile(
                        "项目名称",
                        null,
                        null,
                        null,
                        List.of("资格要求"),
                        List.of("技术要求"),
                        List.of("商务要求"),
                        List.of("评分标准"),
                        null,
                        List.of("投标材料"),
                        List.of("风险提示"),
                        List.of(),
                        List.of()
                ))
                .build();
        when(importAppService.latestParsedTenderDocument(12L)).thenReturn(Optional.of(result));

        mockMvc.perform(get("/api/projects/{projectId}/tender-breakdown/latest", 12L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.document.snapshotId").value(601))
                .andExpect(jsonPath("$.data.document.name").value("招标文件.docx"));

        verify(projectAccessScopeService).assertCurrentUserCanAccessProject(12L);
        verify(importAppService).latestParsedTenderDocument(12L);
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void getLatestParsedTenderBreakdown_withoutSnapshot_shouldReturnEmptySuccess() throws Exception {
        when(importAppService.latestParsedTenderDocument(12L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/projects/{projectId}/tender-breakdown/latest", 12L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.msg").value("尚未解析招标文件"));

        verify(projectAccessScopeService).assertCurrentUserCanAccessProject(12L);
        verify(importAppService).latestParsedTenderDocument(12L);
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void reuseUploadedTenderBreakdown_shouldParseExistingProjectDocumentWithoutMultipartUpload() throws Exception {
        BidTenderDocumentParseDTO result = BidTenderDocumentParseDTO.builder()
                .message("已复用项目已上传的招标文件")
                .document(BidTenderDocumentDTO.builder()
                        .id(501L)
                        .projectId(12L)
                        .tenderId(22L)
                        .name("已上传招标文件.docx")
                        .snapshotId(701L)
                        .extractedTextLength(128)
                        .build())
                .requirementProfile(new TenderRequirementProfile(
                        "项目名称",
                        null,
                        null,
                        null,
                        List.of("资格要求"),
                        List.of("技术要求"),
                        List.of("商务要求"),
                        List.of("评分标准"),
                        null,
                        List.of("投标材料"),
                        List.of("风险提示"),
                        List.of(),
                        List.of()
                ))
                .build();
        when(uploadedReuseAppService.parseLatestUploadedTenderDocument(12L)).thenReturn(Optional.of(result));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/projects/{projectId}/tender-breakdown/reuse-uploaded", 12L))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.document.snapshotId").value(701))
                .andExpect(jsonPath("$.data.document.name").value("已上传招标文件.docx"));

        verify(projectAccessScopeService).assertCurrentUserCanAccessProject(12L);
        verify(uploadedReuseAppService).parseLatestUploadedTenderDocument(12L);
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void getReadiness_whenProjectOutsideScope_shouldReturnForbidden() throws Exception {
        org.mockito.Mockito.doThrow(new org.springframework.security.access.AccessDeniedException("无权访问"))
                .when(projectAccessScopeService).assertCurrentUserCanAccessProject(99L);

        mockMvc.perform(get("/api/projects/{projectId}/tender-breakdown/readiness", 99L))
                .andExpect(status().isForbidden());

        org.mockito.Mockito.verifyNoInteractions(readinessService);
    }
}
