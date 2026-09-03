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
import com.xiyu.bid.scoreparse.dto.ScoreParseItemsDTO;
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
import java.time.LocalDateTime;
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
    private ObjectProvider<LlmTableScoreExtractor> llmTableScoreExtractorProvider;
    @Mock
    private ObjectProvider<ScoreTableSchemaMapper> schemaMapperProvider;

    private ScoreParseAppService service;

    @BeforeEach
    void setUp() {
        // spec 057：治理链入 PostProcessor——真实领域策略+真编排器（executor 缺席即 fail-open，
        // 归并裁决语义由 OrchestratorTest / PostProcessorTest 各自专项覆盖）
        lenient().when(mergeExecutorProvider.getIfAvailable()).thenReturn(null);
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
                postProcessor, llmTableScoreExtractorProvider, schemaMapperProvider);
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
    void parse_reviewRequired_stillPersistsItemsWithReviewFlag() {
        // spec 059 修复：REVIEW_REQUIRED 不再 all-or-nothing 丢弃整批结果。
        // 权重合计 50+30=80 不符 100 → 决策 REVIEW_REQUIRED，但解析产出的有效项仍须落库展示，
        // 复核标记保留在 task 上供前端提示；禁止静默丢弃（1358 漏识别事故根因）。
        ScoreParseTask task = mockParseFlowReturning(List.of(
                candidate("A1", "资质", "具备 CMMI 认证", "50"),
                candidate("A2", "业绩", "近三年业绩", "30")));

        service.executeParseAsync(TASK_ID);

        verify(stateService).markCompleted(TASK_ID);
        assertThat(capturedPersistedCandidates().getValue())
                .extracting(ScoreCandidate::code)
                .containsExactly("A1", "A2");
        verify(estimatedScoreService).estimateForProject(PROJECT_ID);
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
                mockPostProcessor, llmTableScoreExtractorProvider, schemaMapperProvider);
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

    private List<ScoreCandidate> defaultCandidates() {
        return List.of(
                candidate("A1", "资质", "具备 CMMI 5 级认证证书", "60"),
                candidate("A2", "业绩", "近三年类似项目业绩不少于 3 个", "40"));
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
        when(initiationTenderTextResolver.resolveIntake(PROJECT_ID))
                .thenReturn(TenderIntake.unavailable(InitiationTenderTextResolver.NO_TENDER_MESSAGE));

        assertThatThrownBy(() -> service.triggerParse(PROJECT_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(InitiationTenderTextResolver.NO_TENDER_MESSAGE);
        verify(stateService).createTask(anyString(), eq(PROJECT_ID), eq("PARSE"), isNull(), isNull(), eq("MANUAL"));
        verify(stateService).failTask(anyString(), eq(InitiationTenderTextResolver.NO_TENDER_MESSAGE));
    }

    @Test
    void triggerParse_rejectsOversizedWithoutSnapshot() {
        when(initiationTenderTextResolver.resolveIntake(PROJECT_ID))
                .thenReturn(TenderIntake.unavailable(BoundedHttpDownloader.TOO_LARGE_MESSAGE));

        assertThatThrownBy(() -> service.triggerParse(PROJECT_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(BoundedHttpDownloader.TOO_LARGE_MESSAGE);
        verify(stateService).failTask(anyString(), eq(BoundedHttpDownloader.TOO_LARGE_MESSAGE));
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
                | 1 | 商务部分 | 财务状况良好得30分 | 30 |
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
        assertThat(persisted.get(0).weight()).isEqualByComparingTo(new BigDecimal("30"));
        assertThat(persisted.get(1).weight()).isEqualByComparingTo(new BigDecimal("70"));
    }

    @Test
    void executeParse_whenSchemaMapperThrows_fallsBackToHeuristic() {
        String tableText = """
                | 序号 | 评分项目 | 评分标准 | 分值 |
                | --- | --- | --- | --- |
                | 1 | 商务部分 | 财务状况良好得30分 | 30 |
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
        assertThat(persisted.get(0).weight()).isEqualByComparingTo(new BigDecimal("30"));
        assertThat(persisted.get(1).weight()).isEqualByComparingTo(new BigDecimal("70"));
    }

    private void mockParseFlowWithText(String fullText) {
        ScoreParseTask task = ScoreParseTask.builder()
                .id(5L).taskId(TASK_ID).projectId(PROJECT_ID).taskType("PARSE").status("PENDING").build();
        when(taskRepository.findByTaskId(TASK_ID)).thenReturn(Optional.of(task));
        when(initiationTenderTextResolver.resolveIntake(PROJECT_ID))
                .thenReturn(TenderIntake.found(new TenderTextSource("tender.pdf", "doc-insight://t/1", fullText)));
        lenient().when(scoreAnalyzer.recallCandidates(anyString(), isNull(), any()))
                .thenReturn(new RecallOutcome(List.of(), 0));
    }
}
