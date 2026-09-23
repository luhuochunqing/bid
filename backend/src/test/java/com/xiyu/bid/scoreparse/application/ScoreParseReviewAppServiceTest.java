package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.dto.ScoreParseReviewCommand;
import com.xiyu.bid.scoreparse.dto.ScoreParseReviewDTO;
import com.xiyu.bid.scoreparse.entity.ScoreParseEvidence;
import com.xiyu.bid.scoreparse.entity.ScoreParseFact;
import com.xiyu.bid.scoreparse.entity.ScoreParseReview;
import com.xiyu.bid.scoreparse.entity.ScoreParseTask;
import com.xiyu.bid.scoreparse.repository.ScoreParseEvidenceRepository;
import com.xiyu.bid.scoreparse.repository.ScoreParseFactRepository;
import com.xiyu.bid.scoreparse.repository.ScoreParseReviewRepository;
import com.xiyu.bid.scoreparse.repository.ScoreParseTaskRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScoreParseReviewAppServiceTest {

    private static final Long PROJECT_ID = 100L;

    @Mock
    private ScoreParseTaskRepository taskRepository;
    @Mock
    private ScoreParseFactRepository factRepository;
    @Mock
    private ScoreParseEvidenceRepository evidenceRepository;
    @Mock
    private ScoreParseReviewRepository reviewRepository;
    @Mock
    private ProjectAccessScopeService projectAccessScopeService;
    @Mock
    private ScoreItemPersistenceService itemPersistenceService;
    @Mock
    private EstimatedScoreService estimatedScoreService;

    private ScoreParseReviewAppService service;

    @BeforeEach
    void setUp() {
        service = new ScoreParseReviewAppService(taskRepository, factRepository, evidenceRepository,
                reviewRepository, projectAccessScopeService, itemPersistenceService, estimatedScoreService);
    }

    @Test
    void getReviewSnapshot_returnsFactsEvidenceAndAuditsForLatestParseTask() {
        mockLatestTask();
        when(factRepository.findByParseTaskId(9L)).thenReturn(List.of(fact("fact-1", "10")));
        when(evidenceRepository.findByParseTaskId(9L)).thenReturn(List.of(evidence("fact-1")));
        when(reviewRepository.findByParseTaskId(9L)).thenReturn(List.of(review("fact-1")));

        ScoreParseReviewDTO dto = service.getReviewSnapshot(PROJECT_ID);

        verify(projectAccessScopeService).assertCurrentUserCanAccessProject(PROJECT_ID);
        assertThat(dto.decisionStatus()).isEqualTo("REVIEW_REQUIRED");
        assertThat(dto.facts()).hasSize(1);
        assertThat(dto.facts().get(0).evidences()).hasSize(1);
        assertThat(dto.audits()).hasSize(1);
    }

    @Test
    void getReviewSnapshotReturnsPersistedChildAggregationAndLegacyDefaultsToSum() {
        mockLatestTask();
        ScoreParseFact sharedCap = fact("fact-1", "10");
        sharedCap.setChildAggregation("SHARED_CAP");
        when(factRepository.findByParseTaskId(9L)).thenReturn(List.of(sharedCap), List.of(fact("fact-1", "10")));
        when(evidenceRepository.findByParseTaskId(9L)).thenReturn(List.of());
        when(reviewRepository.findByParseTaskId(9L)).thenReturn(List.of());

        ScoreParseReviewDTO dto = service.getReviewSnapshot(PROJECT_ID);
        ScoreParseReviewDTO legacyDto = service.getReviewSnapshot(PROJECT_ID);

        assertThat(dto.facts().get(0).childAggregation()).isEqualTo("SHARED_CAP");
        assertThat(legacyDto.facts().get(0).childAggregation()).isEqualTo("SUM");
    }

    @Test
    void submitReview_savesAuditOnlyAndReturnsSnapshot() {
        mockLatestTask();
        when(factRepository.findByParseTaskId(9L)).thenReturn(List.of(fact("fact-1", "10")));
        when(evidenceRepository.findByParseTaskId(9L)).thenReturn(List.of());
        when(reviewRepository.findByParseTaskId(9L)).thenReturn(List.of());

        service.submitReview(PROJECT_ID, new ScoreParseReviewCommand(List.of(
                new ScoreParseReviewCommand.Action("fact-1", "MANUAL_ADJUST", new BigDecimal("12"), "人工确认权重")
        ), "复核"), "zhangsan");

        ArgumentCaptor<ScoreParseReview> captor = ArgumentCaptor.forClass(ScoreParseReview.class);
        verify(reviewRepository).save(captor.capture());
        ScoreParseReview saved = captor.getValue();
        assertThat(saved.getProjectId()).isEqualTo(PROJECT_ID);
        assertThat(saved.getParseTaskId()).isEqualTo(9L);
        assertThat(saved.getFactId()).isEqualTo("fact-1");
        assertThat(saved.getAction()).isEqualTo("MANUAL_ADJUST");
        assertThat(saved.getOriginalWeight()).isEqualByComparingTo("10");
        assertThat(saved.getAdjustedWeight()).isEqualByComparingTo("12");
        assertThat(saved.getOperatorName()).isEqualTo("zhangsan");
    }

    @Test
    void submitReview_adoptCandidates_persistsItemsAndMarksManualApproved() {
        mockLatestTask();
        ScoreParseFact fact1 = fact("fact-1", "20");
        ScoreParseFact fact2 = fact("fact-2", "80");
        when(factRepository.findByParseTaskId(9L)).thenReturn(List.of(fact1, fact2));
        when(evidenceRepository.findByParseTaskId(9L)).thenReturn(List.of());
        when(reviewRepository.findByParseTaskId(9L)).thenReturn(List.of());

        ScoreParseReviewDTO result = service.submitReview(PROJECT_ID, new ScoreParseReviewCommand(List.of(
                new ScoreParseReviewCommand.Action(null, "ADOPT_CANDIDATES", null, "一键采纳所有候选项")
        ), "人工确认采纳", 9L), "admin");

        verify(itemPersistenceService).persistItems(eq(PROJECT_ID), eq(9L), anyList());
        var ordered = inOrder(taskRepository, itemPersistenceService);
        ordered.verify(taskRepository).lockProjectForTaskChange(PROJECT_ID);
        ordered.verify(taskRepository).findLatestParseTaskForUpdate(PROJECT_ID);
        ordered.verify(itemPersistenceService).persistItems(eq(PROJECT_ID), eq(9L), anyList());
        verify(estimatedScoreService).estimateForProject(PROJECT_ID);

        ArgumentCaptor<ScoreParseTask> taskCaptor = ArgumentCaptor.forClass(ScoreParseTask.class);
        verify(taskRepository).save(taskCaptor.capture());
        ScoreParseTask updatedTask = taskCaptor.getValue();
        assertThat(updatedTask.getDecisionStatus()).isEqualTo("MANUAL_APPROVED");
        assertThat(updatedTask.getOriginalTotalWeight()).isEqualByComparingTo("100");
        assertThat(updatedTask.getFallbackReason()).isNull();

        ArgumentCaptor<ScoreParseReview> reviewCaptor = ArgumentCaptor.forClass(ScoreParseReview.class);
        verify(reviewRepository).save(reviewCaptor.capture());
        assertThat(reviewCaptor.getValue().getAction()).isEqualTo("ADOPT_CANDIDATES");
    }

    @Test
    void submitReview_adoptCandidates_closesAgainstDeclaredEightyPoints() {
        mockLatestTask();
        ScoreParseTask task = taskRepository.findByProjectIdAndTaskTypeAndStatusIn(
                PROJECT_ID, "PARSE", List.of("PENDING", "PROCESSING", "COMPLETED", "FAILED")).get(0);
        task.setRunSummary("{\"tableSplits\":[{\"declaredTotal\":80}]} ");
        when(factRepository.findByParseTaskId(9L)).thenReturn(List.of(fact("fact-1", "30"), fact("fact-2", "50")));
        when(evidenceRepository.findByParseTaskId(9L)).thenReturn(List.of());
        when(reviewRepository.findByParseTaskId(9L)).thenReturn(List.of());

        service.submitReview(PROJECT_ID, new ScoreParseReviewCommand(List.of(
                new ScoreParseReviewCommand.Action(null, "ADOPT_CANDIDATES", null, "采纳80分制候选项")
        ), "人工确认采纳", 9L), "admin");

        verify(itemPersistenceService).persistItems(eq(PROJECT_ID), eq(9L), anyList());
        assertThat(task.getOriginalTotalWeight()).isEqualByComparingTo("80");
    }

    @Test
    void submitReview_adoptCandidates_rejectsConflictingDeclaredTotalsWithoutPersistence() {
        mockLatestTask();
        ScoreParseTask task = taskRepository.findByProjectIdAndTaskTypeAndStatusIn(
                PROJECT_ID, "PARSE", List.of("PENDING", "PROCESSING", "COMPLETED", "FAILED")).get(0);
        task.setRunSummary("{\"tableSplits\":[{\"tableId\":\"NO_DECLARED_TOTAL\",\"declaredTotal\":60},"
                + "{\"tableId\":\"business\",\"declaredTotal\":40}]}");
        when(factRepository.findByParseTaskId(9L)).thenReturn(List.of(fact("fact-1", "60"), fact("fact-2", "40")));

        assertThatThrownBy(() -> service.submitReview(PROJECT_ID, new ScoreParseReviewCommand(List.of(
                new ScoreParseReviewCommand.Action(null, "ADOPT_CANDIDATES", null, "采纳冲突候选项")
        ), "人工确认采纳", 9L), "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("声明总分")
                .hasMessageContaining("人工确认");

        verify(itemPersistenceService, never()).persistItems(eq(PROJECT_ID), eq(9L), anyList());
        verify(taskRepository, never()).save(task);
        verify(reviewRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void submitReview_rejectsFailedLatestParseBeforePublishingOldCandidates() {
        ScoreParseTask failed = ScoreParseTask.builder().id(10L).taskId("new-failed")
                .projectId(PROJECT_ID).taskType("PARSE").status("FAILED")
                .decisionStatus("REVIEW_REQUIRED").build();
        when(taskRepository.findLatestParseTaskForUpdate(PROJECT_ID)).thenReturn(java.util.Optional.of(failed));
        when(factRepository.findByParseTaskId(10L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.submitReview(PROJECT_ID, new ScoreParseReviewCommand(List.of(
                new ScoreParseReviewCommand.Action("fact-from-old-task", "ADOPT_CANDIDATES", new BigDecimal("100"), "旧页面")
        ), "采纳"), "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("解析失败");
        verify(itemPersistenceService, never()).persistItems(eq(PROJECT_ID), eq(10L), anyList());
    }

    @Test
    void submitReview_rejectsOldTaskIdEvenWhenNewParseCompleted() {
        mockLatestTask();
        when(factRepository.findByParseTaskId(9L)).thenReturn(List.of(fact("new-fact", "100")));

        assertThatThrownBy(() -> service.submitReview(PROJECT_ID, new ScoreParseReviewCommand(List.of(
                new ScoreParseReviewCommand.Action("old-fact", "ADOPT_CANDIDATES", new BigDecimal("100"), "旧页面")
        ), "采纳", 8L), "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("任务已变化");
        verify(itemPersistenceService, never()).persistItems(eq(PROJECT_ID), eq(9L), anyList());
    }

    @Test
    void submitReview_rejectsRepeatedAdoptionWithoutRewritingItems() {
        mockLatestTask();
        ScoreParseTask task = taskRepository.findLatestParseTaskForUpdate(PROJECT_ID).orElseThrow();
        task.setDecisionStatus("MANUAL_APPROVED");
        when(factRepository.findByParseTaskId(9L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.submitReview(PROJECT_ID, new ScoreParseReviewCommand(List.of(
                new ScoreParseReviewCommand.Action(null, "ADOPT_CANDIDATES", null, "重复点击")
        ), "重复", 9L), "admin"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("已采纳");
        verify(itemPersistenceService, never()).persistItems(eq(PROJECT_ID), eq(9L), anyList());
    }

    @Test
    void declaredTotal_sumsOnlyExplicitSplitTotalsAndDefaultsWhenUnknownGroupExists() {
        ScoreParseTask explicit = ScoreParseTask.builder().taskId("explicit")
                .runSummary("{\"tableSplits\":[{\"tableId\":\"a\",\"declaredTotal\":60},{\"tableId\":\"b\",\"declaredTotal\":40}]}")
                .build();
        ScoreParseTask unknown = ScoreParseTask.builder().taskId("unknown")
                .runSummary("{\"tableSplits\":[{\"tableId\":\"NO_DECLARED_TOTAL\",\"declaredTotal\":60},{\"tableId\":\"b\",\"declaredTotal\":40}]}")
                .build();

        assertThat(ScoreParseReviewAppService.declaredTotal(explicit)).isEqualByComparingTo("100");
        assertThat(ScoreParseReviewAppService.declaredTotal(unknown)).isNull();
    }

    @Test
    void declaredTotal_usesTenderTextTotalForSingleUnknownTableGroup() {
        ScoreParseTask task = ScoreParseTask.builder().taskId("text-total")
                .runSummary("{\"tableSplits\":[{\"tableId\":\"NO_DECLARED_TOTAL\",\"declaredTotal\":80}]}")
                .build();
        assertThat(ScoreParseReviewAppService.declaredTotal(task)).isEqualByComparingTo("80");
    }

    private void mockLatestTask() {
        ScoreParseTask task = ScoreParseTask.builder()
                .id(9L)
                .taskId("parse-task")
                .projectId(PROJECT_ID)
                .taskType("PARSE")
                .status("COMPLETED")
                .decisionStatus("REVIEW_REQUIRED")
                .fallbackReason("WEIGHT_SUM_MISMATCH")
                .reviewReason("权重不匹配")
                .pipelineVersion("EVIDENCE_PIPELINE_V1")
                .artifactSha256("sha256")
                .build();
        lenient().when(taskRepository.findByProjectIdAndTaskTypeAndStatusIn(
                eq(PROJECT_ID), eq("PARSE"), eq(List.of("PENDING", "PROCESSING", "COMPLETED", "FAILED"))))
                .thenReturn(List.of(task));
        lenient().when(taskRepository.findLatestParseTaskForUpdate(PROJECT_ID))
                .thenReturn(java.util.Optional.of(task));
    }

    private ScoreParseFact fact(String factId, String weight) {
        return ScoreParseFact.builder()
                .id(1L)
                .projectId(PROJECT_ID)
                .parseTaskId(9L)
                .factId(factId)
                .code("A1")
                .dimension("资质")
                .weight(new BigDecimal(weight))
                .sourceText("具备 CMMI 认证")
                .extractionMethod("SIDE_CAR")
                .validationStatus("REVIEW_REQUIRED")
                .confidence(BigDecimal.ONE)
                .build();
    }

    private ScoreParseEvidence evidence(String factId) {
        return ScoreParseEvidence.builder()
                .id(3L)
                .parseTaskId(9L)
                .factId(factId)
                .kind("SOURCE_CELL")
                .cellId("c1")
                .quotedText("CMMI 认证")
                .build();
    }

    private ScoreParseReview review(String factId) {
        return ScoreParseReview.builder()
                .projectId(PROJECT_ID)
                .parseTaskId(9L)
                .factId(factId)
                .action("RETAIN_ORIGINAL")
                .reason("保留现有项")
                .operatorName("zhangsan")
                .build();
    }
}
