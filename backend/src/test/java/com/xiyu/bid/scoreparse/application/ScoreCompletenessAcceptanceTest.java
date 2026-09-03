package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.biddraftagent.repository.BidTenderDocumentSnapshotRepository;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.scoreparse.domain.RecallOutcome;
import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import com.xiyu.bid.scoreparse.domain.ScoreCompletenessPolicy;
import com.xiyu.bid.scoreparse.domain.ScoreEvidence;
import com.xiyu.bid.scoreparse.domain.ScoreFact;
import com.xiyu.bid.scoreparse.domain.ScoreParseDecision;
import com.xiyu.bid.scoreparse.domain.ScoreParseReviewSnapshot;
import com.xiyu.bid.scoreparse.entity.ScoreParseTask;
import com.xiyu.bid.scoreparse.infrastructure.openai.OpenAiScoreAnalyzer;
import com.xiyu.bid.scoreparse.infrastructure.openai.ScoreGlobalMergeExecutor;
import com.xiyu.bid.scoreparse.repository.ScoreItemRepository;
import com.xiyu.bid.scoreparse.repository.ScoreParseTaskRepository;
import com.xiyu.bid.scoreparse.repository.ScoreResultRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * spec 059 Phase 4 完整性阻断、人工复核与取消静默归一化验收测试。
 */
@ExtendWith(MockitoExtension.class)
class ScoreCompletenessAcceptanceTest {

    private static final Long PROJECT_ID = 100L;
    private static final String TASK_ID = "task-phase4-acceptance";

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

    @Mock
    private ObjectProvider<ScoreGlobalMergeExecutor> mergeExecutorProvider;
    @Mock
    private ObjectProvider<com.xiyu.bid.scoreparse.infrastructure.openai.LlmTableScoreExtractor> llmTableScoreExtractorProvider;
    @Mock
    private ObjectProvider<com.xiyu.bid.scoreparse.infrastructure.openai.ScoreTableSchemaMapper> schemaMapperProvider;

    private ScoreParseAppService appService;

    @BeforeEach
    void setUp() {
        lenient().when(mergeExecutorProvider.getIfAvailable()).thenReturn(null);
        lenient().when(llmTableScoreExtractorProvider.getIfAvailable()).thenReturn(null);
        lenient().when(schemaMapperProvider.getIfAvailable()).thenReturn(null);

        ScoreCandidatePostProcessor postProcessor = new ScoreCandidatePostProcessor(
                scoreAnalyzer,
                new ScoreGlobalMergeOrchestrator(mergeExecutorProvider),
                com.xiyu.bid.scoreparse.domain.ScoreVocabularyRegistry.builder().build());

        appService = new ScoreParseAppService(
                taskRepository, itemRepository, resultRepository, projectDocumentRepository,
                stateService, progressService, snapshotRepository,
                projectAccessScopeService, scoreAnalyzer, estimatedScoreService,
                itemPersistenceService, initiationTenderTextResolver, sidecarTableExtractor,
                postProcessor, llmTableScoreExtractorProvider, schemaMapperProvider);
        ReflectionTestUtils.setField(appService, "self", appService);
    }

    private ScoreFact createFact(String factId, String parentId, String dim, double weight) {
        ScoreEvidence ev = new ScoreEvidence(
                ScoreEvidence.Kind.EXPLICIT_WEIGHT_COLUMN, "得" + weight + "分", "c1", 0, 5, "明确分值");
        return new ScoreFact(
                factId, "t1", List.of("c1"), "item " + factId, "1.1", dim,
                BigDecimal.valueOf(weight), ev, null, parentId,
                "STRUCTURED_TABLE_DETERMINISTIC", 1.0, ScoreFact.ValidationStatus.AUTO_APPROVED);
    }

    @Test
    void invariant1_weightSumMismatch_blockedFromAutoApprove_andNoNormalization() {
        // 权重总和为 80 分（缺 20 分）
        ScoreFact f1 = createFact("f1", null, "商务", 50.0);
        ScoreFact f2 = createFact("f2", null, "技术", 30.0);

        var result = ScoreCompletenessPolicy.evaluateCompleteness(List.of(f1, f2), "总分 100 分。");

        // 1. 完整性策略阻断为 REVIEW_REQUIRED
        assertThat(result.complete()).isFalse();
        assertThat(result.decision()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
        assertThat(result.fallbackReason()).isEqualTo("WEIGHT_SUM_MISMATCH");
        // 2. 权重保持原始 80 分，绝无自动归一化篡改
        assertThat(result.leafWeightSum()).isEqualByComparingTo("80.00");

        // 3. 复核快照正确记录差额
        ScoreParseReviewSnapshot snapshot = ScoreParseReviewSnapshot.from(TASK_ID, result, List.of(f1, f2));
        assertThat(snapshot.discrepancy()).isEqualByComparingTo("20.00");
        assertThat(snapshot.toMap()).containsEntry("fallbackReason", "WEIGHT_SUM_MISMATCH");
    }

    @Test
    void invariant2_parentChildDuplicate_blockedFromAutoApprove() {
        // 父项 p1(30) 被作为叶子计分，同时子项 c1(10), c2(20) 也计分
        ScoreFact p1 = createFact("p1", null, "商务大类", 30.0);
        ScoreFact c1 = createFact("c1", "p1", "商务-资质", 10.0);
        ScoreFact c2 = createFact("c2", "p1", "商务-业绩", 20.0);
        ScoreFact tech = createFact("tech", null, "技术方案", 70.0);

        // 如果未经父项过滤直接把全部送入完整性检查，大类若与子项不自洽或被引用
        var result = ScoreCompletenessPolicy.evaluateCompleteness(List.of(p1, c1, c2, tech), "总分 100 分。");
        // 叶子事实为 c1(10) + c2(20) + tech(70) = 100，父项为 p1
        assertThat(result.leafCount()).isEqualTo(3);
        assertThat(result.parentCount()).isEqualTo(1);
        assertThat(result.complete()).isTrue();

        // 但如果子项合计与父项声明不匹配（例如 c1 仅 10，缺 c2，子项合计 10 != 30）
        var mismatchResult = ScoreCompletenessPolicy.evaluateCompleteness(List.of(p1, c1, tech), "总分 100 分。");
        assertThat(mismatchResult.complete()).isFalse();
        assertThat(mismatchResult.decision()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
        assertThat(mismatchResult.fallbackReason()).isEqualTo("CATEGORY_SUM_MISMATCH");
    }

    @Test
    void invariant3_persistenceProtection_reviewRequired_neverOverwritesScoreItems() {
        // 模拟解析产出合计 80 分（不满足 100 分）
        ScoreParseTask task = ScoreParseTask.builder()
                .id(10L).taskId(TASK_ID).projectId(PROJECT_ID)
                .taskType("PARSE").status("PENDING").build();
        when(taskRepository.findByTaskId(TASK_ID)).thenReturn(Optional.of(task));
        when(initiationTenderTextResolver.resolveIntake(PROJECT_ID))
                .thenReturn(TenderIntake.found(new TenderTextSource(
                        "tender.docx", "doc-insight://t/1", "评分办法：总分100分。")));
        when(scoreAnalyzer.recallCandidates(anyString(), isNull(), any()))
                .thenReturn(new RecallOutcome(List.of(
                        new ScoreCandidate("A1", "商务", "具备资质", new BigDecimal("50"), "OBJECTIVE", null, null, "P1", "DETERMINISTIC"),
                        new ScoreCandidate("A2", "技术", "实施方案", new BigDecimal("30"), "OBJECTIVE", null, null, "P2", "DETERMINISTIC")
                ), 0));

        appService.executeParseAsync(TASK_ID);

        // 任务状态为 COMPLETED，但决策状态为 REVIEW_REQUIRED
        verify(stateService).markCompleted(TASK_ID);
        assertThat(task.getDecisionStatus()).isEqualTo("REVIEW_REQUIRED");
        assertThat(task.getFallbackReason()).isEqualTo("WEIGHT_SUM_MISMATCH");

        // 绝对禁止调用 persistItems，已有生效评分项不被覆盖
        verify(itemPersistenceService, never()).persistItems(any(), any(), anyList());
        verify(estimatedScoreService, never()).estimateForProject(any());
    }

    @Test
    void invariant4_autoApproved_persistsAndEstimates() {
        // 满分 60 + 40 = 100 自洽通过
        ScoreParseTask task = ScoreParseTask.builder()
                .id(11L).taskId(TASK_ID).projectId(PROJECT_ID)
                .taskType("PARSE").status("PENDING").build();
        when(taskRepository.findByTaskId(TASK_ID)).thenReturn(Optional.of(task));
        when(initiationTenderTextResolver.resolveIntake(PROJECT_ID))
                .thenReturn(TenderIntake.found(new TenderTextSource(
                        "tender.docx", "doc-insight://t/1", "评分办法：总分100分。")));
        when(scoreAnalyzer.recallCandidates(anyString(), isNull(), any()))
                .thenReturn(new RecallOutcome(List.of(
                        new ScoreCandidate("A1", "商务", "具备资质", new BigDecimal("60"), "OBJECTIVE", null, null, "P1", "DETERMINISTIC"),
                        new ScoreCandidate("A2", "技术", "实施方案", new BigDecimal("40"), "OBJECTIVE", null, null, "P2", "DETERMINISTIC")
                ), 0));

        appService.executeParseAsync(TASK_ID);

        // AUTO_APPROVED 正常触发 persistItems 与 estimateForProject
        verify(itemPersistenceService).persistItems(eq(PROJECT_ID), eq(11L), anyList());
        verify(estimatedScoreService).estimateForProject(PROJECT_ID);
        verify(stateService).markCompleted(TASK_ID);
        assertThat(task.getDecisionStatus()).isEqualTo("AUTO_APPROVED");
        assertThat(task.getFallbackReason()).isNull();
    }
}
