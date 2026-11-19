// Input: mock 快照/LLM/仓库依赖 + 注入自代理
// Output: FR-021 重新解析覆盖语义断言（旧 results+items 失效清理、首次解析零删除）
// Pos: scoreparse/application — spec 041 US5（T043）
package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.biddraftagent.entity.BidTenderDocumentSnapshot;
import com.xiyu.bid.biddraftagent.repository.BidTenderDocumentSnapshotRepository;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.scoreparse.domain.RecallOutcome;
import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import com.xiyu.bid.scoreparse.domain.ScoreParseDecision;
import com.xiyu.bid.scoreparse.domain.WeightSumCheck;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import com.xiyu.bid.scoreparse.dto.ScoreParseItemsDTO;
import com.xiyu.bid.scoreparse.dto.ScoreParseTriggerDTO;
import com.xiyu.bid.scoreparse.entity.ScoreParseTask;
import com.xiyu.bid.scoreparse.infrastructure.openai.LlmTableScoreExtractor;
import com.xiyu.bid.scoreparse.infrastructure.openai.OpenAiScoreAnalyzer;
import com.xiyu.bid.scoreparse.infrastructure.openai.ScoreGlobalMergeExecutor;
import com.xiyu.bid.scoreparse.infrastructure.openai.ScoreTableSchemaMapper;
import com.xiyu.bid.scoreparse.repository.ScoreItemRepository;
import com.xiyu.bid.scoreparse.repository.ScoreParseTaskRepository;
import com.xiyu.bid.scoreparse.repository.ScoreResultRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FR-021 重新解析覆盖语义测试（spec 041 US5 / data-model.md §score_item）。
 * <p>重新解析 MUST 覆盖解析结果：新批次写入前按 project_id 清理旧
 * score_item 与级联的 score_result（评分项变化后旧打分结果失效）。
 */
@ExtendWith(MockitoExtension.class)
class ScoreParseAppServiceTest {

    private static final Long PROJECT_ID = 1L;
    private static final String TASK_ID = "task-reparse";

    @Mock
    private ScoreParseTaskRepository taskRepository;
    @Mock
    private ScoreItemRepository itemRepository;
    @Mock
    private ScoreResultRepository resultRepository;
    @Mock
    private ProjectDocumentRepository projectDocumentRepository;
    @Mock
    private ScoreParseTaskStateService stateService;
    @Mock
    private ScoreParseProgressService progressService;
    @Mock
    private BidTenderDocumentSnapshotRepository snapshotRepository;
    @Mock
    private ProjectAccessScopeService projectAccessScopeService;
    @Mock
    private OpenAiScoreAnalyzer scoreAnalyzer;
    @Mock
    private EstimatedScoreService estimatedScoreService;
    @Mock
    private ScoreItemPersistenceService itemPersistenceService;
    @Mock
    private InitiationTenderTextResolver initiationTenderTextResolver;
    @Mock
    private SidecarTableExtractor sidecarTableExtractor;
    private ScoreCandidatePostProcessor postProcessor;

    @Mock
    private ObjectProvider<ScoreGlobalMergeExecutor> mergeExecutorProvider;
    @Mock
    private ObjectProvider<SidecarDocumentArtifactExtractor> artifactExtractorProvider;
    @Mock
    private ObjectProvider<LlmTableScoreExtractor> llmTableScoreExtractorProvider;
    @Mock
    private ObjectProvider<ScoreTableSchemaMapper> schemaMapperProvider;

    private ScoreParseAppService service;

    @BeforeEach
    void setUp() {
        // spec 057：治理链入 PostProcessor——真实领域策略+真编排器（executor 缺席即 fail-open，
        // 归并裁决语义由 OrchestratorTest / PostProcessorTest 各自专项覆盖）
        lenient().when(mergeExecutorProvider.getIfAvailable()).thenReturn(null);
        lenient().when(artifactExtractorProvider.getIfAvailable()).thenReturn(null);
        lenient().when(llmTableScoreExtractorProvider.getIfAvailable()).thenReturn(null);
        lenient().when(schemaMapperProvider.getIfAvailable()).thenReturn(null);
        postProcessor = new ScoreCandidatePostProcessor(scoreAnalyzer,
                new ScoreGlobalMergeOrchestrator(mergeExecutorProvider),
                com.xiyu.bid.scoreparse.domain.ScoreVocabularyRegistry.builder().build());
        service = new ScoreParseAppService(
                taskRepository, itemRepository, resultRepository, projectDocumentRepository,
                stateService, progressService, snapshotRepository,
                projectAccessScopeService, scoreAnalyzer, estimatedScoreService,
                itemPersistenceService, initiationTenderTextResolver, sidecarTableExtractor,
                artifactExtractorProvider, postProcessor, llmTableScoreExtractorProvider, schemaMapperProvider);
        ReflectionTestUtils.setField(service, "self", service);
    }

    @Test
    void reparse_delegatesPersistenceWithCandidates() {
        mockParseFlowReturning(defaultCandidates());

        service.executeParseAsync(TASK_ID);

        // FR-021 覆盖语义委托给 ScoreItemPersistenceService（其测试类覆盖删除细节）
        ArgumentCaptor<List<ScoreCandidate>> candidates = capturedPersistedCandidates();
        assertThat(candidates.getValue()).hasSize(2);
        verify(stateService).markCompleted(TASK_ID);
    }

    @Test
    void parse_persistsRunSummaryOnTask() {
        // spec 057 FR-04：治理后 run_summary JSON 落库任务实体（lane/aligned/merge 档案可追溯）
        ScoreParseTask task = mockParseFlowReturning(defaultCandidates(), 0);

        service.executeParseAsync(TASK_ID);

        assertThat(task.getRunSummary()).isNotBlank();
        assertThat(task.getRunSummary()).contains("\"lanes\"", "\"tableRows\"");
        verify(stateService).markCompleted(TASK_ID);
    }

    @Test
    void pdfWithoutTablesRetainsOcrPermissionFailureThroughLegacySummary() throws Exception {
        var task = mockParseFlowReturning(defaultCandidates());
        String diagnostic = "BAIDU_OCR_PAGE_FAILED:24:PROVIDER_ERROR_6";
        when(initiationTenderTextResolver.loadTenderFile("doc-insight://t/1", "tender.pdf", null))
                .thenReturn(new LoadedBidFile.InMemory(new byte[]{1}, "tender.pdf", "application/pdf"));
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("doc", "sha", "PDF", "sidecar"),
                List.of(), List.of(diagnostic));
        var extractor = mock(SidecarDocumentArtifactExtractor.class);
        when(artifactExtractorProvider.getIfAvailable()).thenReturn(extractor);
        when(extractor.extract(any(byte[].class), eq("tender.pdf"))).thenReturn(artifact);

        service.executeParseAsync(TASK_ID);

        verify(stateService).markCompleted(TASK_ID);
        assertThat(capturedPersistedCandidates().getValue()).hasSize(2);
        assertThat(task.getRunSummary()).contains("\"lanes\"", "\"sourceDiagnostics\"", diagnostic);
    }

    @Test
    void parse_zeroItemsFailedPath_stillPersistsRunSummary() {
        // spec 057 xcode-review A-4：0 项失败终态同样落 runSummary——事故复盘需要失败时的 lane/对齐档案
        ScoreParseTask task = mockParseFlowReturning(List.of(), 3);

        service.executeParseAsync(TASK_ID);

        verify(stateService).failTask(eq(TASK_ID), anyString());
        verify(stateService, never()).markCompleted(anyString());
        assertThat(task.getRunSummary()).isNotBlank();
        assertThat(task.getRunSummary()).contains("\"lanes\"", "\"skippedChunks\":3");
        verify(itemPersistenceService, never()).persistItems(any(), any(), anyList());
    }

    @Test
    void firstParse_alsoDelegatesPersistence() {
        mockParseFlowReturning(defaultCandidates());

        service.executeParseAsync(TASK_ID);

        verify(itemPersistenceService).persistItems(
                eq(PROJECT_ID), eq(5L), anyList());
        verify(stateService).markCompleted(TASK_ID);
    }

    @Test
    void parse_filtersGhostCandidates() {
        // spec 046 FR-001：表头回声幽灵项（dim=detail="得分"≤4字、无code/sourceText）；
        // 过滤幽灵项后有效项权重 60+40=100，达到 AUTO_APPROVED 触发 persist
        mockParseFlowReturning(List.of(
                candidate("A1", "资质", "具备 CMMI 5 级认证证书", "60"),
                ghostHeaderEcho("40"),
                candidate("A2", "业绩", "近三年类似项目业绩不少于 3 个", "40")));

        service.executeParseAsync(TASK_ID);

        assertThat(capturedPersistedCandidates().getValue())
                .extracting(ScoreCandidate::code)
                .containsExactly("A1", "A2");
    }

    @Test
    void parse_trimsWeightOverload() {
        // spec 046 FR-002：合计 60+40+30=130 > 100.5 → 无 code 嫌疑项被剔，合计回 100
        mockParseFlowReturning(List.of(
                candidate("A1", "资质", "具备 CMMI 认证", "60"),
                candidate("A2", "业绩", "近三年业绩", "40"),
                noCodeCandidate("其他", "汇总说明行", "30")));

        service.executeParseAsync(TASK_ID);

        List<ScoreCandidate> persisted = capturedPersistedCandidates().getValue();
        assertThat(persisted).extracting(ScoreCandidate::code).containsExactly("A1", "A2");
        assertThat(persisted.stream().map(ScoreCandidate::weight)
                .reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("100");
    }

    @Test
    void parse_persistsSkippedChunks() {
        // spec 046 FR-005：LLM 跳过 chunk 数落库 task.skippedChunks
        ScoreParseTask task = mockParseFlowReturning(defaultCandidates(), 2);

        service.executeParseAsync(TASK_ID);

        assertThat(task.getSkippedChunks()).isEqualTo(2);
        verify(stateService).markCompleted(TASK_ID);
    }

    @Test
    void parse_reviewRequired_preservesExistingItemsWithReviewFlag() {
        // task2 收敛：权重合计 50+30=80 不符 100 → 决策 REVIEW_REQUIRED，
        // 只能记录任务复核状态，禁止覆盖既有 score_item 或触发估分。
        ScoreParseTask task = mockParseFlowReturning(List.of(
                candidate("A1", "资质", "具备 CMMI 认证", "50"),
                candidate("A2", "业绩", "近三年业绩", "30")));

        service.executeParseAsync(TASK_ID);

        verify(stateService).markCompleted(TASK_ID);
        verify(itemPersistenceService, never()).persistItems(any(), any(), anyList());
        verify(estimatedScoreService, never()).estimateForProject(any());
        assertThat(task.getDecisionStatus()).isEqualTo("REVIEW_REQUIRED");
        assertThat(task.getFallbackReason()).isEqualTo("WEIGHT_SUM_MISMATCH");
    }

    @Test
    void parse_structureIncomplete_preservesExistingItems_doesNotPersist() {
        // Fix A 守卫锚定：STRUCTURE_INCOMPLETE（结构损坏）保留现有 score_item 不覆盖，但任务仍 markCompleted。
        // 当前 PostProcessor 主链路不产出该决策（仅 spec059 证据管道产出），故 mock PostProcessor 直接钉死
        // AppService 持久化分支，防证据管道并入主链路后回归。
        ScoreCandidatePostProcessor mockPostProcessor = mock(ScoreCandidatePostProcessor.class);
        ScoreParseAppService structureService = new ScoreParseAppService(
                taskRepository, itemRepository, resultRepository, projectDocumentRepository,
                stateService, progressService, snapshotRepository,
                projectAccessScopeService, scoreAnalyzer, estimatedScoreService,
                itemPersistenceService, initiationTenderTextResolver, sidecarTableExtractor,
                artifactExtractorProvider, mockPostProcessor, llmTableScoreExtractorProvider, schemaMapperProvider);
        ReflectionTestUtils.setField(structureService, "self", structureService);

        ScoreParseTask task = mockParseFlowReturning(defaultCandidates());
        var weightResult = new WeightSumCheck.Result(new BigDecimal("100"), false, false,
                java.util.Map.of(), java.util.Map.of());
        var processed = new ScoreCandidatePostProcessor.Processed(
                List.of(candidate("A1", "资质", "具备 CMMI 认证", "100")), null, weightResult,
                "{}", null, null, "LEGACY",
                ScoreParseDecision.STRUCTURE_INCOMPLETE, "STRUCTURE_DEFECT");
        when(mockPostProcessor.process(anyString(), anyString(), any(), anyList(), any()))
                .thenReturn(processed);

        structureService.executeParseAsync(TASK_ID);

        verify(itemPersistenceService, never()).persistItems(any(), any(), anyList());
        verify(estimatedScoreService, never()).estimateForProject(any());
        verify(stateService).markCompleted(TASK_ID);
        assertThat(task.getDecisionStatus()).isEqualTo("STRUCTURE_INCOMPLETE");
    }

    @Test
    void parse_docxStructuredMismatch_marksStructureIncompleteAndSkipRecall() throws Exception {
        ScoreParseTask task = ScoreParseTask.builder()
                .id(5L).taskId(TASK_ID).projectId(PROJECT_ID).taskType("PARSE").status("PENDING").build();
        when(taskRepository.findByTaskId(TASK_ID)).thenReturn(Optional.of(task));
        TenderTextSource source = new TenderTextSource("bid.docx", "doc-insight://t/1", "评分办法：...");
        when(initiationTenderTextResolver.resolveIntake(PROJECT_ID))
                .thenReturn(TenderIntake.found(source));
        when(initiationTenderTextResolver.loadTenderFile("doc-insight://t/1", "bid.docx", null))
                .thenReturn(new LoadedBidFile.InMemory("docx-bytes".getBytes(StandardCharsets.UTF_8), "bid.docx", null));
        when(sidecarTableExtractor.extract(any(byte[].class), eq("bid.docx")))
                .thenThrow(new IllegalStateException("Sidecar 表格提取协议版本不匹配：期望 X-Schema-Version=1.0，实际响应体 v2。"));

        service.executeParseAsync(TASK_ID);

        verify(scoreAnalyzer, never()).recallCandidates(anyString(), isNull(), any());
        verify(itemPersistenceService, never()).persistItems(any(), any(), anyList());
        verify(estimatedScoreService, never()).estimateForProject(any());
        verify(sidecarTableExtractor).extract(any(byte[].class), eq("bid.docx"));
        verify(stateService).markCompleted(TASK_ID);
        assertThat(task.getPipelineVersion()).isEqualTo("LEGACY_FALLBACK");
        assertThat(task.getDecisionStatus()).isEqualTo("STRUCTURE_INCOMPLETE");
        assertThat(task.getFallbackReason()).isEqualTo("STRUCTURE_DEFECT");
        assertThat(task.getRunSummary()).isNotBlank();
    }

    @Test
    void parse_docxEvidencePipelineAutoApproved_skipsLegacyRecallAndPersistsFacts() throws Exception {
        ScoreParseTask task = docxTaskWithBytes();
        SidecarDocumentArtifactExtractor artifactExtractor = mock(SidecarDocumentArtifactExtractor.class);
        when(artifactExtractorProvider.getIfAvailable()).thenReturn(artifactExtractor);
        when(artifactExtractor.extract(any(byte[].class), eq("bid.docx")))
                .thenReturn(evidenceArtifact("60", "40"));

        service.executeParseAsync(TASK_ID);

        verify(scoreAnalyzer, never()).recallCandidates(anyString(), isNull(), any());
        verify(sidecarTableExtractor, never()).extract(any(byte[].class), anyString());
        verify(itemPersistenceService).persistFactsAndEvidence(eq(PROJECT_ID), eq(5L), anyList(), anyList());
        verify(itemPersistenceService).persistItems(eq(PROJECT_ID), eq(5L), anyList());
        verify(estimatedScoreService).estimateForProject(PROJECT_ID);
        verify(stateService).markCompleted(TASK_ID);
        assertThat(task.getPipelineVersion()).isEqualTo("EVIDENCE_PIPELINE_V1");
        assertThat(task.getDecisionStatus()).isEqualTo("AUTO_APPROVED");
        assertThat(task.getFallbackReason()).isNull();
        assertThat(task.getRunSummary()).contains("\"pipelineVersion\":\"EVIDENCE_PIPELINE_V1\"");
    }

    @Test
    void parse_project1381PdfStructuredSummary_persistsAllEightItems() throws Exception {
        ScoreParseTask task = ScoreParseTask.builder()
                .id(5L).taskId(TASK_ID).projectId(PROJECT_ID).taskType("PARSE").status("PENDING").build();
        when(taskRepository.findByTaskId(TASK_ID)).thenReturn(Optional.of(task));
        when(initiationTenderTextResolver.resolveIntake(PROJECT_ID))
                .thenReturn(TenderIntake.found(new TenderTextSource(
                        "project-1381-like.pdf", "doc-insight://t/1381",
                        "报价得满分30分，办公用品得满分10分。\n"
                                + "| 合计<br>得分 | 满分<br>（100分） | |")));
        when(initiationTenderTextResolver.loadTenderFile("doc-insight://t/1381", "project-1381-like.pdf", null))
                .thenReturn(new LoadedBidFile.InMemory("pdf-bytes".getBytes(StandardCharsets.UTF_8), "project-1381-like.pdf", null));
        SidecarDocumentArtifactExtractor artifactExtractor = mock(SidecarDocumentArtifactExtractor.class);
        when(artifactExtractorProvider.getIfAvailable()).thenReturn(artifactExtractor);
        when(artifactExtractor.extract(any(byte[].class), eq("project-1381-like.pdf")))
                .thenReturn(project1381EvidenceArtifact());
        var model = mock(MultimodalScoreFactExtractor.class);
        ReflectionTestUtils.setField(service, "multimodalScoreFactExtractor", model);

        service.executeParseAsync(TASK_ID);
        org.mockito.Mockito.verifyNoInteractions(model);


        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ScoreCandidate>> candidates = ArgumentCaptor.forClass(List.class);
        verify(itemPersistenceService).persistItems(eq(PROJECT_ID), eq(5L), candidates.capture());
        assertThat(candidates.getValue())
                .extracting(ScoreCandidate::dim)
                .containsExactly("投标报价", "公司综合实力", "类似业绩", "技术方案",
                        "供货方案", "人员配备", "质量管理方案", "售后服务方案");
        assertThat(candidates.getValue())
                .extracting(ScoreCandidate::weight)
                .containsExactly(new BigDecimal("40"), new BigDecimal("6"), new BigDecimal("10"),
                        new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("6"),
                        new BigDecimal("10"), new BigDecimal("8"));
        assertThat(task.getDecisionStatus()).isEqualTo("AUTO_APPROVED");
        assertThat(task.getFallbackReason()).isNull();
        verify(estimatedScoreService).estimateForProject(PROJECT_ID);
    }

    @Test
    void parse_docxEvidencePipelineReviewRequired_preservesExistingItemsButPersistsFacts() throws Exception {
        ScoreParseTask task = docxTaskWithBytes();
        SidecarDocumentArtifactExtractor artifactExtractor = mock(SidecarDocumentArtifactExtractor.class);
        when(artifactExtractorProvider.getIfAvailable()).thenReturn(artifactExtractor);
        when(artifactExtractor.extract(any(byte[].class), eq("bid.docx")))
                .thenReturn(evidenceArtifact("60", "20"));

        service.executeParseAsync(TASK_ID);

        verify(scoreAnalyzer, never()).recallCandidates(anyString(), isNull(), any());
        verify(itemPersistenceService).persistFactsAndEvidence(eq(PROJECT_ID), eq(5L), anyList(), anyList());
        verify(itemPersistenceService, never()).persistItems(any(), any(), anyList());
        verify(estimatedScoreService, never()).estimateForProject(any());
        verify(stateService).markCompleted(TASK_ID);
        assertThat(task.getPipelineVersion()).isEqualTo("EVIDENCE_PIPELINE_V1");
        assertThat(task.getDecisionStatus()).isEqualTo("REVIEW_REQUIRED");
        assertThat(task.getFallbackReason()).isEqualTo("WEIGHT_SUM_MISMATCH");
        assertThat(task.getOriginalTotalWeight()).isEqualByComparingTo("80");
        assertThat(task.getNormalizationWarning()).contains("保留旧评分项待复核");
    }

    @Test
    void parse_docxEvidencePipelineAutoApproved_usesDeclaredTotalInsteadOfHardcoded100() throws Exception {
        ScoreParseTask task = docxTaskWithBytes("本项目评分总分：80分。");
        SidecarDocumentArtifactExtractor artifactExtractor = mock(SidecarDocumentArtifactExtractor.class);
        when(artifactExtractorProvider.getIfAvailable()).thenReturn(artifactExtractor);
        when(artifactExtractor.extract(any(byte[].class), eq("bid.docx")))
                .thenReturn(evidenceArtifact("60", "20"));

        service.executeParseAsync(TASK_ID);

        verify(itemPersistenceService).persistItems(eq(PROJECT_ID), eq(5L), anyList());
        verify(estimatedScoreService).estimateForProject(PROJECT_ID);
        assertThat(task.getDecisionStatus()).isEqualTo("AUTO_APPROVED");
        assertThat(task.getOriginalTotalWeight()).isNull();
        assertThat(task.getNormalizationWarning()).isNull();
        assertThat(task.getRunSummary())
                .contains("\"finalTotalWeight\":80")
                .contains("\"decisionStatus\":\"AUTO_APPROVED\"");
    }

    @Test
    void structuredCodeExtractionWinsEvenWhenTextModelIsAvailable() throws Exception {
        ScoreParseTask task = docxTaskWithBytes();
        var artifactExtractor = mock(SidecarDocumentArtifactExtractor.class);
        when(artifactExtractorProvider.getIfAvailable()).thenReturn(artifactExtractor);
        when(artifactExtractor.extract(any(byte[].class), eq("bid.docx")))
                .thenReturn(evidenceArtifact("60", "40"));
        var model = mock(MultimodalScoreFactExtractor.class);
        lenient().when(model.supportsTextOnly()).thenReturn(true);
        ReflectionTestUtils.setField(service, "multimodalScoreFactExtractor", model);

        service.executeParseAsync(TASK_ID);

        org.mockito.Mockito.verifyNoInteractions(model);
        verify(scoreAnalyzer, never()).recallCandidates(anyString(), isNull(), any());
        verify(itemPersistenceService).persistFactsAndEvidence(eq(PROJECT_ID), eq(5L), anyList(), anyList());
        assertThat(task.getDecisionStatus()).isEqualTo("AUTO_APPROVED");
    }

    @Test
    void structuredCodeReadsEveryTenderAttachmentWithoutCallingModel() throws Exception {
        ScoreParseTask task = ScoreParseTask.builder().id(5L).taskId(TASK_ID)
                .projectId(PROJECT_ID).taskType("PARSE").status("PENDING").build();
        when(taskRepository.findByTaskId(TASK_ID)).thenReturn(Optional.of(task));
        var sources = List.of(new TenderTextSource("first.docx", "first", "评分总分100分。"),
                new TenderTextSource("second.docx", "second", "评分总分100分。"));
        var files = List.<LoadedBidFile>of(new LoadedBidFile.InMemory(new byte[]{1}, "first.docx", null),
                new LoadedBidFile.InMemory(new byte[]{2}, "second.docx", null));
        when(initiationTenderTextResolver.resolveExecutionSource(PROJECT_ID))
                .thenReturn(TenderExecutionSource.found(sources, files));
        var artifactExtractor = mock(SidecarDocumentArtifactExtractor.class);
        when(artifactExtractorProvider.getIfAvailable()).thenReturn(artifactExtractor);
        when(artifactExtractor.extract(any(byte[].class), eq("first.docx"))).thenReturn(evidenceArtifact("60", "40"));
        when(artifactExtractor.extract(any(byte[].class), eq("second.docx"))).thenReturn(evidenceArtifact("20", "80"));
        var model = mock(MultimodalScoreFactExtractor.class);
        ReflectionTestUtils.setField(service, "multimodalScoreFactExtractor", model);

        service.executeParseAsync(TASK_ID);

        org.mockito.Mockito.verifyNoInteractions(model);
        verify(artifactExtractor).extract(any(byte[].class), eq("first.docx"));
        verify(artifactExtractor).extract(any(byte[].class), eq("second.docx"));
        verify(itemPersistenceService).persistFactsAndEvidence(eq(PROJECT_ID), eq(5L),
                org.mockito.ArgumentMatchers.argThat(facts -> facts.size() == 4
                        && facts.stream().anyMatch(f -> f.sourceTableId().startsWith("doc0-"))
                        && facts.stream().anyMatch(f -> f.sourceTableId().startsWith("doc1-"))), anyList());
    }

    private List<ScoreCandidate> defaultCandidates() {
        return List.of(
                candidate("A1", "资质", "具备 CMMI 5 级认证证书", "60"),
                candidate("A2", "业绩", "近三年类似项目业绩不少于 3 个", "40"));
    }

    private ScoreParseTask docxTaskWithBytes() throws Exception {
        return docxTaskWithBytes("评分办法：总分100分。");
    }

    private ScoreParseTask docxTaskWithBytes(String text) throws Exception {
        ScoreParseTask task = ScoreParseTask.builder()
                .id(5L).taskId(TASK_ID).projectId(PROJECT_ID).taskType("PARSE").status("PENDING").build();
        when(taskRepository.findByTaskId(TASK_ID)).thenReturn(Optional.of(task));
        when(initiationTenderTextResolver.resolveIntake(PROJECT_ID))
                .thenReturn(TenderIntake.found(new TenderTextSource(
                        "bid.docx", "doc-insight://t/1", text)));
        when(initiationTenderTextResolver.loadTenderFile("doc-insight://t/1", "bid.docx", null))
                .thenReturn(new LoadedBidFile.InMemory("docx-bytes".getBytes(StandardCharsets.UTF_8), "bid.docx", null));
        return task;
    }

    private DocumentArtifact evidenceArtifact(String firstWeight, String secondWeight) {
        return new DocumentArtifact(
                new DocumentArtifact.ArtifactMeta("doc-1", "sha256:test", "DOCX", "table-v2"),
                List.of(new DocumentArtifact.CanonicalTable(
                        "t-0000", null, "评分标准", 3, 4, List.of(), List.of(
                                artifactCell("t-0000-r0-c0", 0, 0, "编号"),
                                artifactCell("t-0000-r0-c1", 0, 1, "评分因素"),
                                artifactCell("t-0000-r0-c2", 0, 2, "评分标准"),
                                artifactCell("t-0000-r0-c3", 0, 3, "分值"),
                                artifactCell("t-0000-r1-c0", 1, 0, "A1"),
                                artifactCell("t-0000-r1-c1", 1, 1, "商务资质"),
                                artifactCell("t-0000-r1-c2", 1, 2, "资质证书齐全"),
                                artifactCell("t-0000-r1-c3", 1, 3, firstWeight),
                                artifactCell("t-0000-r2-c0", 2, 0, "A2"),
                                artifactCell("t-0000-r2-c1", 2, 1, "技术方案"),
                                artifactCell("t-0000-r2-c2", 2, 2, "方案完整可行"),
                                artifactCell("t-0000-r2-c3", 2, 3, secondWeight)),
                        true)),
                List.of());
    }

    private DocumentArtifact project1381EvidenceArtifact() {
        List<List<String>> rows = List.of(
                List.of("序号", "评分因素", "评分标准"),
                List.of("1", "投标报价\n（40分）", "报价规则"),
                List.of("2", "公司综合实力\n（6分）", "证书清单"),
                List.of("3", "类似业绩\n（10分）", "类似案例"),
                List.of("4", "技术方案\n（10分）", "技术方案"),
                List.of("4", "供货方案\n（10分）", "供货保障"),
                List.of("5", "人员配备\n（6分）", "人员配置"),
                List.of("6", "质量管理方案（10分）", "质量管理"),
                List.of("7", "售后服务方案（8分）", "售后服务"),
                List.of("合计\n得分", "满分\n（100分）", ""));
        List<DocumentArtifact.CanonicalTable.CanonicalCellDto> cells = new ArrayList<>();
        for (int row = 0; row < rows.size(); row++) {
            for (int column = 0; column < rows.get(row).size(); column++) {
                cells.add(artifactCell("p24-t0007-r" + row + "-c" + column,
                        row, column, rows.get(row).get(column)));
            }
        }
        return new DocumentArtifact(
                new DocumentArtifact.ArtifactMeta("doc-1381", "sha256:1381", "PDF", "pdf-table-v1"),
                List.of(new DocumentArtifact.CanonicalTable(
                        "p24-t0007", 24, "综合评分法", rows.size(), 3, List.of(), cells, true)),
                List.of());
    }

    private DocumentArtifact.CanonicalTable.CanonicalCellDto artifactCell(
            String id, int row, int column, String text) {
        return new DocumentArtifact.CanonicalTable.CanonicalCellDto(
                id, row, column, text, text, 1, 1, false,
                "/w:document[1]/w:body[1]/w:tbl[1]/w:tr[" + (row + 1) + "]/w:tc[" + (column + 1) + "]");
    }

    private ScoreParseTask mockParseFlowReturning(List<ScoreCandidate> recalled) {
        return mockParseFlowReturning(recalled, 0);
    }

    private ScoreParseTask mockParseFlowReturning(List<ScoreCandidate> recalled, int skippedChunks) {
        ScoreParseTask task = ScoreParseTask.builder()
                .id(5L).taskId(TASK_ID).projectId(PROJECT_ID)
                .taskType("PARSE").status("PENDING").build();
        when(taskRepository.findByTaskId(TASK_ID)).thenReturn(Optional.of(task));
        when(initiationTenderTextResolver.resolveIntake(PROJECT_ID))
                .thenReturn(TenderIntake.found(new TenderTextSource(
                        "tender.pdf", "doc-insight://t/1", "评分办法：...")));
        when(scoreAnalyzer.recallCandidates(anyString(), isNull(), any()))
                .thenReturn(new RecallOutcome(recalled, skippedChunks));
        return task;
    }

    /** project 212 表头回声：dim=detail="得分"、无 code/sourceText */
    private ScoreCandidate ghostHeaderEcho(String weight) {
        return new ScoreCandidate(null, "得分", "得分", new BigDecimal(weight),
                "OBJECTIVE", null, null, "P47", "SEMANTIC");
    }

    /** 无 code 但有 sourceText（避开幽灵规则2，命中 FR-002 第一轮剔除） */
    private ScoreCandidate noCodeCandidate(String dim, String detail, String weight) {
        return new ScoreCandidate(null, dim, detail, new BigDecimal(weight),
                "OBJECTIVE", null, "原文条款文本", "P47", "SEMANTIC");
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<List<ScoreCandidate>> capturedPersistedCandidates() {
        ArgumentCaptor<List<ScoreCandidate>> captor = ArgumentCaptor.forClass(List.class);
        verify(itemPersistenceService).persistItems(eq(PROJECT_ID), eq(5L), captor.capture());
        return captor;
    }

    private ScoreCandidate candidate(String code, String dim, String detail, String weight) {
        return new ScoreCandidate(code, dim, detail, new BigDecimal(weight),
                "OBJECTIVE", null, detail, "P47", "SEMANTIC");
    }

    @Test
    void triggerParse_rejectsWhenNoInitiationTender() {
        when(initiationTenderTextResolver.hasSource(PROJECT_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.triggerParse(PROJECT_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(InitiationTenderTextResolver.NO_TENDER_MESSAGE);
        verify(stateService).createTask(anyString(), eq(PROJECT_ID), eq("PARSE"), isNull(), isNull(), eq("MANUAL"));
        verify(stateService).failTask(anyString(), eq(InitiationTenderTextResolver.NO_TENDER_MESSAGE));
    }

    @Test
    void triggerParse_whenSourceExists_returnsPendingAndDispatchesAsync() {
        when(initiationTenderTextResolver.hasSource(PROJECT_ID)).thenReturn(true);

        ScoreParseTriggerDTO trigger = service.triggerParse(PROJECT_ID);

        assertThat(trigger.status()).isEqualTo("PENDING");
        verify(stateService).createTask(eq(trigger.taskId()), eq(PROJECT_ID), eq("PARSE"), isNull(), isNull(), eq("MANUAL"));
        verify(initiationTenderTextResolver, never()).resolveIntake(any());
        verify(initiationTenderTextResolver, never()).resolveExecutionSource(any());
    }

    @Test
    void executeParse_oversizedTextFailedPath_neverOverwritesExistingItems() {
        ScoreParseTask task = ScoreParseTask.builder()
                .id(5L).taskId(TASK_ID).projectId(PROJECT_ID)
                .taskType("PARSE").status("PENDING").build();
        when(taskRepository.findByTaskId(TASK_ID)).thenReturn(Optional.of(task));
        when(initiationTenderTextResolver.resolveIntake(PROJECT_ID))
                .thenReturn(TenderIntake.unavailable(OversizedTenderTextException.MESSAGE));

        service.executeParseAsync(TASK_ID);

        verify(stateService).failTask(eq(TASK_ID), eq(OversizedTenderTextException.MESSAGE));
        verify(itemPersistenceService, never()).persistItems(any(), any(), anyList());
    }

    @Test
    void getItems_metaFallsBackToSnapshotName() {
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(PROJECT_ID)).thenReturn(List.of());
        when(initiationTenderTextResolver.findLatestTenderDocument(PROJECT_ID))
                .thenReturn(Optional.empty());
        when(snapshotRepository.findTopByProjectIdOrderByCreatedAtDescIdDesc(PROJECT_ID))
                .thenReturn(Optional.of(BidTenderDocumentSnapshot.builder()
                        .fileName("旧快照.pdf").build()));
        when(taskRepository.findByProjectIdAndTaskTypeAndStatusIn(anyLong(), anyString(), anyList()))
                .thenReturn(List.of());

        ScoreParseItemsDTO dto = service.getItems(PROJECT_ID);

        assertThat(dto.meta().sourceFileName()).isEqualTo("旧快照.pdf");
        assertThat(dto.meta().lastParseStatus()).isNull();
        assertThat(dto.meta().lastParseError()).isNull();
    }

    @Test
    void getItems_lastParseMetaNullWhenNoParseTask() {
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(PROJECT_ID)).thenReturn(List.of());
        when(initiationTenderTextResolver.findLatestTenderDocument(PROJECT_ID))
                .thenReturn(Optional.empty());
        when(snapshotRepository.findTopByProjectIdOrderByCreatedAtDescIdDesc(PROJECT_ID))
                .thenReturn(Optional.empty());
        when(taskRepository.findByProjectIdAndTaskTypeAndStatusIn(anyLong(), anyString(), anyList()))
                .thenReturn(List.of());

        ScoreParseItemsDTO dto = service.getItems(PROJECT_ID);

        assertThat(dto.meta().lastParseStatus()).isNull();
        assertThat(dto.meta().lastParseError()).isNull();
    }

    @Test
    void getItems_lastParseMetaCarriesFailedError() {
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(PROJECT_ID)).thenReturn(List.of());
        when(initiationTenderTextResolver.findLatestTenderDocument(PROJECT_ID))
                .thenReturn(Optional.of(ProjectDocument.builder().name("招标.pdf").build()));
        when(taskRepository.findByProjectIdAndTaskTypeAndStatusIn(
                eq(PROJECT_ID), eq("PARSE"), anyList()))
                .thenReturn(List.of(ScoreParseTask.builder()
                        .id(9L).taskType("PARSE").status("FAILED")
                        .errorMessage("立项招标文件无法读取").build()));
        when(taskRepository.findByProjectIdAndTaskTypeAndStatusIn(
                eq(PROJECT_ID), eq("SCORING"), anyList()))
                .thenReturn(List.of());

        ScoreParseItemsDTO dto = service.getItems(PROJECT_ID);

        assertThat(dto.meta().lastParseStatus()).isEqualTo("FAILED");
        assertThat(dto.meta().lastParseError()).isEqualTo("立项招标文件无法读取");
    }

    @Test
    void getItems_metaCarriesFileNamesAndTimes() {
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(PROJECT_ID)).thenReturn(List.of());
        when(initiationTenderTextResolver.findLatestTenderDocument(PROJECT_ID))
                .thenReturn(Optional.of(ProjectDocument.builder().name("招标文件-v3.pdf").build()));
        when(taskRepository.findByProjectIdAndTaskTypeAndStatusIn(
                eq(PROJECT_ID), eq("PARSE"), anyList()))
                .thenReturn(List.of(ScoreParseTask.builder()
                        .id(1L).taskType("PARSE").status("COMPLETED")
                        .completedAt(LocalDateTime.of(2026, 8, 16, 10, 0)).build()));
        when(taskRepository.findByProjectIdAndTaskTypeAndStatusIn(
                eq(PROJECT_ID), eq("SCORING"), anyList()))
                .thenReturn(List.of(ScoreParseTask.builder()
                        .id(2L).taskType("SCORING").status("COMPLETED")
                        .fileName("投标文件-终稿.docx")
                        .completedAt(LocalDateTime.of(2026, 8, 16, 11, 0)).build()));

        ScoreParseItemsDTO dto = service.getItems(PROJECT_ID);

        assertThat(dto.meta().sourceFileName()).isEqualTo("招标文件-v3.pdf");
        assertThat(dto.meta().parseTime()).isEqualTo(LocalDateTime.of(2026, 8, 16, 10, 0));
        assertThat(dto.meta().bidFileName()).isEqualTo("投标文件-终稿.docx");
        assertThat(dto.meta().scoreTime()).isEqualTo(LocalDateTime.of(2026, 8, 16, 11, 0));
    }

    @Test
    void getItems_metaNullSafeWhenNoSnapshotOrTasks() {
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(PROJECT_ID)).thenReturn(List.of());
        when(initiationTenderTextResolver.findLatestTenderDocument(PROJECT_ID))
                .thenReturn(Optional.empty());
        when(snapshotRepository.findTopByProjectIdOrderByCreatedAtDescIdDesc(PROJECT_ID))
                .thenReturn(Optional.empty());
        when(taskRepository.findByProjectIdAndTaskTypeAndStatusIn(anyLong(), anyString(), anyList()))
                .thenReturn(List.of());

        ScoreParseItemsDTO dto = service.getItems(PROJECT_ID);

        assertThat(dto.meta().sourceFileName()).isNull();
        assertThat(dto.meta().parseTime()).isNull();
        assertThat(dto.meta().bidFileName()).isNull();
        assertThat(dto.meta().scoreTime()).isNull();
        assertThat(dto.meta().lastParseStatus()).isNull();
        assertThat(dto.meta().lastParseError()).isNull();
    }

    @Test
    void triggerParse_autoWithHistory_doesNotCreateTask() {
        ScoreParseTask old = ScoreParseTask.builder()
                .id(3L).taskId("old-parse").projectId(PROJECT_ID)
                .taskType("PARSE").status("COMPLETED").build();
        when(taskRepository.findByProjectIdAndTaskTypeAndStatusIn(
                PROJECT_ID, "PARSE", List.of("PENDING", "PROCESSING"))).thenReturn(List.of());
        when(taskRepository.findByProjectIdAndTaskTypeAndStatusIn(
                PROJECT_ID, "PARSE", List.of("PENDING", "PROCESSING", "COMPLETED", "FAILED")))
                .thenReturn(List.of(old));
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(PROJECT_ID)).thenReturn(List.of());

        var dto = service.triggerParse(PROJECT_ID, "AUTO");

        assertThat(dto.taskId()).isEqualTo("old-parse");
        verify(stateService, org.mockito.Mockito.never())
                .createTask(anyString(), any(), anyString(), any(), any(), anyString());
    }

    @Test
    void triggerParse_manualWithHistory_stillCreates() {
        when(taskRepository.findByProjectIdAndTaskTypeAndStatusIn(
                PROJECT_ID, "PARSE", List.of("PENDING", "PROCESSING"))).thenReturn(List.of());
        when(taskRepository.findByProjectIdAndTaskTypeAndStatusIn(
                PROJECT_ID, "PARSE", List.of("PENDING", "PROCESSING", "COMPLETED", "FAILED")))
                .thenReturn(List.of(ScoreParseTask.builder()
                        .id(3L).taskId("old-parse").taskType("PARSE").status("COMPLETED").build()));
        when(initiationTenderTextResolver.resolveIntake(PROJECT_ID))
                .thenReturn(TenderIntake.found(new TenderTextSource(
                        "tender.pdf", "doc-insight://t/1", "评分办法")));

        service.triggerParse(PROJECT_ID, "MANUAL");

        verify(stateService).createTask(anyString(), eq(PROJECT_ID), eq("PARSE"), isNull(), isNull(), eq("MANUAL"));
    }

    @Test
    void executeParse_dropsExtremeWeightsOver100_andSucceeds() {
        // 防御：召回池中混入误识别的年份（2024）或金额（100000000）等异常大数 → filterInvalidWeights 过滤丢弃，正常分值成功解析
        List<ScoreCandidate> mixed = List.of(
                new ScoreCandidate("1", "商务", "年份条款", new BigDecimal("2024"), null, null, "2024年成立", "P1", null),
                new ScoreCandidate("2", "技术", "金额条款", new BigDecimal("100000000"), null, null, "注册资本1亿元", "P2", null),
                new ScoreCandidate("3", "技术", "实施方案", new BigDecimal("30"), null, null, "方案完整得30分", "P3", null),
                new ScoreCandidate("4", "商务", "财务审计", new BigDecimal("70"), null, null, "审计报告得70分", "P4", null));
        mockParseFlowReturning(mixed);

        service.executeParseAsync(TASK_ID);

        verify(stateService).markCompleted(TASK_ID);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ScoreCandidate>> captor = ArgumentCaptor.forClass(List.class);
        verify(itemPersistenceService).persistItems(eq(PROJECT_ID), eq(5L), captor.capture());
        List<ScoreCandidate> persisted = captor.getValue();
        assertThat(persisted).hasSize(2);
        assertThat(persisted.stream().map(ScoreCandidate::weight).toList())
                .containsExactly(new BigDecimal("30"), new BigDecimal("70"));
    }

    @Test
    void executeParse_withSchemaMapper_usesCustomMapping() {
        String tableText = """
                | 序号 | 评分大类 | 评分因素 | 评分标准 | 分值 |
                | --- | --- | --- | --- | --- |
                | 1 | 商务部分 | 人员资质 | 具备PMP证书得100分 | 100 |
                """;
        mockParseFlowWithText(tableText);
        ScoreTableSchemaMapper mockMapper = org.mockito.Mockito.mock(ScoreTableSchemaMapper.class);
        when(schemaMapperProvider.getIfAvailable()).thenReturn(mockMapper);
        when(mockMapper.mapSchema(any())).thenReturn(
                new com.xiyu.bid.scoreparse.domain.grid.TableColumnMapping(0, 1, 2, 3, 4, 1, false));

        service.executeParseAsync(TASK_ID);

        verify(stateService).markCompleted(TASK_ID);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ScoreCandidate>> captor = ArgumentCaptor.forClass(List.class);
        verify(itemPersistenceService).persistItems(eq(PROJECT_ID), eq(5L), captor.capture());
        List<ScoreCandidate> persisted = captor.getValue();
        assertThat(persisted).hasSize(1);
        assertThat(persisted.get(0).dim()).isEqualTo("商务部分/人员资质");
    }

    @Test
    void executeParse_withoutSchemaMapper_fallsBackToHeuristic() {
        String tableText = """
                | 序号 | 评分项目 | 评分标准 | 分值 |
                | --- | --- | --- | --- |
                | 1 | 商务部分 | 财务状况：财务状况良好得30分 | 30 |
                | 2 | 技术方案 | 方案详细可行得70分 | 70 |
                """;
        mockParseFlowWithText(tableText);
        when(schemaMapperProvider.getIfAvailable()).thenReturn(null);

        service.executeParseAsync(TASK_ID);

        verify(stateService).markCompleted(TASK_ID);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ScoreCandidate>> captor = ArgumentCaptor.forClass(List.class);
        verify(itemPersistenceService).persistItems(eq(PROJECT_ID), eq(5L), captor.capture());
        List<ScoreCandidate> persisted = captor.getValue();
        assertThat(persisted).hasSize(2);
        assertThat(ScoreItemPersistenceService.resolveDim(persisted.getFirst())).isEqualTo("财务状况");
        assertThat(persisted.get(0).weight()).isEqualByComparingTo(new BigDecimal("30"));
        assertThat(persisted.get(1).weight()).isEqualByComparingTo(new BigDecimal("70"));
    }

    @Test
    void executeParse_whenSchemaMapperThrows_fallsBackToHeuristic() {
        String tableText = """
                | 序号 | 评分项目 | 评分标准 | 分值 |
                | --- | --- | --- | --- |
                | 1 | 商务部分 | 财务状况：财务状况良好得30分 | 30 |
                | 2 | 技术方案 | 方案详细可行得70分 | 70 |
                """;
        mockParseFlowWithText(tableText);
        ScoreTableSchemaMapper mockMapper = org.mockito.Mockito.mock(ScoreTableSchemaMapper.class);
        when(schemaMapperProvider.getIfAvailable()).thenReturn(mockMapper);
        when(mockMapper.mapSchema(any())).thenThrow(new RuntimeException("LLM network timeout"));

        service.executeParseAsync(TASK_ID);

        verify(stateService).markCompleted(TASK_ID);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ScoreCandidate>> captor = ArgumentCaptor.forClass(List.class);
        verify(itemPersistenceService).persistItems(eq(PROJECT_ID), eq(5L), captor.capture());
        List<ScoreCandidate> persisted = captor.getValue();
        assertThat(persisted).hasSize(2);
        assertThat(ScoreItemPersistenceService.resolveDim(persisted.getFirst())).isEqualTo("财务状况");
        assertThat(persisted.get(0).weight()).isEqualByComparingTo(new BigDecimal("30"));
        assertThat(persisted.get(1).weight()).isEqualByComparingTo(new BigDecimal("70"));
    }

    @Test
    void executeParse_legacyAutoApprovedWithUnresolvedName_downgradesToReviewRequiredAndCompletesTask() {
        String tableText = """
                | 序号 | 评分项目 | 评分标准 | 分值 |
                | --- | --- | --- | --- |
                | 1 | 1 | 商务评分（20分）<br>具体得分按响应完整度评审 | 30 |
                | 2 | 技术方案 | 方案详细可行得70分 | 70 |
                """;
        mockParseFlowWithText(tableText);
        when(schemaMapperProvider.getIfAvailable()).thenReturn(null);

        service.executeParseAsync(TASK_ID);

        verify(itemPersistenceService, never()).persistItems(any(), any(), anyList());
        verify(itemPersistenceService).persistReviewCandidateFacts(eq(PROJECT_ID), eq(5L), anyList());
        verify(estimatedScoreService, never()).estimateForProject(any());
        verify(stateService).markCompleted(TASK_ID);
        verify(stateService, never()).failTask(any(), any());
    }

    @Test
    void legacyAutoApprovalRequiresReviewWhenCategoryHasOnlyScoringProse() {
        var task = mockParseFlowWithText("""
                | 序号 | 评分项目 | 评分标准 | 分值 |
                | --- | --- | --- | --- |
                | 1 | 商务 | 投标人提供营业收入证明得30分 | 30 |
                | 2 | 技术方案 | 方案详细可行得70分 | 70 |
                """);
        when(schemaMapperProvider.getIfAvailable()).thenReturn(null);

        service.executeParseAsync(TASK_ID);

        assertThat(task.getDecisionStatus()).isEqualTo("REVIEW_REQUIRED");
        assertThat(task.getFallbackReason()).isEqualTo("INVALID_SCORE_ITEM_NAME");
        verify(itemPersistenceService, never()).persistItems(any(), any(), anyList());
        verify(itemPersistenceService).persistReviewCandidateFacts(eq(PROJECT_ID), eq(5L), anyList());
        verify(estimatedScoreService, never()).estimateForProject(any());
        verify(stateService).markCompleted(TASK_ID);
    }

    @Test
    void legacyTableWithTruncatedNameAndOnlyRuleBodyRequiresReview() throws Exception {
        String tableText = """
                | 序号 | 评分项目 | 评分标准 | 分值 |
                | --- | --- | --- | --- |
                | 1 | 商务评分（20 | 投标人提供营业收入证明得30分 | 30 |
                | 2 | 技术方案 | 方案详细可行得70分 | 70 |
                """;
        var task = mockParseFlowWithText(tableText);
        when(schemaMapperProvider.getIfAvailable()).thenReturn(null);

        service.executeParseAsync(TASK_ID);

        assertThat(task.getDecisionStatus()).isEqualTo("REVIEW_REQUIRED");
        assertThat(task.getFallbackReason()).isEqualTo("INVALID_SCORE_ITEM_NAME");
        var summary = new com.fasterxml.jackson.databind.ObjectMapper().readTree(task.getRunSummary());
        assertThat(summary.path("decisionStatus").asText()).isEqualTo(task.getDecisionStatus());
        assertThat(summary.path("fallbackReason").asText()).isEqualTo(task.getFallbackReason());
        assertThat(summary.path("finalTotalWeight").decimalValue()).isEqualByComparingTo("100");
        verify(itemPersistenceService, never()).persistItems(any(), any(), anyList());
        verify(itemPersistenceService).persistReviewCandidateFacts(eq(PROJECT_ID), eq(5L), anyList());
        verify(estimatedScoreService, never()).estimateForProject(any());
        verify(stateService).markCompleted(TASK_ID);
    }

    private ScoreParseTask mockParseFlowWithText(String fullText) {
        ScoreParseTask task = ScoreParseTask.builder()
                .id(5L).taskId(TASK_ID).projectId(PROJECT_ID).taskType("PARSE").status("PENDING").build();
        when(taskRepository.findByTaskId(TASK_ID)).thenReturn(Optional.of(task));
        when(initiationTenderTextResolver.resolveIntake(PROJECT_ID))
                .thenReturn(TenderIntake.found(new TenderTextSource("tender.pdf", "doc-insight://t/1", fullText)));
        lenient().when(scoreAnalyzer.recallCandidates(anyString(), isNull(), any()))
                .thenReturn(new RecallOutcome(List.of(), 0));
        return task;
    }
}
