// Input: projectId（触发打分）/ taskId（异步执行）
// Output: ScoreParseTriggerDTO（触发）/ score_result 落库（执行）
// Pos: scoreparse/application — 阶段 2 实际打分编排（spec 041 US4 / 044 花费守卫）
// 维护声明: 维护者按项目SOP；FR-012/FR-019/FR-021；spec 031 异步四件套范式；结果装配拆至 ScoreScoringResultsAssembler（2026-08-19 满足 300 行预算）
package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.scoreparse.domain.AutoFailCircuit;
import com.xiyu.bid.scoreparse.domain.ImageBudget;
import com.xiyu.bid.scoreparse.dto.ScoreParseProgressDTO;
import com.xiyu.bid.scoreparse.dto.ScoreParseTriggerDTO;
import com.xiyu.bid.scoreparse.dto.ScoreScoringCommand;
import com.xiyu.bid.scoreparse.dto.ScoreScoringResultsDTO;
import com.xiyu.bid.scoreparse.entity.ScoreItem;
import com.xiyu.bid.scoreparse.entity.ScoreParseTask;
import com.xiyu.bid.scoreparse.infrastructure.openai.OpenAiScoreAnalyzer;
import com.xiyu.bid.scoreparse.repository.ScoreItemRepository;
import com.xiyu.bid.scoreparse.repository.ScoreParseTaskRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** Version-bound scoring orchestration; slow model work runs outside database transactions. */
@Service
@Slf4j
public class ScoreScoringAppService {

    private static final String TASK_TYPE_SCORING = "SCORING";
    private static final List<String> ACTIVE_STATUSES = List.of("PENDING", "PROCESSING");
    private final ScoreParseTaskRepository taskRepository;
    private final ScoreItemRepository itemRepository;
    private final ScoreParseTaskStateService stateService;
    private final ScoreParseProgressService progressService;
    private final ProjectAccessScopeService projectAccessScopeService;
    private final OpenAiScoreAnalyzer scoreAnalyzer;
    private final BidFileLoadingCoordinator bidFileLoader;
    private final ImageBudget imageBudget;

    private final ScoreScoringVersionService versions;

    @Lazy
    @Autowired
    private ScoreScoringAppService self;

    @Autowired
    public ScoreScoringAppService(
            ScoreParseTaskRepository taskRepository, ScoreItemRepository itemRepository,
            ScoreParseTaskStateService stateService, ScoreParseProgressService progressService,
            ProjectAccessScopeService projectAccessScopeService, OpenAiScoreAnalyzer scoreAnalyzer,
            BidFileLoadingCoordinator bidFileLoader, ScoreScoringVersionService versions,
            @Value("${app.score-parse.multimodal.max-images-per-item:3}") int multimodalMaxImagesPerItem,
            @Value("${app.score-parse.multimodal.max-image-bytes-per-item:5242880}") long multimodalMaxImageBytesPerItem
    ) {
        this.taskRepository = taskRepository;
        this.itemRepository = itemRepository;
        this.stateService = stateService;
        this.progressService = progressService;
        this.projectAccessScopeService = projectAccessScopeService;
        this.scoreAnalyzer = scoreAnalyzer;
        this.bidFileLoader = bidFileLoader;
        this.versions = java.util.Objects.requireNonNull(versions, "versions");
        // spec 048 T019：图片配给预算从 app.score-parse.multimodal.* 解析，经 ScoreItemAssessor 构造器注入
        this.imageBudget = new ImageBudget(multimodalMaxImagesPerItem, multimodalMaxImageBytesPerItem);
    }

    public ScoreParseTriggerDTO triggerScoring(Long projectId) {
        return triggerScoring(projectId, ScoreScoringCommand.defaults());
    }

    public ScoreParseTriggerDTO triggerScoring(Long projectId, ScoreScoringCommand command) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        return versions.prepare(projectId, () -> triggerLocked(projectId, command));
    }

    private ScoreParseTriggerDTO triggerLocked(Long projectId, ScoreScoringCommand command) {
        var snapshot = versions.lockedSnapshot(projectId);
        ScoreScoringCommand cmd = command == null ? ScoreScoringCommand.defaults() : command;
        if(!List.of("ALL","ITEMS","UNSATISFIED").contains(cmd.normalizedScope()))
            throw new IllegalArgumentException("INVALID_SCORING_SCOPE");
        String requestReport=new ScoringRunReportCodec().request(cmd);
        List<ScoreItem> items = snapshot.items();
        if (items.isEmpty()) {
            throw new IllegalArgumentException("SCORE_ITEMS_NOT_READY");
        }
        List<ProjectDocument> bidDocs = bidFileLoader.findBidDocuments(projectId);
        if (bidDocs.isEmpty()) {
            throw new IllegalArgumentException("NO_BID_DOCUMENT");
        }
        List<ScoreParseTask> active = scoringTasks(projectId, ACTIVE_STATUSES);
        if (!active.isEmpty()) {
            if ("AUTO".equals(cmd.normalizedSource())) {
                ScoreParseTask existing = active.get(0);
                return new ScoreParseTriggerDTO(existing.getTaskId(), existing.getStatus());
            }
            throw new IllegalStateException("TASK_IN_PROGRESS");
        }
        if ("AUTO".equals(cmd.normalizedSource())
                && new ScoreParseAutoPolicy(taskRepository, itemRepository).circuitOpen(projectId)) {
            throw new IllegalStateException(AutoFailCircuit.OPEN_MESSAGE);
        }
        ProjectDocument bidDoc = bidDocs.get(0);
        String taskId = UUID.randomUUID().toString();
        String itemHash = snapshot.fingerprint();
        ScoreParseTask created = stateService.createTask(
                taskId, projectId, TASK_TYPE_SCORING, bidDoc.getName(), bidDoc.getFileUrl(), cmd.normalizedSource());
        created.setStage(cmd.normalizedScope());
        created.setPipelineVersion("SCORING_VERSION_V1");
        created.setItemSetHash(itemHash);
        if ("ITEMS".equals(cmd.normalizedScope()) && cmd.itemIds() != null && !cmd.itemIds().isEmpty()) {
            created.setChapterHashes("IDS:" + cmd.itemIds().stream().map(String::valueOf)
                    .collect(Collectors.joining(",")));
        }
        if("ITEMS".equals(cmd.normalizedScope()) && (cmd.itemIds()==null || cmd.itemIds().isEmpty()
                || !items.stream().map(ScoreItem::getId).collect(Collectors.toSet()).containsAll(cmd.itemIds())))
            throw new IllegalArgumentException("INVALID_SCORING_ITEM_IDS");
        created.setRunSummary(requestReport);
        taskRepository.save(created);
        ScoreAfterCommit.dispatch(() -> self.executeScoringAsync(taskId));
        return new ScoreParseTriggerDTO(taskId, "PENDING");
    }

    @Async("scoreParseExecutor")
    @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public void executeScoringAsync(String taskId) {
        try {
            doExecuteScoring(taskId);
        } catch (RuntimeException exception) {
            log.error("打分任务异常终止: taskId={}", taskId, exception);
            taskRepository.findByTaskId(taskId).ifPresent(t -> versions.finish(t, null, "打分失败，请重试"));
        } finally {
            progressService.clearProgress(taskId);
        }
    }

    private void doExecuteScoring(String taskId) {
        var snapshot = versions.start(taskId);
        if (snapshot == null) return;
        ScoreParseTask task = taskRepository.findByTaskId(taskId)
                .orElseThrow(() -> new IllegalStateException("任务不存在: " + taskId));
        new ScoreScoringExecution(bidFileLoader, scoreAnalyzer, imageBudget, versions,
                (value, text) -> updateProgress(taskId, value, text)).execute(task, snapshot);
    }

    public ScoreParseProgressDTO getStatus(Long projectId) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        var task=latestTask(projectId);
        return task==null?null:progressService.getProgress(task.getTaskId());
    }

    public ScoreScoringResultsDTO getResults(Long projectId) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        return versions.read(projectId);
    }

    private List<ScoreParseTask> scoringTasks(Long projectId, List<String> statuses) {
        return versions.lockedTasks(projectId, statuses);
    }

    private ScoreParseTask latestTask(Long projectId) {
        return taskRepository.findByProjectIdAndTaskTypeAndStatusIn(
                        projectId, TASK_TYPE_SCORING, List.of("PENDING", "PROCESSING", "COMPLETED", "FAILED"))
                .stream().max(Comparator.comparing(ScoreParseTask::getId))
                .orElse(null);
    }

    private void updateProgress(String taskId, int progress, String stageText) {
        stateService.updateProgress(taskId, progress, stageText);
        progressService.updateProgress(taskId, new ScoreParseProgressDTO(
                taskId, "PROCESSING", progress, stageText, null, null, null));
    }
}
