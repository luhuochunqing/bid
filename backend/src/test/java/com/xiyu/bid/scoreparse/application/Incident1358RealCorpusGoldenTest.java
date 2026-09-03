package com.xiyu.bid.scoreparse.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.biddraftagent.repository.BidTenderDocumentSnapshotRepository;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.scoreparse.domain.RecallOutcome;
import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import com.xiyu.bid.scoreparse.entity.ScoreParseTask;
import com.xiyu.bid.scoreparse.infrastructure.openai.OpenAiScoreAnalyzer;
import com.xiyu.bid.scoreparse.infrastructure.openai.ScoreGlobalMergeExecutor;
import com.xiyu.bid.scoreparse.repository.ScoreItemRepository;
import com.xiyu.bid.scoreparse.repository.ScoreParseTaskRepository;
import com.xiyu.bid.scoreparse.repository.ScoreResultRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Fix C 真实数据金标（事故 1358「漏识别」根治）—— 本地语料门控，CI / 纯源码 worktree 自动跳过。
 *
 * <p><b>为何门控</b>：真实客户招标文件<b>禁止入仓</b>（{@code docs/lessons/scoreparse-golden-corpus/index.md}：
 * 真实文件只能存于 {@code XIYU_EVAL_CORPUS_DIR} 本地路径）。本测试仅在设置了该环境变量、且本地放置了
 * 离线录制的真实候选快照时才运行；未设置则整类禁用（镜像 {@code scripts/eval-scoreparse/nightly-full.sh} 的跳过语义）。
 *
 * <p><b>与 Part A 的分工</b>：脱敏金标
 * {@code ScoreCompletenessAcceptanceTest#incident1358_closedLoopWithDeclaredFamilyLackingMembers_persistsAllItems_autoApproved}
 * 用合成但结构忠实的 11 项夹具证明<b>形态</b>（满分闭环 + 声明大类无 familySums 成员 → AUTO_APPROVED + 全量落库）；
 * 本测试用<b>真实 1358 召回数据</b>跑同一条生产链，证明真实文档的解析结果同样落到 AUTO_APPROVED、11 项无一漏识别。
 *
 * <p><b>快照约定（如何录制 {@code candidates.json}）</b>：对真实 1358 文档跑召回链（{@code OpenAiScoreAnalyzer.recallCandidates}
 * 或结构化表格提取）后，把产出的 {@code List<ScoreCandidate>} 用 Jackson 序列化，放到
 * {@code $XIYU_EVAL_CORPUS_DIR/incident-1358/candidates.json}。代码中只出现「事故编号」约定路径，
 * <b>不含任何真实客户文件名 / 文件名 hash / 原文内容</b>（合规）。
 *
 * <p><b>已知期望（脱敏，仅计数与决策，非可定位数据）</b>：真实 1358 解析为 11 项、满分恰好 100 闭环、AUTO_APPROVED。
 */
@EnabledIfEnvironmentVariable(named = "XIYU_EVAL_CORPUS_DIR", matches = ".+")
@ExtendWith(MockitoExtension.class)
class Incident1358RealCorpusGoldenTest {

    private static final Long PROJECT_ID = 200L;
    private static final String TASK_ID = "task-1358-realcorpus";
    private static final Long TASK_PK = 20L;
    /** 真实 1358 的已知金标期望：11 项、满分 100 闭环。 */
    private static final int EXPECTED_ITEM_COUNT = 11;
    private static final String EXPECTED_TOTAL_WEIGHT = "100";

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

    @Test
    void realCorpus1358_parsesToAutoApproved_andPersistsEveryItem() throws Exception {
        Path snapshot = Path.of(System.getenv("XIYU_EVAL_CORPUS_DIR"))
                .resolve("incident-1358").resolve("candidates.json");
        Assumptions.assumeTrue(Files.isRegularFile(snapshot),
                "未找到 1358 真实候选快照，跳过（按类 javadoc 录制后放到该路径再运行）: " + snapshot);

        List<ScoreCandidate> realCandidates = new ObjectMapper().readValue(
                snapshot.toFile(), new TypeReference<List<ScoreCandidate>>() {});
        assertThat(realCandidates).as("真实候选快照不得为空").isNotEmpty();

        ScoreParseTask task = ScoreParseTask.builder()
                .id(TASK_PK).taskId(TASK_ID).projectId(PROJECT_ID)
                .taskType("PARSE").status("PENDING").build();
        when(taskRepository.findByTaskId(TASK_ID)).thenReturn(Optional.of(task));
        when(initiationTenderTextResolver.resolveIntake(PROJECT_ID))
                .thenReturn(TenderIntake.found(new TenderTextSource(
                        "tender.docx", "doc-insight://t/1358", "评分办法：总分100分。")));
        when(scoreAnalyzer.recallCandidates(anyString(), isNull(), any()))
                .thenReturn(new RecallOutcome(realCandidates, 0));

        appService.executeParseAsync(TASK_ID);

        // 真实 1358 数据上，漏识别症状的反面：AUTO_APPROVED、无复核标记
        verify(stateService).markCompleted(TASK_ID);
        assertThat(task.getDecisionStatus()).isEqualTo("AUTO_APPROVED");
        assertThat(task.getFallbackReason()).isNull();

        // 11 项无一漏识别，全部落库；权重满分 100 闭环；触发阶段 1 预计得分
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ScoreCandidate>> persisted = ArgumentCaptor.forClass(List.class);
        verify(itemPersistenceService).persistItems(eq(PROJECT_ID), eq(TASK_PK), persisted.capture());
        assertThat(persisted.getValue()).hasSize(EXPECTED_ITEM_COUNT);
        BigDecimal persistedTotal = persisted.getValue().stream()
                .map(ScoreCandidate::weight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(persistedTotal).isEqualByComparingTo(EXPECTED_TOTAL_WEIGHT);
        verify(estimatedScoreService).estimateForProject(PROJECT_ID);
    }
}
