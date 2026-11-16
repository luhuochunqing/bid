// Input: projectId（触发打分）/ mock 协调器与 LLM 依赖
// Output: 版本边界前置校验/真实评分链/超限与故障传播/原文证据/失败保留结果断言
// Pos: scoreparse/application — spec 041 US4 + 045 T012（coordinator 二态接线）
package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.scoreparse.dto.ScoreParseTriggerDTO;
import com.xiyu.bid.scoreparse.entity.ScoreItem;
import com.xiyu.bid.scoreparse.entity.ScoreParseTask;
import com.xiyu.bid.scoreparse.entity.ScoreResult;
import com.xiyu.bid.scoreparse.infrastructure.openai.OpenAiScoreAnalyzer;
import com.xiyu.bid.scoreparse.infrastructure.openai.ScoreAssessmentOutput;
import com.xiyu.bid.scoreparse.repository.ScoreItemRepository;
import com.xiyu.bid.scoreparse.repository.ScoreParseTaskRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 阶段 2 实际打分编排测试（spec 041 US4 / 045 接线）。
 * <p>FR-019 前置校验（标书已上传 + 评分项就绪 + 任务互斥）；
 * FR-021 版本边界整批发布；输入失败保留旧结果，单项失败明确发布失败占位；
 * spec 045：同步段配额拒绝语义 + 异步段文本量超限传播。
 */
@ExtendWith(MockitoExtension.class)
class ScoreScoringAppServiceTest {

    private static final Long PROJECT_ID = 1L;
    private static final String BID_FILE_URL = "doc-insight://bid/test.pdf";
    private static final String BID_TEXT = "[第 1 页]\n投标文件全文内容CMMI5认证证书\n技术方案完善、可行";

    @Mock
    private ScoreParseTaskRepository taskRepository;
    @Mock
    private ScoreItemRepository itemRepository;
    @Mock
    private ScoreScoringVersionService versions;
    @Mock
    private ScoreParseTaskStateService stateService;
    @Mock
    private ScoreParseProgressService progressService;
    @Mock
    private ProjectAccessScopeService projectAccessScopeService;
    @Mock
    private OpenAiScoreAnalyzer scoreAnalyzer;
    @Mock
    private BidFileLoadingCoordinator bidFileLoader;

    private ScoreScoringAppService service;

    @BeforeEach
    void setUp() {
        // 预算参数取 dev 默认（spec 048 T019：app.score-parse.multimodal.* 注入）
        service = new ScoreScoringAppService(
                taskRepository, itemRepository,
                stateService, progressService, projectAccessScopeService, scoreAnalyzer,
                bidFileLoader, versions, 3, 5242880L);
        ReflectionTestUtils.setField(service, "self", service);
        org.mockito.Mockito.lenient().when(versions.prepare(eq(PROJECT_ID), any())).thenAnswer(call ->
                ((java.util.function.Supplier<?>) call.getArgument(1)).get());
        org.mockito.Mockito.lenient().when(versions.lockedSnapshot(PROJECT_ID)).thenReturn(snapshot(List.of()));
        org.mockito.Mockito.lenient().when(versions.start(anyString())).thenAnswer(call -> versions.lockedSnapshot(PROJECT_ID));
        org.mockito.Mockito.lenient().when(versions.capture(PROJECT_ID)).thenAnswer(call -> versions.lockedSnapshot(PROJECT_ID));
        org.mockito.Mockito.lenient().when(versions.recordInput(any(), eq(77L))).thenReturn(true);
        org.mockito.Mockito.lenient().when(scoreAnalyzer.scoringConfiguration()).thenReturn(configuration());
    }

    @Test
    void noBidDocument_rejectsWithSemantic() {
        mockItemsPresent();
        when(bidFileLoader.findBidDocuments(PROJECT_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> service.triggerScoring(PROJECT_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NO_BID_DOCUMENT");
        verify(stateService, never()).createTask(anyString(), any(), anyString(), any(), any(), anyString());
    }

    @Test
    void triggerScoring_returnsPendingImmediatelyWithoutDownloading() throws Exception {
        // 同步段零下载（spec 045 性能优化）：triggerScoring 快速创建任务返回 PENDING，不阻塞 HTTP 线程
        mockNoActiveScoringTask();
        mockBidDocumentPresent();
        mockItemsPresent();
        when(stateService.createTask(anyString(), any(), anyString(), any(), any(), anyString()))
                .thenReturn(scoringTask());

        ReflectionTestUtils.setField(service, "self", org.mockito.Mockito.mock(ScoreScoringAppService.class));
        ScoreParseTriggerDTO dto = service.triggerScoring(PROJECT_ID);

        assertThat(dto.status()).isEqualTo("PENDING");
        verify(bidFileLoader, never()).openScoring(any(Long.class), anyString(), org.mockito.ArgumentMatchers.anyLong());
        verify(stateService).createTask(anyString(), eq(PROJECT_ID), eq("SCORING"), any(), any(), anyString());
        verify(versions).prepare(eq(PROJECT_ID), any());
        ArgumentCaptor<ScoreParseTask> created = ArgumentCaptor.forClass(ScoreParseTask.class);
        verify(taskRepository).save(created.capture());
        assertThat(created.getValue().getPipelineVersion()).isEqualTo("SCORING_VERSION_V1");
        assertThat(created.getValue().getItemSetHash()).isEqualTo("rules");
    }

    @Test
    void oversizedBidTextInAsync_failsTaskWithFriendlyMessage() throws Exception {
        // 异步段文本量超限（FR-004）：任务 FAILED + OversizedBidTextException.MESSAGE 原文，不产生部分得分
        ScoreParseTask task = scoringTask();
        when(taskRepository.findByTaskId(anyString())).thenReturn(Optional.of(task));
        when(versions.lockedSnapshot(PROJECT_ID)).thenReturn(snapshot(List.of(item(10L, "资质", "具备 CMMI 5 级认证证书", "10", "OBJECTIVE"))));
        when(bidFileLoader.openScoring(eq(PROJECT_ID), anyString(), org.mockito.ArgumentMatchers.anyLong()))
                .thenThrow(new OversizedBidTextException());

        service.executeScoringAsync("task-uuid");

        verify(versions).finish(eq(task), org.mockito.ArgumentMatchers.isNull(), eq(OversizedBidTextException.MESSAGE));
        verify(progressService).clearProgress("task-uuid");
        org.mockito.Mockito.verifyNoInteractions(scoreAnalyzer);
    }

    @Test
    void scoreItemsNotReady_rejectsWithSemantic() {
        when(versions.lockedSnapshot(PROJECT_ID)).thenReturn(snapshot(List.of()));

        assertThatThrownBy(() -> service.triggerScoring(PROJECT_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SCORE_ITEMS_NOT_READY");
        verify(stateService, never()).createTask(anyString(), any(), anyString(), any(), any(), anyString());
    }

    @Test
    void scoringTaskInProgress_rejectsWith409Semantic() {
        ScoreParseTask existing = ScoreParseTask.builder()
                .id(9L).taskId("existing-task").projectId(PROJECT_ID)
                .taskType("SCORING").status("PROCESSING").build();
        when(versions.lockedTasks(PROJECT_ID, List.of("PENDING", "PROCESSING")))
                .thenReturn(List.of(existing));
        mockBidDocumentPresent();
        mockItemsPresent();

        assertThatThrownBy(() -> service.triggerScoring(PROJECT_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TASK_IN_PROGRESS");
        verify(stateService, never()).createTask(anyString(), any(), anyString(), any(), any(), anyString());
    }

    @Test
    void autoScoringFollowsInProgressTask() {
        ScoreParseTask existing = ScoreParseTask.builder()
                .id(9L).taskId("existing-task").projectId(PROJECT_ID)
                .taskType("SCORING").status("PROCESSING").build();
        when(versions.lockedTasks(PROJECT_ID, List.of("PENDING", "PROCESSING")))
                .thenReturn(List.of(existing));
        mockBidDocumentPresent();
        mockItemsPresent();

        ScoreParseTriggerDTO dto = service.triggerScoring(
                PROJECT_ID, new com.xiyu.bid.scoreparse.dto.ScoreScoringCommand("AUTO", "ALL", List.of()));

        assertThat(dto.taskId()).isEqualTo("existing-task");
        assertThat(dto.status()).isEqualTo("PROCESSING");
        verify(stateService, never()).createTask(anyString(), any(), anyString(), any(), any(), anyString());
    }

    @Test
    void validTrigger_scoresObjectiveAndSubjectiveAndPersists() throws Exception {
        mockNoActiveScoringTask();
        mockBidDocumentPresent();
        ScoreItem objective = item(10L, "资质", "具备 CMMI 5 级认证证书", "10", "OBJECTIVE");
        ScoreItem subjective = item(11L, "方案", "技术方案完善、可行", "15", "SUBJECTIVE");
        when(versions.lockedSnapshot(PROJECT_ID)).thenReturn(snapshot(List.of(objective, subjective)));
        mockTaskAndBidDocumentText();

        when(scoreAnalyzer.assessObjective(eq("具备 CMMI 5 级认证证书"),
                eq(new BigDecimal("10")), anyString(), anyList(), any(com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiRequestBudget.class)))
                .thenReturn(assessment(8.0, 80, "标书已提供 CMMI 5 证书", "投标文件全文内容CMMI5认证证书", false, null, "无"));
        when(scoreAnalyzer.assessSubjective(eq("技术方案完善、可行"), anyString(), any(com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiRequestBudget.class)))
                .thenReturn(assessment(null, null, null, null, false, null, "建议补充实施计划细化到人天"));

        ScoreParseTriggerDTO result = service.triggerScoring(PROJECT_ID);

        assertThat(result.status()).isEqualTo("PENDING");
        ArgumentCaptor<List<ScoreResult>> captor = ArgumentCaptor.captor();
        verify(versions).finish(any(), captor.capture(), org.mockito.ArgumentMatchers.isNull());
        List<ScoreResult> saved = captor.getValue();
        assertThat(saved).hasSize(2);

        ScoreResult objectiveResult = saved.stream()
                .filter(r -> r.getScoreItemId().equals(10L)).findFirst().orElseThrow();
        assertThat(objectiveResult.getActualScore()).isEqualByComparingTo("8");
        assertThat(objectiveResult.getMatchRatio()).isEqualTo(80);
        assertThat(objectiveResult.getQuote()).isEqualTo("投标文件全文内容CMMI5认证证书");
        assertThat(objectiveResult.getStatusStage2()).isEqualTo("PENDING");

        ScoreResult subjectiveResult = saved.stream()
                .filter(r -> r.getScoreItemId().equals(11L)).findFirst().orElseThrow();
        assertThat(subjectiveResult.getActualScore()).isNull();
        assertThat(subjectiveResult.getMatchRatio()).isNull();
        assertThat(subjectiveResult.getSuggestion()).isEqualTo("建议补充实施计划细化到人天");
        assertThat(subjectiveResult.getStatusStage2()).isEqualTo("PENDING");
        assertThat(subjectiveResult.getReuseKind()).isEqualTo("SUBJECTIVE");
        verify(versions).finish(any(), anyList(), org.mockito.ArgumentMatchers.isNull());
        ArgumentCaptor<ScoreParseTask> taskCaptor = ArgumentCaptor.forClass(ScoreParseTask.class);
        verify(taskRepository, org.mockito.Mockito.atLeastOnce()).save(taskCaptor.capture());
        assertThat(taskCaptor.getAllValues().stream().anyMatch(t -> t.getBidContentHash() != null
                && t.getItemSetHash() != null)).isTrue();
    }

    @Test
    void outOfRangeScore_nullifiedAndPending() throws Exception {
        mockNoActiveScoringTask();
        mockBidDocumentPresent();
        ScoreItem objective = item(10L, "资质", "具备 CMMI 5 级认证证书", "10", "OBJECTIVE");
        when(versions.lockedSnapshot(PROJECT_ID)).thenReturn(snapshot(List.of(objective)));
        mockTaskAndBidDocumentText();
        when(scoreAnalyzer.assessObjective(anyString(), any(BigDecimal.class), anyString(), anyList(), any(com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiRequestBudget.class)))
                .thenReturn(assessment(15.0, 100, "模型越界输出", "引用", false, null, null));

        service.triggerScoring(PROJECT_ID);

        ArgumentCaptor<List<ScoreResult>> captor = ArgumentCaptor.captor();
        verify(versions).finish(any(), captor.capture(), org.mockito.ArgumentMatchers.isNull());
        ScoreResult result = captor.getValue().get(0);
        assertThat(result.getActualScore()).isNull();
        assertThat(result.getStatusStage2()).isEqualTo("PENDING");
    }

    @Test
    void quoteMissing_requiresEvidenceBeforePublishingZeroScore() throws Exception {
        mockNoActiveScoringTask();
        mockBidDocumentPresent();
        ScoreItem objective = item(10L, "资质", "具备 ISO27001 认证", "5", "OBJECTIVE");
        when(versions.lockedSnapshot(PROJECT_ID)).thenReturn(snapshot(List.of(objective)));
        mockTaskAndBidDocumentText();
        when(scoreAnalyzer.assessObjective(anyString(), any(BigDecimal.class), anyString(), anyList(), any(com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiRequestBudget.class)))
                .thenReturn(assessment(0.0, 0, null, "模型违规引用", true, "标书未响应本项", "补充证书"));

        service.triggerScoring(PROJECT_ID);

        ArgumentCaptor<List<ScoreResult>> captor = ArgumentCaptor.captor();
        verify(versions).finish(any(), captor.capture(), org.mockito.ArgumentMatchers.isNull());
        ScoreResult result = captor.getValue().get(0);
        assertThat(result.getActualScore()).isNull();
        assertThat(result.getQuote()).isNull();
        assertThat(result.getStatusStage2()).isEqualTo("PENDING");
    }

    @Test
    void bidDocumentLoadFailure_marksFailedAndPreservesOldResults() throws Exception {
        ScoreParseTask task = scoringTask();
        when(taskRepository.findByTaskId(anyString())).thenReturn(Optional.of(task));
        when(versions.lockedSnapshot(PROJECT_ID)).thenReturn(snapshot(List.of(item(10L, "资质", "具备 CMMI 5 级认证证书", "10", "OBJECTIVE"))));
        when(bidFileLoader.openScoring(eq(PROJECT_ID), anyString(), org.mockito.ArgumentMatchers.anyLong()))
                .thenThrow(new IllegalStateException("投标文件加载失败: " + BID_FILE_URL));

        service.executeScoringAsync("task-uuid");

        verify(versions).finish(eq(task), org.mockito.ArgumentMatchers.isNull(), eq("投标文件加载失败: " + BID_FILE_URL));
        org.mockito.Mockito.verifyNoInteractions(scoreAnalyzer);
    }

    @Test
    void ocrFailure_keepsActionableCauseAndPreservesOldResults() throws Exception {
        ScoreParseTask task = scoringTask();
        when(taskRepository.findByTaskId(anyString())).thenReturn(Optional.of(task));
        when(versions.lockedSnapshot(PROJECT_ID)).thenReturn(snapshot(List.of(item(10L, "销售额", "销售额", "15", "OBJECTIVE"))));
        var failure = new BidTextExtractionException("BID_OCR_TIMEOUT");
        when(bidFileLoader.openScoring(eq(PROJECT_ID), anyString(), org.mockito.ArgumentMatchers.anyLong())).thenThrow(failure);
        service.executeScoringAsync("task-uuid");
        verify(versions).finish(eq(task), org.mockito.ArgumentMatchers.isNull(), eq(failure.getMessage()));
        var report = new ScoringRunReportCodec().scoring(task.getRunSummary());
        assertThat(report.path("reasonCode").asText()).isEqualTo(failure.getMessage());
        org.mockito.Mockito.verifyNoInteractions(scoreAnalyzer);
    }

    @Test
    void manualSameBidAndItemHash_createsRealReassessment() throws Exception {
        mockNoActiveScoringTask();
        mockBidDocumentPresent();
        ScoreItem objective = item(10L, "资质", "具备 CMMI 5 级认证证书", "10", "OBJECTIVE");
        when(versions.lockedSnapshot(PROJECT_ID)).thenReturn(snapshot(List.of(objective)));
        mockTaskAndBidDocumentText();
        var oldRow = ScoreResult.builder().scoreItemId(10L).scoringTaskId(4L)
                .actualScore(new BigDecimal("7")).quote("投标文件全文内容CMMI5认证证书").statusStage2("PENDING").build();
        var identity = new com.xiyu.bid.scoreparse.domain.ScoreResultReusePolicy.Identity("async-hash", "rules",
                ScoringRunReportCodec.ENGINE, com.xiyu.bid.scoreparse.infrastructure.openai.ScoringModelPolicy.fingerprint(configuration()));
        var source = new com.xiyu.bid.scoreparse.domain.ScoreResultReusePolicy.Source(identity, "COMPLETE", "SCORED", 4L);
        org.mockito.Mockito.doReturn(new ScoreScoringVersionService.Snapshot(List.of(objective), "rules",
                java.util.Map.of(10L, oldRow), java.util.Map.of(10L, oldRow), java.util.Map.of(), java.util.Map.of(10L, source), java.util.Map.of())).when(versions).capture(PROJECT_ID);
        when(scoreAnalyzer.assessObjective(anyString(),any(),anyString(), anyList(), any(com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiRequestBudget.class)))
                .thenReturn(assessment(5d,null,"evidence","quote",false,null,null));

        ScoreParseTriggerDTO dto = service.triggerScoring(PROJECT_ID);

        assertThat(dto.status()).isEqualTo("PENDING");
        assertThat(dto.outcome()).isNull();
        verify(scoreAnalyzer).assessObjective(anyString(), any(), anyString(), anyList(), any(com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiRequestBudget.class));
        verify(scoreAnalyzer, never()).assessSubjective(anyString(), anyString(), any(com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiRequestBudget.class));
    }

    @Test
    void loadedImages_passedToAssessorWithinBudget() throws Exception {
        mockNoActiveScoringTask(); mockBidDocumentPresent(); mockItemsPresent();
        var task = scoringTask();
        when(stateService.createTask(anyString(), any(), anyString(), any(), any(), anyString())).thenReturn(task);
        when(taskRepository.findByTaskId(anyString())).thenReturn(Optional.of(task));
        var image = new com.xiyu.bid.scoreparse.domain.DocumentImage(0, "image/png", "资质", 1, 640, 480, "aaaa");
        var images = org.mockito.Mockito.mock(SidecarImageExtractor.class);
        when(images.renderPages(any(byte[].class), anyString(), eq(java.util.Set.of(1)), org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(new SidecarImageExtraction(List.of(image), false));
        mockScoringFile(images);
        when(scoreAnalyzer.assessObjective(anyString(), any(), anyString(), anyList(),
                any(com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiRequestBudget.class)))
                .thenAnswer(call -> ((List<?>) call.getArgument(3)).isEmpty()
                        ? assessment(null, null, null, null, false, null, null)
                        : assessment(8d, null, "证书扫描件图示满足", "投标文件全文内容CMMI5认证证书", false, null, null));
        service.triggerScoring(PROJECT_ID);
        ArgumentCaptor<List<com.xiyu.bid.scoreparse.domain.DocumentImage>> captor = ArgumentCaptor.captor();
        verify(scoreAnalyzer, org.mockito.Mockito.times(2)).assessObjective(anyString(), any(), anyString(), captor.capture(),
                any(com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiRequestBudget.class));
        assertThat(captor.getAllValues()).containsExactly(List.of(), List.of(image));
        verify(versions).finish(any(), anyList(), org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void llmAssessFailure_marksFailedAndCannotPublishScore() throws Exception {
        mockNoActiveScoringTask();
        mockBidDocumentPresent();
        ScoreItem objective = item(10L, "资质", "具备 CMMI 5 级认证证书", "10", "OBJECTIVE");
        when(versions.lockedSnapshot(PROJECT_ID)).thenReturn(snapshot(List.of(objective)));
        mockTaskAndBidDocumentText();
        when(scoreAnalyzer.assessObjective(anyString(), any(BigDecimal.class), anyString(), anyList(), any(com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiRequestBudget.class)))
                .thenThrow(new RuntimeException("LLM 超时"));

        service.triggerScoring(PROJECT_ID);

        ArgumentCaptor<List<ScoreResult>> rows = ArgumentCaptor.captor();
        verify(versions).finish(any(), rows.capture(), eq("SCORING_ITEM_FAILED"));
        assertThat(rows.getValue()).singleElement().satisfies(row -> {
            assertThat(row.getActualScore()).isNull();
            assertThat(row.getReuseKind()).isEqualTo("FAILED");
        });
    }

    @Test
    void missingVersionService_failsConstruction() {
        assertThatThrownBy(() -> new ScoreScoringAppService(taskRepository, itemRepository, stateService,
                progressService, projectAccessScopeService, scoreAnalyzer, bidFileLoader, null, 3, 5242880L))
                .isInstanceOf(NullPointerException.class).hasMessage("versions");
    }

    @Test
    void versionStartFailure_preservesOldResultsAndClearsProgress() {
        var task = scoringTask();
        org.mockito.Mockito.doThrow(new IllegalStateException("database failure")).when(versions).start(task.getTaskId());
        when(taskRepository.findByTaskId(task.getTaskId())).thenReturn(Optional.of(task));
        service.executeScoringAsync(task.getTaskId());
        verify(versions).finish(eq(task), org.mockito.ArgumentMatchers.isNull(), eq("打分失败，请重试"));
        verify(progressService).clearProgress(task.getTaskId());
    }

    @Test
    void resultRead_delegatesToVersionBoundary() {
        service.getResults(PROJECT_ID);
        verify(projectAccessScopeService).assertCurrentUserCanAccessProject(PROJECT_ID);
        verify(versions).read(PROJECT_ID);
        org.mockito.Mockito.verifyNoInteractions(itemRepository);
    }

    @Test void statusWithoutTaskIsAnExplicitEmptyRead() {
        assertThat(service.getStatus(PROJECT_ID)).isNull();
        verify(stateService,never()).createTask(anyString(),any(),anyString(),any(),any(),anyString());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "BID_OCR_UNAVAILABLE,false,OCR 识别不可用",
            "SOURCE_DECODE_FAILED,false,源文件解码失败",
            "BID_OCR_TIMEOUT,false,OCR 识别超时",
            "EMF_UNSUPPORTED_RECORD,false,EMF 矢量图",
            "BID_OCR_UNAVAILABLE,true,OCR 识别不可用",
            "SOURCE_DECODE_FAILED,true,源文件解码失败",
            "NONE,false,无提取故障"
    })
    void coverageGapFailsRunOnlyWithoutTrustedNewResult(String reason, boolean scored, String expectedReason)
            throws Exception {
        var task = scoringTask();
        task.setStage("ITEMS");task.setTriggerSource("MANUAL");task.setItemSetHash("rules");
        task.setCreatedAt(java.time.LocalDateTime.now());
        var codec = new ScoringRunReportCodec();
        task.setRunSummary(codec.request(new com.xiyu.bid.scoreparse.dto.ScoreScoringCommand("MANUAL", "ITEMS", List.of(10L,11L))));
        var certificate = item(10L, "资质证书", "具有ISO9001质量管理体系认证证书得10分", "10", "OBJECTIVE");
        var pending = item(11L, "业绩", "每提供一份合同得2分，最多10分", "10", "OBJECTIVE");
        var retained = item(12L, "其他资质", "其他资质评分", "10", "OBJECTIVE");
        var oldRow = ScoreResult.builder().scoreItemId(12L).scoringTaskId(4L).actualScore(new BigDecimal("7"))
                .statusStage2("PENDING").quote("此前已核验的证据").build();
        var configuration = new com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiBidAgentRequestConfig(
                "synthetic", "http://provider.invalid/v1", "synthetic", java.time.Duration.ofSeconds(1),
                com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiBidAgentApiStyle.CHAT_COMPLETIONS);
        var identity = new com.xiyu.bid.scoreparse.domain.ScoreResultReusePolicy.Identity("file-hash", "rules", ScoringRunReportCodec.ENGINE,
                com.xiyu.bid.scoreparse.infrastructure.openai.ScoringModelPolicy.fingerprint(configuration));
        var source = new com.xiyu.bid.scoreparse.domain.ScoreResultReusePolicy.Source(identity, "COMPLETE", "SCORED", 4L);
        var snapshot = new ScoreScoringVersionService.Snapshot(List.of(certificate, pending, retained), "rules",
                java.util.Map.of(12L,oldRow), java.util.Map.of(12L,oldRow), java.util.Map.of(), java.util.Map.of(12L,source), java.util.Map.of());
        var staleSnapshot = new ScoreScoringVersionService.Snapshot(List.of(certificate, pending, retained), "rules",
                java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of());
        org.mockito.Mockito.doReturn(staleSnapshot).when(versions).start(task.getTaskId());
        org.mockito.Mockito.doReturn(snapshot).when(versions).capture(PROJECT_ID);
        when(versions.recordInput(task,77L)).thenReturn(true);
        when(taskRepository.findByTaskId(task.getTaskId())).thenReturn(Optional.of(task));
        boolean technical = !"NONE".equals(reason), timeout = "BID_OCR_TIMEOUT".equals(reason);
        var bad = List.<java.util.Map<String,Object>>of(java.util.Map.of("pageNumber", 2, "reason", reason));
        var empty = List.<java.util.Map<String,Object>>of();
        var report = new com.xiyu.bid.scoreparse.domain.ScoringExtractionReport(1, "scoring-ocr-v2",
                technical ? "PARTIAL" : "COMPLETE", 2, timeout ? 1 : 2, 1, timeout ? 1 : 0,
                technical && !timeout ? 1 : 0, technical ? 0 : 1,
                timeout ? bad : empty, technical && !timeout ? bad : empty, technical ? empty : bad, 0);
        var document = new com.xiyu.bid.scoreparse.domain.ScoringDocument("[第 1 页]\nISO9001质量管理体系认证证书",
                List.of(java.util.Map.of("pageNumber", 1), java.util.Map.of("pageNumber", 2)), report);
        var texts = org.mockito.Mockito.mock(SidecarStreamTextExtractor.class);
        when(texts.extractScoringDocument(any(byte[].class), anyString(), org.mockito.ArgumentMatchers.anyLong())).thenReturn(document);
        var released = new java.util.concurrent.atomic.AtomicBoolean();
        var session = new ScoringFileSession(77L, "file-hash", new LoadedBidFile.InMemory(new byte[]{1}, "bid.pdf", "pdf"),
                texts, null, System.currentTimeMillis()+30000, () -> released.set(true));
        when(bidFileLoader.openScoring(eq(PROJECT_ID), eq(task.getTaskId()), org.mockito.ArgumentMatchers.anyLong())).thenReturn(session);
        when(scoreAnalyzer.scoringConfiguration()).thenReturn(configuration);
        when(scoreAnalyzer.assessObjective(anyString(), any(), anyString(), anyList(),
                any(com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiRequestBudget.class))).thenAnswer(invocation ->
                scored && certificate.getDetail().equals(invocation.getArgument(0))
                        ? assessment(10d, null, "已核验证书", "ISO9001质量管理体系认证证书", false, null, null)
                        : assessment(null, null, "依据不足", null, false, "需人工核验", null));

        service.executeScoringAsync(task.getTaskId());

        ArgumentCaptor<List<ScoreResult>> replacement = ArgumentCaptor.captor();
        ArgumentCaptor<String> failure = ArgumentCaptor.captor();
        verify(versions).finish(eq(task), replacement.capture(), failure.capture());
        if (technical && !scored) {
            assertThat(failure.getValue()).contains("处内容无法解析").contains(expectedReason);
            assertThat(replacement.getValue()).isNull();
        } else {
            assertThat(failure.getValue()).isNull();
            assertThat(replacement.getValue()).hasSize(3);
            assertThat(replacement.getValue().get(2).getActualScore()).isEqualByComparingTo("7");
            assertThat(replacement.getValue().get(2).getReuseKind()).isEqualTo("REUSED");
            if (technical) assertThat(replacement.getValue().get(1).getMissedReason()).contains("MODEL_QUOTE_MISSING");
            if (scored) assertThat(replacement.getValue().getFirst().getActualScore()).isEqualByComparingTo("10");
        }
        verify(versions).capture(PROJECT_ID);
        var summary = codec.scoring(task.getRunSummary());
        assertThat(summary.path("input").path("extraction").path("failedUnitCount").asLong()).isEqualTo(report.failedUnitCount());
        assertThat(summary.path("taskQuality").asText()).isEqualTo(technical ? "PARTIAL" : "NEEDS_REVIEW");
        assertThat(summary.path("items").get(1).path("outcome").asText()).isEqualTo(technical ? "FAILED" : "NEEDS_REVIEW");
        assertThat(summary.path("items").get(1).path("reasonCode").asText()).isEqualTo(technical ? "SCORING_EXTRACTION_FAILED" : "EVIDENCE_INSUFFICIENT");
        assertThat(summary.path("items").get(2).path("originTaskId").asLong()).isEqualTo(4L);
        if (scored) {
            var published = replacement.getValue().stream().collect(java.util.stream.Collectors.toMap(ScoreResult::getScoreItemId, row -> row));
            var sources = snapshot.items().stream().collect(java.util.stream.Collectors.toMap(ScoreItem::getId, row -> codec.source(task, row.getId())));
            var result = new ScoreScoringResultsAssembler().assemble(snapshot.items(), published, task, sources);
            assertThat(result.failed()).isEqualTo(1);
            assertThat(result.objectiveScored()).isEqualTo(1);
            assertThat(result.objectivePending()).isEqualTo(1);
            assertThat(result.qualityStatus()).isEqualTo("PARTIAL");
        }
        assertThat(released).isTrue();
        verify(progressService).clearProgress(task.getTaskId());
    }

    private void mockNoActiveScoringTask() {
        when(versions.lockedTasks(PROJECT_ID, List.of("PENDING", "PROCESSING")))
                .thenReturn(List.of());
    }

    private void mockBidDocumentPresent() {
        ProjectDocument bidDoc = ProjectDocument.builder()
                .id(77L).projectId(PROJECT_ID).name("投标文件.pdf")
                .documentCategory("BID").fileUrl(BID_FILE_URL).build();
        when(bidFileLoader.findBidDocuments(PROJECT_ID)).thenReturn(List.of(bidDoc));
    }

    private void mockItemsPresent() {
        when(versions.lockedSnapshot(PROJECT_ID)).thenReturn(snapshot(List.of(item(10L, "资质", "具备 CMMI 5 级认证证书", "10", "OBJECTIVE"))));
    }

    private void mockTaskAndBidDocumentText() throws Exception {
        ScoreParseTask task = scoringTask();
        when(stateService.createTask(anyString(), any(), anyString(), any(), any(), anyString())).thenReturn(task);
        when(taskRepository.findByTaskId(anyString())).thenReturn(Optional.of(task));
        mockScoringFile(null);
    }

    private ScoreParseTask scoringTask() {
        ScoreParseTask task = ScoreParseTask.builder()
                .id(5L).taskId("task-uuid").projectId(PROJECT_ID)
                .taskType("SCORING").status("PENDING").build();
        task.setStage("ALL"); task.setTriggerSource("MANUAL"); task.setItemSetHash("rules");
        task.setCreatedAt(java.time.LocalDateTime.now());
        task.setRunSummary(new ScoringRunReportCodec().request(com.xiyu.bid.scoreparse.dto.ScoreScoringCommand.defaults()));
        return task;
    }

    private ScoreItem item(Long id, String dim, String detail, String weight, String scoreType) {
        return ScoreItem.builder()
                .id(id).projectId(PROJECT_ID).parseTaskId(1L)
                .itemIndex(1).code("A1").dim(dim).detail(detail)
                .weight(new BigDecimal(weight)).scoreType(scoreType)
                .statusStage1("PENDING").build();
    }

    private ScoreScoringVersionService.Snapshot snapshot(List<ScoreItem> items) {
        return new ScoreScoringVersionService.Snapshot(items, "rules", java.util.Map.of(), java.util.Map.of(),
                java.util.Map.of(), java.util.Map.of(), java.util.Map.of());
    }

    private com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiBidAgentRequestConfig configuration() {
        return new com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiBidAgentRequestConfig(
                "synthetic", "http://provider.invalid/v1", "synthetic", java.time.Duration.ofSeconds(1),
                com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiBidAgentApiStyle.CHAT_COMPLETIONS);
    }

    private void mockScoringFile(SidecarImageExtractor images) throws Exception {
        var texts = org.mockito.Mockito.mock(SidecarStreamTextExtractor.class);
        var report = new com.xiyu.bid.scoreparse.domain.ScoringExtractionReport(1, "scoring-ocr-v2", "COMPLETE",
                1, 1, 0, 0, 0, 0, List.of(), List.of(), List.of(), 0);
        var document = new com.xiyu.bid.scoreparse.domain.ScoringDocument(BID_TEXT,
                List.of(java.util.Map.of("pageNumber", 1)), report);
        when(texts.extractScoringDocument(any(byte[].class), anyString(), org.mockito.ArgumentMatchers.anyLong())).thenReturn(document);
        var session = new ScoringFileSession(77L, "async-hash", new LoadedBidFile.InMemory(new byte[]{1}, "bid.pdf", "pdf"),
                texts, images, System.currentTimeMillis()+30000, () -> {});
        when(bidFileLoader.openScoring(eq(PROJECT_ID), anyString(), org.mockito.ArgumentMatchers.anyLong())).thenReturn(session);
    }

    private ScoreAssessmentOutput assessment(Double actualScore, Integer matchRatio, String evidence,
                                              String quote, Boolean quoteMissing,
                                              String missedReason, String suggestion) {
        ScoreAssessmentOutput output = new ScoreAssessmentOutput();
        output.actualScore = actualScore;
        output.matchRatio = matchRatio;
        output.evidence = evidence;
        output.quote = quote;
        output.quoteMissing = quoteMissing;
        output.missedReason = missedReason;
        output.suggestion = suggestion;
        return output;
    }
}
