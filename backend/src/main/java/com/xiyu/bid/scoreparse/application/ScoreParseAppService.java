// Input: projectId（触发解析）/ taskId（异步执行）
// Output: ScoreParseTriggerDTO / ScoreParseProgressDTO / ScoreParseItemsDTO
// Pos: scoreparse/application — 解析编排（spec 041 US1）
// 维护声明: 维护者按项目SOP；spec 031 异步四件套范式（@Async + DB + Redis + 自代理）
package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.biddraftagent.repository.BidTenderDocumentSnapshotRepository;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.scoreparse.domain.ItemCountCheck;
import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import com.xiyu.bid.scoreparse.domain.ScoreItemMergePolicy;
import com.xiyu.bid.scoreparse.domain.SummaryAggregator;
import com.xiyu.bid.scoreparse.domain.WeightSumCheck;
import com.xiyu.bid.scoreparse.dto.ScoreParseItemsDTO;
import com.xiyu.bid.scoreparse.dto.ScoreParseProgressDTO;
import com.xiyu.bid.scoreparse.dto.ScoreParseTriggerDTO;
import com.xiyu.bid.scoreparse.dto.ScoreItemDTO;
import com.xiyu.bid.scoreparse.entity.ScoreItem;
import com.xiyu.bid.scoreparse.entity.ScoreParseTask;
import com.xiyu.bid.scoreparse.infrastructure.openai.OpenAiScoreAnalyzer;
import com.xiyu.bid.scoreparse.repository.ScoreItemRepository;
import com.xiyu.bid.scoreparse.repository.ScoreParseTaskRepository;
import com.xiyu.bid.scoreparse.repository.ScoreResultRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
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
    private final ScoreItemMergePolicy mergePolicy = new ScoreItemMergePolicy();
    private final WeightSumCheck weightSumCheck = new WeightSumCheck();
    private final ItemCountCheck itemCountCheck = new ItemCountCheck();
    private final SummaryAggregator summaryAggregator = new SummaryAggregator();

    /** 自身代理（解决 @Async 自调用失效）；@Lazy 避免循环依赖 */
    @Lazy
    @Autowired
    private ScoreParseAppService self;

    /** 触发解析。立项招标文件或历史快照至少一份，否则 400。缺省 MANUAL。 */
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

        // FR-001 四路召回（召回一/二正则+结构、召回三/四 LLM 语义）
        List<ScoreCandidate> candidates = scoreAnalyzer.recallCandidates(
                source.text(), null,
                (progress, stage) -> updateProgress(taskId, progress, "RECALL", stage));

        // FR-004 合并去重 + Validation 丢弃（weight 无法解析为数字的候选丢弃并记日志）
        List<ScoreCandidate> merged = mergePolicy.merge(candidates);
        List<ScoreCandidate> valid = filterInvalidWeights(merged);

        // FR-005/FR-022 权重闭环校验：合计≠100 触发完整性回补
        WeightSumCheck.Result weightResult = weightSumCheck.checkCandidates(valid);
        ItemCountCheck.Result continuityResult = itemCountCheck.checkCandidates(valid);
        if (weightResult.needRecheck() || continuityResult.needRecheck()) {
            updateProgress(taskId, 80, "GAP_RECHECK", "分值或编号连续性异常，触发完整性回补");
            List<ScoreCandidate> missed = scoreAnalyzer.recheckGaps(
                    source.text(), valid);
            if (!missed.isEmpty()) {
                log.info("完整性回补发现遗漏项: taskId={}, missed={}", taskId, missed.size());
                valid = filterInvalidWeights(mergePolicy.merge(concat(valid, missed)));
                weightResult = weightSumCheck.checkCandidates(valid);
            }
        }

        // FR-006/FR-007 数量校验：0 项 → 解析失败终态
        ItemCountCheck.Result countResult = itemCountCheck.check(valid.size());
        if (countResult.failed()) {
            log.warn("解析结果为 0 项，判定失败: taskId={}, candidates={}", taskId, candidates.size());
            stateService.failTask(taskId, countResult.failureMessage());
            progressService.clearProgress(taskId);
            return;
        }

        // FR-003 客观/主观分类 + 覆盖落库（FR-021）
        updateProgress(taskId, 90, "PERSIST", "评分项落库");
        itemPersistenceService.persistItems(task.getProjectId(), task.getId(), valid);

        // US3 阶段 1 预计得分：解析完成后按知识库类别分型匹配回填（FR-011）
        updateProgress(taskId, 95, "ESTIMATE", "阶段 1 预计得分计算");
        estimatedScoreService.estimateForProject(task.getProjectId());

        log.info("解析任务完成: taskId={}, items={}, totalWeight={}, weightWarning={}",
                taskId, valid.size(), weightResult.totalWeight(), weightResult.weightWarning());
        stateService.markCompleted(taskId);
        progressService.clearProgress(taskId);
    }

    /** 查询解析状态（Redis 优先，DB fallback） */
    public ScoreParseProgressDTO getStatus(Long projectId) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        ScoreParseTask task = latestTask(projectId, TASK_TYPE_PARSE);
        return progressService.getProgress(task.getTaskId());
    }

    /** 查询评分项清单 + 汇总统计 + 来源信息栏元数据（FR-017 / FR-022，US3 SummaryAggregator 域核心） */
    public ScoreParseItemsDTO getItems(Long projectId) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        List<ScoreItem> items = itemRepository.findByProjectIdOrderByItemIndexAsc(projectId);
        List<ScoreItemDTO> itemDTOs = items.stream().map(this::toDTO).toList();

        SummaryAggregator.Result summary = summaryAggregator.aggregate(items.stream()
                .map(item -> new SummaryAggregator.Item(
                        item.getWeight(), item.getScoreType(), item.getEstScore(),
                        item.getStatusStage1()))
                .toList());
        return new ScoreParseItemsDTO(itemDTOs, new ScoreParseItemsDTO.Summary(
                summary.totalWeight(), summary.totalEstScore(),
                summary.okCount(), summary.dangerCount(), summary.pendingCount(),
                summary.objectiveWeight(), summary.subjectiveWeight(),
                summary.weightWarning()),
                new ScoreParseItemsMetaBuilder(initiationTenderTextResolver, snapshotRepository,
                        taskRepository, itemRepository, resultRepository,
                        new ScoreBidDocumentLookup(projectDocumentRepository, null, null))
                        .build(projectId, latestTaskOrNull(projectId, TASK_TYPE_PARSE),
                                latestTaskOrNull(projectId, "SCORING")));
    }

    private ScoreParseTask latestTaskOrNull(Long projectId, String taskType) {
        return taskRepository.findByProjectIdAndTaskTypeAndStatusIn(
                        projectId, taskType, List.of("PENDING", "PROCESSING", "COMPLETED", "FAILED"))
                .stream().max(java.util.Comparator.comparing(ScoreParseTask::getId)).orElse(null);
    }

    private ScoreParseTask latestTask(Long projectId, String taskType) {
        List<ScoreParseTask> tasks = taskRepository.findByProjectIdAndTaskTypeAndStatusIn(
                projectId, taskType, List.of("PENDING", "PROCESSING", "COMPLETED", "FAILED"));
        return tasks.stream()
                .max(java.util.Comparator.comparing(ScoreParseTask::getId))
                .orElseThrow(() -> new IllegalStateException("项目无解析任务: " + projectId));
    }

    private List<ScoreCandidate> filterInvalidWeights(List<ScoreCandidate> candidates) {
        List<ScoreCandidate> valid = new ArrayList<>();
        int dropped = 0;
        for (ScoreCandidate candidate : candidates) {
            if (candidate.weight() != null && candidate.weight().compareTo(BigDecimal.ZERO) > 0) {
                valid.add(candidate);
            } else {
                dropped++;
            }
        }
        if (dropped > 0) {
            log.warn("丢弃权重无法解析的候选 {} 项（spec Edge Cases）", dropped);
        }
        return valid;
    }

    private List<ScoreCandidate> concat(List<ScoreCandidate> first, List<ScoreCandidate> second) {
        List<ScoreCandidate> all = new ArrayList<>(first);
        all.addAll(second);
        return all;
    }

    private void updateProgress(String taskId, int progress, String stageKey, String stageText) {
        stateService.updateProgress(taskId, progress, stageText);
        progressService.updateProgress(taskId, new ScoreParseProgressDTO(
                taskId, "PROCESSING", progress, stageText, null, null, null));
    }

    private ScoreItemDTO toDTO(ScoreItem item) {
        return new ScoreItemDTO(
                item.getId(), item.getCode(), item.getDim(), item.getDetail(),
                item.getWeight(), item.getScoreType(), item.getStatusStage1(),
                item.getEstScore(), item.getEstBasis(), item.getKbHit(), item.getLocation());
    }
}
