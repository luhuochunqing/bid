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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
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
        ), "人工确认采纳"), "admin");

        verify(itemPersistenceService).persistItems(eq(PROJECT_ID), eq(9L), anyList());
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
        when(taskRepository.findByProjectIdAndTaskTypeAndStatusIn(
                eq(PROJECT_ID), eq("PARSE"), eq(List.of("PENDING", "PROCESSING", "COMPLETED", "FAILED"))))
                .thenReturn(List.of(task));
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
