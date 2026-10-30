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
import org.mockito.ArgumentCaptor;
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
    private ObjectProvider<SidecarDocumentArtifactExtractor> artifactExtractorProvider;
    @Mock
    private ObjectProvider<com.xiyu.bid.scoreparse.infrastructure.openai.LlmTableScoreExtractor> llmTableScoreExtractorProvider;
    @Mock
    private ObjectProvider<com.xiyu.bid.scoreparse.infrastructure.openai.ScoreTableSchemaMapper> schemaMapperProvider;

    private ScoreParseAppService appService;

    @BeforeEach
    void setUp() {
        lenient().when(mergeExecutorProvider.getIfAvailable()).thenReturn(null);
        lenient().when(artifactExtractorProvider.getIfAvailable()).thenReturn(null);
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
                artifactExtractorProvider, postProcessor, llmTableScoreExtractorProvider, schemaMapperProvider);
        ReflectionTestUtils.setField(appService, "self", appService);
    }

    @Test void failedModelRequestEndsTaskAsFailedWithoutPublishingItems() {
        var task = ScoreParseTask.builder().id(900L).taskId(TASK_ID).projectId(PROJECT_ID).build();
        when(taskRepository.findByTaskId(TASK_ID)).thenReturn(Optional.of(task));
        var source = new TenderTextSource("synthetic.pdf", "memory:synthetic", "采购要求");
        when(initiationTenderTextResolver.resolveExecutionSource(PROJECT_ID))
                .thenReturn(TenderExecutionSource.found(source, null));
        var model = org.mockito.Mockito.mock(MultimodalScoreFactExtractor.class);
        when(model.supportsTextOnly()).thenReturn(true);
        when(model.extractTextAndProgress(anyString(), any())).thenThrow(
                new ScoreModelRequestException(new IllegalStateException("private gateway body",
                        new java.net.SocketTimeoutException())));
        ReflectionTestUtils.setField(appService, "multimodalScoreFactExtractor", model);
        appService.executeParseAsync(TASK_ID);
        verify(stateService).failTask(eq(TASK_ID), org.mockito.ArgumentMatchers.contains("评分模型请求超时"));
        org.mockito.Mockito.verifyNoInteractions(itemPersistenceService);
        org.mockito.Mockito.verify(model, org.mockito.Mockito.never()).recheckTextAndProgress(anyString(), any());
    }

    private ScoreFact createFact(String factId, String parentId, String dim, double weight) {
        ScoreEvidence ev = new ScoreEvidence(
                ScoreEvidence.Kind.EXPLICIT_WEIGHT_COLUMN, "得" + weight + "分", "c1", 0, 5, "明确分值");
        return new ScoreFact(
                factId, "t1", List.of("c1"), "item " + factId, "1.1", dim,
                BigDecimal.valueOf(weight), ev, null, parentId,
                "STRUCTURED_TABLE_DETERMINISTIC", 1.0, ScoreFact.ValidationStatus.AUTO_APPROVED);
    }

    /** 9 参 legacy ctor 构建生产链候选：contextNote 为第 6 参（承载大类声明分值）。 */
    private static ScoreCandidate cand(String code, String dim, String detail, int weight, String contextNote) {
        return new ScoreCandidate(code, dim, detail, BigDecimal.valueOf(weight), "OBJECTIVE",
                contextNote, null, null, null);
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
        assertThat(snapshot.toMap()).containsEntry("declaredTotalSource", "TENDER_TEXT");
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
    void invariant3_reviewRequired_preservesExistingItemsWithReviewFlag() {
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

        // 任务状态为 COMPLETED，决策状态为 REVIEW_REQUIRED，复核标记保留在 task 上
        verify(stateService).markCompleted(TASK_ID);
        assertThat(task.getDecisionStatus()).isEqualTo("REVIEW_REQUIRED");
        assertThat(task.getFallbackReason()).isEqualTo("WEIGHT_SUM_MISMATCH");

        // task2 收敛：REVIEW_REQUIRED 只记录复核状态，不覆盖旧 score_item，不触发估分。
        verify(itemPersistenceService, org.mockito.Mockito.never()).persistItems(any(), any(), anyList());
        verify(estimatedScoreService, org.mockito.Mockito.never()).estimateForProject(any());
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

    /**
     * Fix C 端到端金标（事故 1358「漏识别」根治，脱敏但结构忠实复现）。
     * <p>11 项、满分恰好 100 闭环；商务组 8 项 dim 均不含 family 关键字，但 contextNote 声明
     * 「商务评分标准（56分）」，使 {@code declaredFamilyWeights={商务:56}} 在 {@code familySums} 中**无成员**
     * ——正是 1358 触发 {@link com.xiyu.bid.scoreparse.domain.WeightSumCheck} 大类交叉校验假阳性的形态。
     * <p>本测试跑通**真实生产链** {@code executeParseAsync → ScoreCandidatePostProcessor → WeightSumCheck → persistItems}，
     * 断言 1358 漏识别症状的反面：AUTO_APPROVED、无复核标记、11 项全部落库、触发预计得分。
     * <p>反循环验证保证：还原 Fix B（去掉 {@code WeightSumCheck} 的 {@code familySums.containsKey} 跳过守卫）
     * 会使本测试转红（NPE→failTask 或假阳性 REVIEW_REQUIRED + 整批被静默丢弃），即金标真能抓回归（详见 lessons §217）。
     */
    @Test
    void incident1358_closedLoopWithDeclaredFamilyLackingMembers_persistsAllItems_autoApproved() {
        ScoreParseTask task = ScoreParseTask.builder()
                .id(12L).taskId(TASK_ID).projectId(PROJECT_ID)
                .taskType("PARSE").status("PENDING").build();
        when(taskRepository.findByTaskId(TASK_ID)).thenReturn(Optional.of(task));
        when(initiationTenderTextResolver.resolveIntake(PROJECT_ID))
                .thenReturn(TenderIntake.found(new TenderTextSource(
                        "tender.docx", "doc-insight://t/1", "评分办法：总分100分。")));
        when(scoreAnalyzer.recallCandidates(anyString(), isNull(), any()))
                .thenReturn(new RecallOutcome(List.of(
                        // 商务组（56 分）：dim 不含 family 关键字，contextNote 声明大类总分
                        cand("A1", "财务状况及抗风险能力", "提供近三年经审计财务报表，无重大亏损及不良记录", 8, "商务评分标准（56分）"),
                        cand("A2", "商城系统对接能力", "具备与采购方商城系统的数据对接与联调方案", 8, "商务评分标准（56分）"),
                        cand("A3", "供货及安装方案", "供货周期、安装组织与进度保障措施完整可行", 7, "商务评分标准（56分）"),
                        cand("A4", "售后保障及维修承诺", "质保期内维修、备件供应及故障处理承诺", 7, "商务评分标准（56分）"),
                        cand("A5", "业绩案例", "提供近三年同类项目业绩合同关键页复印件", 6, "商务评分标准（56分）"),
                        cand("A6", "项目团队配置", "项目负责人及核心团队成员资历与分工明确", 6, "商务评分标准（56分）"),
                        cand("A7", "供货能力及信用记录", "提供供货能力证明及无重大违法记录声明函", 7, "商务评分标准（56分）"),
                        cand("A8", "质保期及故障处理时效", "质保期时长与故障到达现场处理时效承诺", 7, "商务评分标准（56分）"),
                        // 技术组（44 分）：dim 含「技术」→ familySums[技术]=44，自洽无声明
                        cand("B1", "技术实施方案", "技术路线、实施步骤与质量控制措施科学合理", 18, null),
                        cand("B2", "技术架构与功能满足度", "系统架构先进性及对采购需求的功能覆盖程度", 16, null),
                        cand("B3", "技术培训与知识转移", "培训计划、课时安排与知识转移交付物", 10, null)
                ), 0));

        appService.executeParseAsync(TASK_ID);

        // 1358 漏识别症状的反面：满分闭环的成功解析必须 AUTO_APPROVED、无复核标记
        verify(stateService).markCompleted(TASK_ID);
        assertThat(task.getDecisionStatus()).isEqualTo("AUTO_APPROVED");
        assertThat(task.getFallbackReason()).isNull();

        // 11 项无一被静默丢弃，全部落库并触发阶段 1 预计得分
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ScoreCandidate>> persisted = ArgumentCaptor.forClass(List.class);
        verify(itemPersistenceService).persistItems(eq(PROJECT_ID), eq(12L), persisted.capture());
        assertThat(persisted.getValue()).hasSize(11);
        verify(estimatedScoreService).estimateForProject(PROJECT_ID);
    }
}
