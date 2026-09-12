// Input: projectId（触发解析）/ taskId（异步执行）
// Output: ScoreParseTriggerDTO / ScoreParseProgressDTO / ScoreParseItemsDTO
// Pos: scoreparse/application — 解析编排（spec 041 US1）
package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.biddraftagent.repository.BidTenderDocumentSnapshotRepository;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.scoreparse.domain.RecallOutcome;
import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import com.xiyu.bid.scoreparse.domain.ScoreParseDecision;
import com.xiyu.bid.scoreparse.domain.ScoreTableRecallLane;
import com.xiyu.bid.scoreparse.domain.SummaryAggregator;
import com.xiyu.bid.scoreparse.dto.ScoreParseItemsDTO;
import com.xiyu.bid.scoreparse.dto.ScoreParseProgressDTO;
import com.xiyu.bid.scoreparse.dto.ScoreParseTriggerDTO;
import com.xiyu.bid.scoreparse.dto.ScoreItemDTO;
import com.xiyu.bid.scoreparse.entity.ScoreItem;
import com.xiyu.bid.scoreparse.entity.ScoreParseTask;
import com.xiyu.bid.scoreparse.domain.grid.TableColumnMapping;
import com.xiyu.bid.scoreparse.domain.grid.TableGrid;
import com.xiyu.bid.scoreparse.infrastructure.openai.LlmTableScoreExtractor;
import com.xiyu.bid.scoreparse.infrastructure.openai.OpenAiScoreAnalyzer;
import com.xiyu.bid.scoreparse.infrastructure.openai.ScoreTableSchemaMapper;
import com.xiyu.bid.scoreparse.repository.ScoreItemRepository;
import com.xiyu.bid.scoreparse.repository.ScoreParseTaskRepository;
import com.xiyu.bid.scoreparse.repository.ScoreResultRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 评分标准解析应用服务（spec 041 US1 编排层）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScoreParseAppService {

    private static final String TASK_TYPE_PARSE = "PARSE";
    private static final List<String> ACTIVE_STATUSES = List.of("PENDING", "PROCESSING");
    private static final String NO_SNAPSHOT_MESSAGE = InitiationTenderTextResolver.NO_TENDER_MESSAGE;
    private static final int STRUCTURED_TABLE_MIN_CANDIDATES = 5;

    private final ScoreParseTaskRepository taskRepository;
    private final ScoreItemRepository itemRepository;
    private final ScoreResultRepository resultRepository;
    private final ProjectDocumentRepository projectDocumentRepository;
    private final ScoreParseTaskStateService stateService;
    private final ScoreParseProgressService progressService;
    private final BidTenderDocumentSnapshotRepository snapshotRepository;
    private final ProjectAccessScopeService projectAccessScopeService;
    private final OpenAiScoreAnalyzer scoreAnalyzer;
    private final EstimatedScoreService estimatedScoreService;
    private final ScoreItemPersistenceService itemPersistenceService;
    private final InitiationTenderTextResolver initiationTenderTextResolver;
    private final SidecarTableExtractor sidecarTableExtractor;
    private final ObjectProvider<SidecarDocumentArtifactExtractor> artifactExtractorProvider;
    private final ScoreCandidatePostProcessor postProcessor;
    private final ObjectProvider<LlmTableScoreExtractor> llmTableScoreExtractorProvider;
    private final ObjectProvider<ScoreTableSchemaMapper> schemaMapperProvider;
    private final SummaryAggregator summaryAggregator = new SummaryAggregator();

    /** 自身代理（解决 @Async 自调用失效）；@Lazy 避免循环依赖 */
    @Lazy
    @Autowired
    private ScoreParseAppService self;

    public ScoreParseTriggerDTO triggerParse(Long projectId) {
        return triggerParse(projectId, "MANUAL");
    }

    public ScoreParseTriggerDTO triggerParse(Long projectId, String source) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        return triggerParseInternal(projectId, "AUTO".equalsIgnoreCase(source) ? "AUTO" : "MANUAL");
    }

    /** 事件路径（无用户上下文）；源头已校验项目权限。 */
    public ScoreParseTriggerDTO triggerParseFromEvent(Long projectId) {
        return triggerParseInternal(projectId, "AUTO");
    }

    /** 监听器：从未解析且未熔断才允许自动新建。 */
    public boolean allowAutoParse(Long projectId) {
        return new ScoreParseAutoPolicy(taskRepository, itemRepository)
                .allowAuto(projectId, latestTaskOrNull(projectId, TASK_TYPE_PARSE));
    }

    private ScoreParseTriggerDTO triggerParseInternal(Long projectId, String source) {
        List<ScoreParseTask> activeTasks = taskRepository.findByProjectIdAndTaskTypeAndStatusIn(
                projectId, TASK_TYPE_PARSE, ACTIVE_STATUSES);
        if (!activeTasks.isEmpty()) {
            ScoreParseTask existing = activeTasks.get(0);
            return new ScoreParseTriggerDTO(existing.getTaskId(), existing.getStatus());
        }
        ScoreParseTask latestParse = latestTaskOrNull(projectId, TASK_TYPE_PARSE);
        if ("AUTO".equals(source) && !new ScoreParseAutoPolicy(taskRepository, itemRepository)
                .allowAuto(projectId, latestParse)) {
            return latestParse == null ? null : new ScoreParseTriggerDTO(latestParse.getTaskId(), latestParse.getStatus());
        }
        TenderIntake intake = initiationTenderTextResolver.resolveIntake(projectId);
        if (intake.source().isEmpty()) {
            return rejectUnavailable(projectId, intake.emptyReason(), source);
        }
        String taskId = UUID.randomUUID().toString();
        stateService.createTask(taskId, projectId, TASK_TYPE_PARSE, null, null, source);
        self.executeParseAsync(taskId);
        return new ScoreParseTriggerDTO(taskId, "PENDING");
    }

    private ScoreParseTriggerDTO rejectUnavailable(Long projectId, String reason, String source) {
        String message = reason == null || reason.isBlank() ? NO_SNAPSHOT_MESSAGE : reason;
        String taskId = UUID.randomUUID().toString();
        stateService.createTask(taskId, projectId, TASK_TYPE_PARSE, null, null, source);
        stateService.failTask(taskId, message);
        throw new IllegalArgumentException(message);
    }

    /** 异步执行解析（scoreParseExecutor，分钟级 LLM 调用） */
    @Async("scoreParseExecutor")
    public void executeParseAsync(String taskId) {
        try {
            doExecuteParse(taskId);
        } catch (RuntimeException exception) {
            log.error("解析任务异常终止: taskId={}", taskId, exception);
            stateService.failTask(taskId, "解析失败: " + exception.getMessage());
        }
    }

    private void doExecuteParse(String taskId) {
        stateService.markProcessing(taskId);
        ScoreParseTask task = taskRepository.findByTaskId(taskId)
                .orElseThrow(() -> new IllegalStateException("任务不存在: " + taskId));
        TenderIntake intake = initiationTenderTextResolver.resolveIntake(task.getProjectId());
        if (intake.source().isEmpty()) {
            String reason = intake.emptyReason() == null ? NO_SNAPSHOT_MESSAGE : intake.emptyReason();
            log.warn("读取立项招标文件失败: taskId={}, msg={}", taskId, reason);
            stateService.failTask(taskId, reason);
            progressService.clearProgress(taskId);
            return;
        }
        TenderTextSource source = intake.source().orElseThrow();
        task.setFileName(source.fileName());
        task.setFileUrl(source.fileUrl());
        taskRepository.save(task);
        updateProgress(taskId, 5, "READ_DOCUMENT", "读取招标文件");

        var structuredCandidates = StructuredTableExtractionHandler.extract(
                source, initiationTenderTextResolver,
                artifactExtractorProvider == null ? null : artifactExtractorProvider.getIfAvailable(),
                sidecarTableExtractor);
        if (structuredCandidates.structureIncomplete()) {
            StructuredTableExtractionHandler.markIncomplete(task, taskId, structuredCandidates.failureReason(),
                    taskRepository, stateService, progressService);
            return;
        }
        if (structuredCandidates.evidencePipeline()) {
            StructuredTableExtractionHandler.markEvidencePipelineCompleted(
                    task, taskId, structuredCandidates, taskRepository, stateService,
                    progressService, itemPersistenceService, estimatedScoreService);
            return;
        }
        RecallOutcome outcome = scoreAnalyzer.recallCandidates(source.text(), null,
                (progress, stage) -> updateProgress(taskId, progress, "RECALL", stage));
        task.setSkippedChunks(outcome.skippedChunks());
        List<ScoreCandidate> tableCandidates = structuredCandidates.candidates();
        if (tableCandidates.size() >= STRUCTURED_TABLE_MIN_CANDIDATES) {
            log.info("结构化表格提取命中候选: count={}", tableCandidates.size());
        } else {
            tableCandidates = recallTableCandidates(source.text());
        }
        updateProgress(taskId, 70, "GOVERN", "候选治理、归并裁决与权重收敛");
        var processed = postProcessor.process(taskId, source.text(), outcome, tableCandidates,
                (progress, stageKey) -> updateProgress(taskId, progress, stageKey,
                        "GAP_RECHECK".equals(stageKey) ? "分值或编号连续性异常，触发完整性回补" : stageKey));
        task.setRunSummary(processed.runSummaryJson());
        task.setNormalizationWarning(processed.normalizationWarning());
        task.setPipelineVersion(processed.pipelineVersion());
        task.setDecisionStatus(processed.decisionStatus() == null ? null : processed.decisionStatus().name());
        task.setFallbackReason(processed.fallbackReason());
        if (processed.failMessage() != null) {
            taskRepository.save(task);
            stateService.failTask(taskId, processed.failMessage());
            progressService.clearProgress(taskId);
            return;
        }
        List<ScoreCandidate> valid = processed.candidates();
        if (processed.originalTotal() != null) {
            task.setOriginalTotalWeight(processed.originalTotal());
        }
        taskRepository.save(task);
        // task2 收敛纪律：只有 AUTO_APPROVED 可覆盖旧 score_item；
        // REVIEW_REQUIRED / STRUCTURE_INCOMPLETE / PARSE_FAILED 都保留旧项，避免不完整结果洗掉可用数据。
        if (!valid.isEmpty() && processed.decisionStatus() == ScoreParseDecision.AUTO_APPROVED) {
            updateProgress(taskId, 90, "PERSIST", "评分项落库");
            itemPersistenceService.persistItems(task.getProjectId(), task.getId(), valid);
            updateProgress(taskId, 95, "ESTIMATE", "阶段 1 预计得分计算");
            estimatedScoreService.estimateForProject(task.getProjectId());
        } else {
            log.warn("解析结果未自动通过，保留现有评分项不覆盖: taskId={}, decision={}", taskId, processed.decisionStatus());
        }
        log.info("解析任务完成: taskId={}, items={}, totalWeight={}, decision={}",
                taskId, valid.size(), processed.weightResult().totalWeight(), processed.decisionStatus());
        stateService.markCompleted(taskId);
        progressService.clearProgress(taskId);
    }

    public ScoreParseProgressDTO getStatus(Long projectId) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        ScoreParseTask task = latestTask(projectId, TASK_TYPE_PARSE);
        return progressService.getProgress(task.getTaskId());
    }

    public ScoreParseItemsDTO getItems(Long projectId) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        List<ScoreItem> items = itemRepository.findByProjectIdOrderByItemIndexAsc(projectId);
        List<ScoreItemDTO> itemDTOs = items.stream().map(this::toDTO).toList();

        SummaryAggregator.Result summary = summaryAggregator.aggregate(items.stream()
                .map(item -> new SummaryAggregator.Item(
                        item.getWeight(), item.getScoreType(), item.getEstScore(), item.getStatusStage1()))
                .toList());
        ScoreParseTask latest = latestTaskOrNull(projectId, TASK_TYPE_PARSE);
        return new ScoreParseItemsDTO(itemDTOs, new ScoreParseItemsDTO.Summary(
                summary.totalWeight(), summary.totalEstScore(),
                summary.okCount(), summary.dangerCount(), summary.pendingCount(),
                summary.objectiveWeight(), summary.subjectiveWeight(),
                summary.weightWarning(), latest == null ? null : latest.getOriginalTotalWeight(),
                latest == null ? null : latest.getNormalizationWarning()),
                new ScoreParseItemsMetaBuilder(initiationTenderTextResolver, snapshotRepository,
                        taskRepository, itemRepository, resultRepository,
                        new ScoreBidDocumentLookup(projectDocumentRepository, null, null, null, null))
                        .build(projectId, latest, latestTaskOrNull(projectId, "SCORING")));
    }
    private static final List<String> ANY_STATUS = List.of("PENDING", "PROCESSING", "COMPLETED", "FAILED");
    private ScoreParseTask latestTaskOrNull(Long projectId, String taskType) {
        return taskRepository.findByProjectIdAndTaskTypeAndStatusIn(projectId, taskType, ANY_STATUS)
                .stream().max(java.util.Comparator.comparing(ScoreParseTask::getId)).orElse(null);
    }

    private ScoreParseTask latestTask(Long projectId, String taskType) {
        return taskRepository.findByProjectIdAndTaskTypeAndStatusIn(projectId, taskType, ANY_STATUS)
                .stream().max(java.util.Comparator.comparing(ScoreParseTask::getId))
                .orElseThrow(() -> new IllegalStateException("项目无解析任务: " + projectId));
    }

    private List<ScoreCandidate> recallTableCandidates(String fullText) {
        var sm = schemaMapperProvider != null ? schemaMapperProvider.getIfAvailable() : null;
        java.util.function.Function<TableGrid, TableColumnMapping> mapperFunc = sm != null ? sm::mapSchema : null;

        List<ScoreCandidate> tableCandidates = ScoreTableRecallLane.recall(fullText, mapperFunc);
        if (!tableCandidates.isEmpty()) {
            log.info("表格抽取命中候选: count={}", tableCandidates.size());
            return tableCandidates;
        }

        var extractor = llmTableScoreExtractorProvider != null ? llmTableScoreExtractorProvider.getIfAvailable() : null;
        if (extractor != null) {
            try {
                List<ScoreCandidate> llmCandidates = extractor.extractFromDocument(fullText);
                if (!llmCandidates.isEmpty()) {
                    log.info("LLM 表格抽取产出候选数: count={}", llmCandidates.size());
                    return llmCandidates;
                }
            } catch (RuntimeException e) {
                log.warn("LLM 表格抽取异常: {}", e.getMessage());
            }
        }
        return List.of();
    }

    private void updateProgress(String taskId, int progress, String stageKey, String stageText) {
        stateService.updateProgress(taskId, progress, stageText);
        progressService.updateProgress(taskId, new ScoreParseProgressDTO(
                taskId, "PROCESSING", progress, stageText, null, null, null));
    }

    private ScoreItemDTO toDTO(ScoreItem item) {
        return new ScoreItemDTO(item.getId(), item.getCode(), item.getDim(), item.getDetail(),
                item.getWeight(), item.getScoreType(), item.getStatusStage1(), item.getEstScore(),
                item.getEstBasis(), item.getKbHit(), item.getLocation(), item.getSourceCellIds(),
                item.getWeightEvidence(), item.getExtractionMethod(), item.getConfidence(), item.getValidationStatus(), item.getParentScoreItemId());
    }
}
