package com.xiyu.bid.biddraftagent.controller;

import com.xiyu.bid.biddraftagent.application.BidDraftAgentAppService;
import com.xiyu.bid.biddraftagent.application.BidTenderDocumentImportAppService;
import com.xiyu.bid.biddraftagent.application.CommercialClassificationAppService;
import com.xiyu.bid.biddraftagent.application.FullAnalysisAppService;
import com.xiyu.bid.biddraftagent.application.KnowledgeBaseMatchAppService;
import com.xiyu.bid.biddraftagent.application.QualificationMatchAppService;
import com.xiyu.bid.biddraftagent.application.RiskClassificationAppService;
import com.xiyu.bid.biddraftagent.application.ScoringCriteriaClassificationAppService;
import com.xiyu.bid.biddraftagent.application.TechnicalClassificationAppService;
import com.xiyu.bid.biddraftagent.dto.BidDraftAgentApplyResponseDTO;
import com.xiyu.bid.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static com.xiyu.bid.biddraftagent.controller.BidDraftAgentControllerFixtures.sampleCreateRunRequest;
import static com.xiyu.bid.biddraftagent.controller.BidDraftAgentControllerFixtures.sampleParseDto;
import static com.xiyu.bid.biddraftagent.controller.BidDraftAgentControllerFixtures.sampleReviewDto;
import static com.xiyu.bid.biddraftagent.controller.BidDraftAgentControllerFixtures.sampleRunDto;
import static com.xiyu.bid.biddraftagent.controller.BidDraftAgentControllerFixtures.emptyQualificationMatchResult;
import static com.xiyu.bid.biddraftagent.controller.BidDraftAgentControllerFixtures.emptyTechnicalResult;
import static com.xiyu.bid.biddraftagent.controller.BidDraftAgentControllerFixtures.emptyCommercialResult;
import static com.xiyu.bid.biddraftagent.controller.BidDraftAgentControllerFixtures.emptyRiskResult;
import static com.xiyu.bid.biddraftagent.controller.BidDraftAgentControllerFixtures.emptyScoringResult;
import static com.xiyu.bid.biddraftagent.controller.BidDraftAgentControllerFixtures.emptyKnowledgeBaseMatchResult;
import static com.xiyu.bid.biddraftagent.controller.BidDraftAgentControllerFixtures.emptyFullAnalysisResult;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BidDraftAgentControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Mock
    private BidDraftAgentAppService appService;

    @Mock
    private BidTenderDocumentImportAppService importAppService;

    @Mock
    private QualificationMatchAppService qualificationMatchAppService;

    @Mock
    private TechnicalClassificationAppService technicalClassificationAppService;

    @Mock
    private CommercialClassificationAppService commercialClassificationAppService;

    @Mock
    private RiskClassificationAppService riskClassificationAppService;

    @Mock
    private ScoringCriteriaClassificationAppService scoringCriteriaClassificationAppService;

    @Mock
    private KnowledgeBaseMatchAppService knowledgeBaseMatchAppService;

    @Mock
    private FullAnalysisAppService fullAnalysisAppService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BidDraftAgentController(
                        appService, importAppService, qualificationMatchAppService,
                        technicalClassificationAppService, commercialClassificationAppService,
                        riskClassificationAppService, scoringCriteriaClassificationAppService,
                        knowledgeBaseMatchAppService, fullAnalysisAppService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void importTenderDocument_shouldUploadAndParseTenderRequirements() throws Exception {
        when(importAppService.parseTenderDocument(eq(11L), any())).thenReturn(sampleParseDto());

        mockMvc.perform(multipart("/api/projects/{projectId}/bid-agent/tender-documents", 11L)
                        .file("file", "招标范围和评分标准".getBytes())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.msg").value("招标文件已解析，已更新招标要求快照"))
                .andExpect(jsonPath("$.data.document.name").value("file"))
                .andExpect(jsonPath("$.data.document.snapshotId").value(601))
                .andExpect(jsonPath("$.data.requirementProfile.technicalRequirements[0]").value("提供实施方案"))
                .andExpect(jsonPath("$.data.message").value("招标文件已解析，已更新招标要求快照"));

        verify(importAppService).parseTenderDocument(eq(11L), any());
    }

    @Test
    void importTenderDocument_shouldReturnForbiddenWhenProjectAccessDenied() throws Exception {
        when(importAppService.parseTenderDocument(eq(12L), any()))
                .thenThrow(new AccessDeniedException("权限不足"));

        mockMvc.perform(multipart("/api/projects/{projectId}/bid-agent/tender-documents", 12L)
                        .file("file", "招标范围和评分标准".getBytes())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(403));

        verify(importAppService).parseTenderDocument(eq(12L), any());
    }

    @Test
    void createRun_shouldReturnCreatedRunPayload() throws Exception {
        when(appService.createRun(11L, 601L)).thenReturn(sampleRunDto());

        mockMvc.perform(post("/api/projects/{projectId}/bid-agent/runs", 11L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(sampleCreateRunRequest()))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.projectName").value("华东智慧园区改造项目"))
                .andExpect(jsonPath("$.data.artifacts[0].artifactType").value("DRAFT_TEXT"));

        verify(appService).createRun(11L, 601L);
    }

    @Test
    void createRun_shouldReturnForbiddenWhenProjectAccessDenied() throws Exception {
        when(appService.createRun(12L, null)).thenThrow(new AccessDeniedException("权限不足"));

        mockMvc.perform(post("/api/projects/{projectId}/bid-agent/runs", 12L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(403));

        verify(appService).createRun(12L, null);
    }

    @Test
    void getRun_shouldReturnSavedRun() throws Exception {
        when(appService.getRun(11L, 100L)).thenReturn(sampleRunDto());

        mockMvc.perform(get("/api/projects/{projectId}/bid-agent/runs/{runId}", 11L, 100L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(100));

        verify(appService).getRun(11L, 100L);
    }

    @Test
    void getRun_shouldReturnForbiddenWhenProjectAccessDenied() throws Exception {
        when(appService.getRun(12L, 100L)).thenThrow(new AccessDeniedException("权限不足"));

        mockMvc.perform(get("/api/projects/{projectId}/bid-agent/runs/{runId}", 12L, 100L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(403));

        verify(appService).getRun(12L, 100L);
    }

    @Test
    void reviewCurrentDraft_shouldReturnReviewSummary() throws Exception {
        when(appService.reviewCurrentDraft(11L)).thenReturn(sampleReviewDto());

        mockMvc.perform(post("/api/projects/{projectId}/bid-agent/reviews", 11L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewSummary").value("updated review"))
                .andExpect(jsonPath("$.data.nextActions[0]").value("项目概况"));

        verify(appService).reviewCurrentDraft(11L);
    }

    @Test
    void reviewCurrentDraft_shouldReturnForbiddenWhenProjectAccessDenied() throws Exception {
        when(appService.reviewCurrentDraft(12L)).thenThrow(new AccessDeniedException("权限不足"));

        mockMvc.perform(post("/api/projects/{projectId}/bid-agent/reviews", 12L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(403));

        verify(appService).reviewCurrentDraft(12L);
    }

    @Test
    void reviewRun_shouldReviewTheRequestedRun() throws Exception {
        when(appService.reviewRun(11L, 100L)).thenReturn(sampleReviewDto());

        mockMvc.perform(post("/api/projects/{projectId}/bid-agent/runs/{runId}/reviews", 11L, 100L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewSummary").value("updated review"));

        verify(appService).reviewRun(11L, 100L);
    }

    @Test
    void reviewRun_shouldReturnForbiddenWhenProjectAccessDenied() throws Exception {
        when(appService.reviewRun(12L, 100L)).thenThrow(new AccessDeniedException("权限不足"));

        mockMvc.perform(post("/api/projects/{projectId}/bid-agent/runs/{runId}/reviews", 12L, 100L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(403));

        verify(appService).reviewRun(12L, 100L);
    }

    @Test
    void applyRun_shouldMarkArtifactReadyForWriter() throws Exception {
        when(appService.applyRun(11L, 100L)).thenReturn(BidDraftAgentApplyResponseDTO.builder()
                .runId(100L)
                .artifactId(200L)
                .artifactType("DRAFT_TEXT")
                .status("READY_FOR_WRITER")
                .readyForWriter(true)
                .handoffTarget("document-writer")
                .message("草稿产物已标记为文档写手可用")
                .build());

        mockMvc.perform(post("/api/projects/{projectId}/bid-agent/runs/{runId}/apply", 11L, 100L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readyForWriter").value(true))
                .andExpect(jsonPath("$.data.status").value("READY_FOR_WRITER"));

        verify(appService).applyRun(11L, 100L);
    }

    @Test
    void applyRun_shouldReturnForbiddenWhenProjectAccessDenied() throws Exception {
        when(appService.applyRun(12L, 100L)).thenThrow(new AccessDeniedException("权限不足"));

        mockMvc.perform(post("/api/projects/{projectId}/bid-agent/runs/{runId}/apply", 12L, 100L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(403));

        verify(appService).applyRun(12L, 100L);
    }

    // ── 分析端点测试（7 端点 × 2 场景） ──────────────────────────────────────

    @Test
    void getQualificationMatch_shouldReturnMatchResult() throws Exception {
        when(qualificationMatchAppService.matchForProject(11L)).thenReturn(emptyQualificationMatchResult());

        mockMvc.perform(get("/api/projects/{projectId}/bid-agent/qualification-match", 11L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.msg").value("资质匹配完成"));

        verify(qualificationMatchAppService).matchForProject(11L);
    }

    @Test
    void getQualificationMatch_shouldReturnForbiddenWhenProjectAccessDenied() throws Exception {
        when(qualificationMatchAppService.matchForProject(12L))
                .thenThrow(new AccessDeniedException("权限不足"));

        mockMvc.perform(get("/api/projects/{projectId}/bid-agent/qualification-match", 12L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(403));

        verify(qualificationMatchAppService).matchForProject(12L);
    }

    @Test
    void getTechnicalRequirements_shouldReturnClassificationResult() throws Exception {
        when(technicalClassificationAppService.classifyForProject(11L)).thenReturn(emptyTechnicalResult());

        mockMvc.perform(get("/api/projects/{projectId}/bid-agent/technical-requirements", 11L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.msg").value("技术要点分类完成"));

        verify(technicalClassificationAppService).classifyForProject(11L);
    }

    @Test
    void getTechnicalRequirements_shouldReturnForbiddenWhenProjectAccessDenied() throws Exception {
        when(technicalClassificationAppService.classifyForProject(12L))
                .thenThrow(new AccessDeniedException("权限不足"));

        mockMvc.perform(get("/api/projects/{projectId}/bid-agent/technical-requirements", 12L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(403));

        verify(technicalClassificationAppService).classifyForProject(12L);
    }

    @Test
    void getCommercialRequirements_shouldReturnClassificationResult() throws Exception {
        when(commercialClassificationAppService.classifyForProject(11L)).thenReturn(emptyCommercialResult());

        mockMvc.perform(get("/api/projects/{projectId}/bid-agent/commercial-requirements", 11L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.msg").value("商务条款分类完成"));

        verify(commercialClassificationAppService).classifyForProject(11L);
    }

    @Test
    void getCommercialRequirements_shouldReturnForbiddenWhenProjectAccessDenied() throws Exception {
        when(commercialClassificationAppService.classifyForProject(12L))
                .thenThrow(new AccessDeniedException("权限不足"));

        mockMvc.perform(get("/api/projects/{projectId}/bid-agent/commercial-requirements", 12L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(403));

        verify(commercialClassificationAppService).classifyForProject(12L);
    }

    @Test
    void getRiskClassification_shouldReturnClassificationResult() throws Exception {
        when(riskClassificationAppService.classifyForProject(11L)).thenReturn(emptyRiskResult());

        mockMvc.perform(get("/api/projects/{projectId}/bid-agent/risk-classification", 11L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.msg").value("风险分类完成"));

        verify(riskClassificationAppService).classifyForProject(11L);
    }

    @Test
    void getRiskClassification_shouldReturnForbiddenWhenProjectAccessDenied() throws Exception {
        when(riskClassificationAppService.classifyForProject(12L))
                .thenThrow(new AccessDeniedException("权限不足"));

        mockMvc.perform(get("/api/projects/{projectId}/bid-agent/risk-classification", 12L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(403));

        verify(riskClassificationAppService).classifyForProject(12L);
    }

    @Test
    void getScoringCriteria_shouldReturnClassificationResult() throws Exception {
        when(scoringCriteriaClassificationAppService.classifyForProject(11L)).thenReturn(emptyScoringResult());

        mockMvc.perform(get("/api/projects/{projectId}/bid-agent/scoring-criteria", 11L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.msg").value("评分标准解析完成"));

        verify(scoringCriteriaClassificationAppService).classifyForProject(11L);
    }

    @Test
    void getScoringCriteria_shouldReturnForbiddenWhenProjectAccessDenied() throws Exception {
        when(scoringCriteriaClassificationAppService.classifyForProject(12L))
                .thenThrow(new AccessDeniedException("权限不足"));

        mockMvc.perform(get("/api/projects/{projectId}/bid-agent/scoring-criteria", 12L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(403));

        verify(scoringCriteriaClassificationAppService).classifyForProject(12L);
    }

    @Test
    void getKnowledgeBaseMatch_shouldReturnMatchResult() throws Exception {
        when(knowledgeBaseMatchAppService.matchForProject(11L)).thenReturn(emptyKnowledgeBaseMatchResult());

        mockMvc.perform(get("/api/projects/{projectId}/bid-agent/knowledge-base-match", 11L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.msg").value("四库联动匹配完成"));

        verify(knowledgeBaseMatchAppService).matchForProject(11L);
    }

    @Test
    void getKnowledgeBaseMatch_shouldReturnForbiddenWhenProjectAccessDenied() throws Exception {
        when(knowledgeBaseMatchAppService.matchForProject(12L))
                .thenThrow(new AccessDeniedException("权限不足"));

        mockMvc.perform(get("/api/projects/{projectId}/bid-agent/knowledge-base-match", 12L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(403));

        verify(knowledgeBaseMatchAppService).matchForProject(12L);
    }

    @Test
    void getFullAnalysis_shouldReturnAggregatedResult() throws Exception {
        when(fullAnalysisAppService.analyzeForProject(11L)).thenReturn(emptyFullAnalysisResult());

        mockMvc.perform(get("/api/projects/{projectId}/bid-agent/full-analysis", 11L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.msg").value("全维度分析完成"))
                .andExpect(jsonPath("$.data.riskSummary.redLineCount").value(0));

        verify(fullAnalysisAppService).analyzeForProject(11L);
    }

    @Test
    void getFullAnalysis_shouldReturnForbiddenWhenProjectAccessDenied() throws Exception {
        when(fullAnalysisAppService.analyzeForProject(12L))
                .thenThrow(new AccessDeniedException("权限不足"));

        mockMvc.perform(get("/api/projects/{projectId}/bid-agent/full-analysis", 12L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(403));

        verify(fullAnalysisAppService).analyzeForProject(12L);
    }
}
