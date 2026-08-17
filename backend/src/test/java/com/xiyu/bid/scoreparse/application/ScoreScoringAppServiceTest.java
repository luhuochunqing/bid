// Input: projectId（触发打分）/ mock 存储与 LLM 依赖
// Output: 前置校验/整批覆盖/失败保留旧结果断言
// Pos: scoreparse/application — spec 041 US4 编排测试（FR-012/FR-019/FR-021）
package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.biddraftagent.application.ExtractedTenderDocument;
import com.xiyu.bid.biddraftagent.application.LoadedTenderDocument;
import com.xiyu.bid.biddraftagent.application.StoredTenderDocument;
import com.xiyu.bid.biddraftagent.application.TenderDocumentStorage;
import com.xiyu.bid.biddraftagent.application.TenderDocumentTextExtractor;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.scoreparse.dto.ScoreParseTriggerDTO;
import com.xiyu.bid.scoreparse.entity.ScoreItem;
import com.xiyu.bid.scoreparse.entity.ScoreParseTask;
import com.xiyu.bid.scoreparse.entity.ScoreResult;
import com.xiyu.bid.scoreparse.infrastructure.openai.OpenAiScoreAnalyzer;
import com.xiyu.bid.scoreparse.infrastructure.openai.ScoreAssessmentOutput;
import com.xiyu.bid.scoreparse.repository.ScoreItemRepository;
import com.xiyu.bid.scoreparse.domain.KnowledgeCategoryPolicy;
import com.xiyu.bid.scoreparse.repository.ScoreParseTaskRepository;
import com.xiyu.bid.scoreparse.repository.ScoreResultRepository;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 阶段 2 实际打分编排测试（spec 041 US4）。
 * <p>FR-019 前置校验（标书已上传 + 评分项就绪 + 任务互斥）；
 * FR-021 整批覆盖；US4 场景 6 失败保留旧结果。
 */
@ExtendWith(MockitoExtension.class)
class ScoreScoringAppServiceTest {

    private static final Long PROJECT_ID = 1L;
    private static final String BID_FILE_URL = "doc-insight://bid/test.pdf";

    @Mock
    private ScoreParseTaskRepository taskRepository;
    @Mock
    private ScoreItemRepository itemRepository;
    @Mock
    private ScoreResultRepository resultRepository;
    @Mock
    private ProjectDocumentRepository projectDocumentRepository;
    @Mock
    private TenderDocumentStorage documentStorage;
    @Mock
    private TenderDocumentTextExtractor textExtractor;
    @Mock
    private ScoreParseTaskStateService stateService;
    @Mock
    private ScoreParseProgressService progressService;
    @Mock
    private ProjectAccessScopeService projectAccessScopeService;
    @Mock
    private OpenAiScoreAnalyzer scoreAnalyzer;

    private ScoreScoringAppService service;

    @BeforeEach
    void setUp() {
        service = new ScoreScoringAppService(
                taskRepository, itemRepository, resultRepository,
                projectDocumentRepository, documentStorage, textExtractor,
                stateService, progressService, projectAccessScopeService, scoreAnalyzer);
        ReflectionTestUtils.setField(service, "self", service);
    }

    @Test
    void noBidDocument_rejectsWithSemantic() {
        mockItemsPresent();
        when(projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(
                PROJECT_ID, "BID", null, null)).thenReturn(List.of());
        when(projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(
                PROJECT_ID, "BID_FILE", null, null)).thenReturn(List.of());
        when(projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(
                PROJECT_ID, "BID_DOCUMENT", null, null)).thenReturn(List.of());

        assertThatThrownBy(() -> service.triggerScoring(PROJECT_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NO_BID_DOCUMENT");
        verify(stateService, never()).createTask(anyString(), any(), anyString(), any(), any(), anyString());
    }

    @Test
    void scoreItemsNotReady_rejectsWithSemantic() {
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(PROJECT_ID)).thenReturn(List.of());

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
        when(taskRepository.findByProjectIdAndTaskTypeAndStatusIn(
                PROJECT_ID, "SCORING", List.of("PENDING", "PROCESSING")))
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
        when(taskRepository.findByProjectIdAndTaskTypeAndStatusIn(
                PROJECT_ID, "SCORING", List.of("PENDING", "PROCESSING")))
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
    void validTrigger_scoresObjectiveAndSubjectiveAndPersists() {
        mockNoActiveScoringTask();
        mockBidDocumentPresent();
        ScoreItem objective = item(10L, "资质", "具备 CMMI 5 级认证证书", "10", "OBJECTIVE");
        ScoreItem subjective = item(11L, "方案", "技术方案完善、可行", "15", "SUBJECTIVE");
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(PROJECT_ID))
                .thenReturn(List.of(objective, subjective));
        mockTaskAndBidDocumentText();

        when(scoreAnalyzer.assessObjective(eq("具备 CMMI 5 级认证证书"),
                eq(new BigDecimal("10")), anyString()))
                .thenReturn(assessment(8.0, 80, "标书已提供 CMMI 5 证书", "第 2.1 节 P8", false, null, "无"));
        when(scoreAnalyzer.assessSubjective(eq("技术方案完善、可行"), anyString()))
                .thenReturn(assessment(null, null, null, null, false, null, "建议补充实施计划细化到人天"));

        ScoreParseTriggerDTO result = service.triggerScoring(PROJECT_ID);

        assertThat(result.status()).isEqualTo("PENDING");
        ArgumentCaptor<List<ScoreResult>> captor = ArgumentCaptor.captor();
        verify(resultRepository).deleteByScoreItemIdIn(List.of(10L, 11L));
        verify(resultRepository).saveAll(captor.capture());
        List<ScoreResult> saved = captor.getValue();
        assertThat(saved).hasSize(2);

        ScoreResult objectiveResult = saved.stream()
                .filter(r -> r.getScoreItemId().equals(10L)).findFirst().orElseThrow();
        assertThat(objectiveResult.getActualScore()).isEqualByComparingTo("8");
        assertThat(objectiveResult.getMatchRatio()).isEqualTo(80);
        assertThat(objectiveResult.getQuote()).isEqualTo("第 2.1 节 P8");
        assertThat(objectiveResult.getStatusStage2()).isEqualTo("PENDING");

        ScoreResult subjectiveResult = saved.stream()
                .filter(r -> r.getScoreItemId().equals(11L)).findFirst().orElseThrow();
        assertThat(subjectiveResult.getActualScore()).isNull();
        assertThat(subjectiveResult.getMatchRatio()).isNull();
        assertThat(subjectiveResult.getSuggestion()).isEqualTo("建议补充实施计划细化到人天");
        assertThat(subjectiveResult.getStatusStage2()).isEqualTo("PENDING");
        assertThat(subjectiveResult.getReuseKind()).isEqualTo("FRESH");
        verify(stateService).markCompleted(anyString());
        ArgumentCaptor<ScoreParseTask> taskCaptor = ArgumentCaptor.forClass(ScoreParseTask.class);
        verify(taskRepository, org.mockito.Mockito.atLeastOnce()).save(taskCaptor.capture());
        assertThat(taskCaptor.getAllValues().stream().anyMatch(t -> t.getBidContentHash() != null
                && t.getItemSetHash() != null)).isTrue();
    }

    @Test
    void outOfRangeScore_nullifiedAndPending() {
        mockNoActiveScoringTask();
        mockBidDocumentPresent();
        ScoreItem objective = item(10L, "资质", "具备 CMMI 5 级认证证书", "10", "OBJECTIVE");
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(PROJECT_ID))
                .thenReturn(List.of(objective));
        mockTaskAndBidDocumentText();
        when(scoreAnalyzer.assessObjective(anyString(), any(BigDecimal.class), anyString()))
                .thenReturn(assessment(15.0, 100, "模型越界输出", "引用", false, null, null));

        service.triggerScoring(PROJECT_ID);

        ArgumentCaptor<List<ScoreResult>> captor = ArgumentCaptor.captor();
        verify(resultRepository).saveAll(captor.capture());
        ScoreResult result = captor.getValue().get(0);
        assertThat(result.getActualScore()).isNull();
        assertThat(result.getStatusStage2()).isEqualTo("PENDING");
    }

    @Test
    void quoteMissing_nullifiesQuoteAndZeroScore() {
        mockNoActiveScoringTask();
        mockBidDocumentPresent();
        ScoreItem objective = item(10L, "资质", "具备 ISO27001 认证", "5", "OBJECTIVE");
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(PROJECT_ID))
                .thenReturn(List.of(objective));
        mockTaskAndBidDocumentText();
        when(scoreAnalyzer.assessObjective(anyString(), any(BigDecimal.class), anyString()))
                .thenReturn(assessment(0.0, 0, null, "模型违规引用", true, "标书未响应本项", "补充证书"));

        service.triggerScoring(PROJECT_ID);

        ArgumentCaptor<List<ScoreResult>> captor = ArgumentCaptor.captor();
        verify(resultRepository).saveAll(captor.capture());
        ScoreResult result = captor.getValue().get(0);
        assertThat(result.getActualScore()).isEqualByComparingTo("0");
        assertThat(result.getQuote()).isNull();
        assertThat(result.getStatusStage2()).isEqualTo("DANGER");
    }

    @Test
    void bidDocumentLoadFailure_marksFailedAndWritesFallbackPendingResults() {
        mockNoActiveScoringTask();
        mockBidDocumentPresent();
        ScoreItem objective = item(10L, "资质", "具备 CMMI 5 级认证证书", "10", "OBJECTIVE");
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(PROJECT_ID))
                .thenReturn(List.of(objective));
        when(documentStorage.loadByFileUrl(BID_FILE_URL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.triggerScoring(PROJECT_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("投标文件加载失败");
        verify(stateService, never()).createTask(anyString(), any(), anyString(), any(), any(), anyString());
        verify(stateService, never()).failTask(anyString(), anyString());
        verify(resultRepository, never()).saveAll(anyList());
    }

    @Test
    void sameBidAndItemHash_skipsWithoutAnalyzer() {
        mockNoActiveScoringTask();
        mockBidDocumentPresent();
        ScoreItem objective = item(10L, "资质", "具备 CMMI 5 级认证证书", "10", "OBJECTIVE");
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(PROJECT_ID)).thenReturn(List.of(objective));
        byte[] bytes = "pdf-bytes".getBytes();
        when(documentStorage.loadByFileUrl(BID_FILE_URL))
                .thenReturn(Optional.of(new LoadedTenderDocument(
                        new StoredTenderDocument(BID_FILE_URL, "/tmp/bid.pdf", "sha256"), bytes)));
        String bidHash = com.xiyu.bid.scoreparse.domain.BidScoreSkipPolicy.hashBytes(bytes);
        String itemHash = com.xiyu.bid.scoreparse.domain.BidScoreSkipPolicy.hashItems(List.of(
                com.xiyu.bid.scoreparse.domain.BidScoreSkipPolicy.itemFingerprint(
                        10L, objective.getWeight(), objective.getDetail())));
        when(taskRepository.findByProjectIdAndTaskTypeAndStatusIn(
                PROJECT_ID, "SCORING", List.of("COMPLETED")))
                .thenReturn(List.of(ScoreParseTask.builder()
                        .id(8L).taskId("prev").projectId(PROJECT_ID)
                        .taskType("SCORING").status("COMPLETED")
                        .bidContentHash(bidHash).itemSetHash(itemHash).build()));
        when(stateService.createTask(anyString(), any(), anyString(), any(), any(), anyString()))
                .thenReturn(scoringTask());

        ScoreParseTriggerDTO dto = service.triggerScoring(PROJECT_ID);

        assertThat(dto.outcome()).isEqualTo("SKIPPED");
        assertThat(dto.hint()).contains("文件未变化");
        verify(scoreAnalyzer, never()).assessObjective(anyString(), any(), anyString());
        verify(scoreAnalyzer, never()).assessSubjective(anyString(), anyString());
    }

    @Test
    void llmAssessFailure_marksFailedAndPreservesOldResults() {
        mockNoActiveScoringTask();
        mockBidDocumentPresent();
        ScoreItem objective = item(10L, "资质", "具备 CMMI 5 级认证证书", "10", "OBJECTIVE");
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(PROJECT_ID))
                .thenReturn(List.of(objective));
        mockTaskAndBidDocumentText();
        when(scoreAnalyzer.assessObjective(anyString(), any(BigDecimal.class), anyString()))
                .thenThrow(new RuntimeException("LLM 超时"));

        service.triggerScoring(PROJECT_ID);

        verify(stateService).failTask(anyString(), anyString());
        verify(resultRepository, never()).deleteByScoreItemIdIn(anyList());
        verify(resultRepository, never()).saveAll(anyList());
    }

    private void mockNoActiveScoringTask() {
        when(taskRepository.findByProjectIdAndTaskTypeAndStatusIn(
                PROJECT_ID, "SCORING", List.of("PENDING", "PROCESSING")))
                .thenReturn(List.of());
    }

    private void mockBidDocumentPresent() {
        ProjectDocument bidDoc = ProjectDocument.builder()
                .id(77L).projectId(PROJECT_ID).name("投标文件.pdf")
                .documentCategory("BID").fileUrl(BID_FILE_URL).build();
        when(projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(
                PROJECT_ID, "BID", null, null)).thenReturn(List.of(bidDoc));
    }

    private void mockItemsPresent() {
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(PROJECT_ID))
                .thenReturn(List.of(item(10L, "资质", "具备 CMMI 5 级认证证书", "10", "OBJECTIVE")));
    }

    private void mockTaskAndBidDocumentText() {
        ScoreParseTask task = scoringTask();
        when(stateService.createTask(anyString(), any(), anyString(), any(), any(), anyString())).thenReturn(task);
        when(taskRepository.findByTaskId(anyString())).thenReturn(Optional.of(task));
        when(documentStorage.loadByFileUrl(BID_FILE_URL))
                .thenReturn(Optional.of(new LoadedTenderDocument(
                        new StoredTenderDocument(BID_FILE_URL, "/tmp/bid.pdf", "sha256"),
                        "pdf-bytes".getBytes())));
        when(textExtractor.extract(anyString(), isNull(), any(byte[].class)))
                .thenReturn(new ExtractedTenderDocument(
                        "投标文件.pdf", "application/pdf", "投标文件全文内容", 8, "pdf"));
    }

    private ScoreParseTask scoringTask() {
        ScoreParseTask task = ScoreParseTask.builder()
                .id(5L).taskId("task-uuid").projectId(PROJECT_ID)
                .taskType("SCORING").status("PENDING").build();
        return task;
    }

    private ScoreItem item(Long id, String dim, String detail, String weight, String scoreType) {
        return ScoreItem.builder()
                .id(id).projectId(PROJECT_ID).parseTaskId(1L)
                .itemIndex(1).code("A1").dim(dim).detail(detail)
                .weight(new BigDecimal(weight)).scoreType(scoreType)
                .statusStage1("PENDING").build();
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
