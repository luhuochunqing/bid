// Input: projectId（触发打分）/ taskId（异步执行）
// Output: ScoreParseTriggerDTO（触发）/ score_result 落库（执行）
// Pos: scoreparse/application — 阶段 2 实际打分编排（spec 041 US4 / 044 花费守卫）
// 维护声明: 维护者按项目SOP；FR-012/FR-019/FR-021；spec 031 异步四件套范式；结果装配拆至 ScoreScoringResultsAssembler（2026-08-19 满足 300 行预算）
package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.scoreparse.domain.AutoFailCircuit;
import com.xiyu.bid.scoreparse.domain.BidChapterDirtySet;
import com.xiyu.bid.scoreparse.domain.BidScoreSkipPolicy;
import com.xiyu.bid.scoreparse.domain.ImageBudget;
import com.xiyu.bid.scoreparse.dto.ScoreParseProgressDTO;
import com.xiyu.bid.scoreparse.dto.ScoreParseTriggerDTO;
import com.xiyu.bid.scoreparse.dto.ScoreScoringCommand;
import com.xiyu.bid.scoreparse.dto.ScoreScoringResultsDTO;
import com.xiyu.bid.scoreparse.entity.ScoreItem;
import com.xiyu.bid.scoreparse.entity.ScoreParseTask;
import com.xiyu.bid.scoreparse.entity.ScoreResult;
import com.xiyu.bid.scoreparse.infrastructure.openai.OpenAiScoreAnalyzer;
import com.xiyu.bid.scoreparse.repository.ScoreItemRepository;
import com.xiyu.bid.scoreparse.repository.ScoreParseTaskRepository;
import com.xiyu.bid.scoreparse.repository.ScoreResultRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Version-bound scoring orchestration; slow model work runs outside database transactions. */
@Service
@Slf4j
public class ScoreScoringAppService {

    private static final String TASK_TYPE_SCORING = "SCORING";
    private static final List<String> ACTIVE_STATUSES = List.of("PENDING", "PROCESSING");
    private final ScoreParseTaskRepository taskRepository;
    private final ScoreItemRepository itemRepository;
    private final ScoreResultRepository resultRepository;
    private final ScoreParseTaskStateService stateService;
    private final ScoreParseProgressService progressService;
    private final ProjectAccessScopeService projectAccessScopeService;
    private final OpenAiScoreAnalyzer scoreAnalyzer;
    private final BidFileLoadingCoordinator bidFileLoader;
    private final ScoreResultOverwriter resultOverwriter;
    private final ImageBudget imageBudget;
    private final ScoreScoringResultsAssembler resultsAssembler = new ScoreScoringResultsAssembler();
    private final ScoreScoringItemPicker itemPicker = new ScoreScoringItemPicker();

    @Autowired(required=false)
    private ScoreScoringVersionService versions;

    @Lazy
    @Autowired
    private ScoreScoringAppService self;

    @Autowired
    public ScoreScoringAppService(
            ScoreParseTaskRepository taskRepository, ScoreItemRepository itemRepository,
            ScoreResultRepository resultRepository,
            ScoreParseTaskStateService stateService, ScoreParseProgressService progressService,
            ProjectAccessScopeService projectAccessScopeService, OpenAiScoreAnalyzer scoreAnalyzer,
            BidFileLoadingCoordinator bidFileLoader, ScoreResultOverwriter resultOverwriter,
            @Value("${app.score-parse.multimodal.max-images-per-item:3}") int multimodalMaxImagesPerItem,
            @Value("${app.score-parse.multimodal.max-image-bytes-per-item:5242880}") long multimodalMaxImageBytesPerItem
    ) {
        this.taskRepository = taskRepository;
        this.itemRepository = itemRepository;
        this.resultRepository = resultRepository;
        this.stateService = stateService;
        this.progressService = progressService;
        this.projectAccessScopeService = projectAccessScopeService;
        this.scoreAnalyzer = scoreAnalyzer;
        this.bidFileLoader = bidFileLoader;
        this.resultOverwriter = resultOverwriter;
        // spec 048 T019：图片配给预算从 app.score-parse.multimodal.* 解析，经 ScoreItemAssessor 构造器注入
        this.imageBudget = new ImageBudget(multimodalMaxImagesPerItem, multimodalMaxImageBytesPerItem);
    }

    public ScoreParseTriggerDTO triggerScoring(Long projectId) {
        return triggerScoring(projectId, ScoreScoringCommand.defaults());
    }

    public ScoreParseTriggerDTO triggerScoring(Long projectId, ScoreScoringCommand command) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        if (versions != null) return versions.prepare(projectId, () -> triggerLocked(projectId, command));
        return triggerLocked(projectId, command);
    }

    private ScoreParseTriggerDTO triggerLocked(Long projectId, ScoreScoringCommand command) {
        var snapshot = versions == null ? null : versions.lockedSnapshot(projectId);
        ScoreScoringCommand cmd = command == null ? ScoreScoringCommand.defaults() : command;
        List<ScoreItem> items = snapshot == null ? itemRepository.findByProjectIdOrderByItemIndexAsc(projectId) : snapshot.items();
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
        String itemHash = snapshot != null ? snapshot.fingerprint() : BidScoreSkipPolicy.hashItems(items.stream()
                .map(item -> BidScoreSkipPolicy.itemFingerprint(item.getId(), item.getWeight(), item.getDetail()))
                .toList());
        ScoreParseTask last = latestCompletedScoring(projectId);
        if ("ALL".equals(cmd.normalizedScope()) && (snapshot == null || snapshot.results().size() == items.size())
                && last != null && bidDoc.getFileUrl() != null && bidDoc.getFileUrl().equals(last.getFileUrl())
                && itemHash != null && itemHash.equals(last.getItemSetHash())) {
            return completeSkipped(projectId, bidDoc, last.getBidContentHash(), itemHash, cmd.normalizedSource());
        }

        ScoreParseTask created = stateService.createTask(
                taskId, projectId, TASK_TYPE_SCORING, bidDoc.getName(), bidDoc.getFileUrl(), cmd.normalizedSource());
        created.setStage(cmd.normalizedScope());
        if (versions != null) created.setPipelineVersion("SCORING_VERSION_V1");
        if (last != null && bidDoc.getFileUrl() != null && bidDoc.getFileUrl().equals(last.getFileUrl())) {
            created.setBidContentHash(last.getBidContentHash());
        }
        created.setItemSetHash(itemHash);
        if ("ITEMS".equals(cmd.normalizedScope()) && cmd.itemIds() != null && !cmd.itemIds().isEmpty()) {
            created.setChapterHashes("IDS:" + cmd.itemIds().stream().map(String::valueOf)
                    .collect(Collectors.joining(",")));
        }
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
            if (versions == null) stateService.failTask(taskId, "打分失败，请重试");
            else taskRepository.findByTaskId(taskId).ifPresent(t -> versions.finish(t, null, "打分失败，请重试"));
        } finally {
            progressService.clearProgress(taskId);
        }
    }

    private void doExecuteScoring(String taskId) {
        var snapshot = versions == null ? null : versions.start(taskId);
        if (versions != null && snapshot == null) return;
        if (versions == null) stateService.markProcessing(taskId);
        ScoreParseTask task = taskRepository.findByTaskId(taskId)
                .orElseThrow(() -> new IllegalStateException("任务不存在: " + taskId));
        List<ScoreItem> items = snapshot == null ? itemRepository.findByProjectIdOrderByItemIndexAsc(task.getProjectId()) : snapshot.items();
        updateProgress(taskId, 5, "读取投标文件");
        BidFileLoadingCoordinator.LoadedBidDocument loaded;
        try {
            loaded = bidFileLoader.load(task.getProjectId(), taskId);
        } catch (RuntimeException ex) {
            // 兜底文案不给用户看堆栈，但运维必须能看到根因（045 T025 排查教训：异常被吞导致盲飞）
            log.warn("投标文件加载/提取失败: taskId={}, projectId={}, type={}, msg={}",
                    taskId, task.getProjectId(), ex.getClass().getSimpleName(), ex.getMessage(), ex);
            failScoringWithPending(taskId, task, items, ex instanceof OversizedBidTextException
                    || ex instanceof BidFileQuotaException || ex instanceof BidTextExtractionException ? ex.getMessage()
                    : "投标文件解析失败，无法完成打分，请检查文件内容或重新上传");
            return;
        }
        task.setBidContentHash(loaded.contentHash());
        ScoreParseTask last = latestCompletedScoring(task.getProjectId());
        if ("ALL".equals(task.getStage()) && (snapshot == null || snapshot.results().size() == items.size())
                && last != null && BidScoreSkipPolicy.shouldSkip(loaded.contentHash(), task.getItemSetHash(),
                last.getBidContentHash(), last.getItemSetHash())) {
            task.setStatus("COMPLETED");
            task.setProgress(100);
            task.setStage("SKIPPED");
            task.setCompletedAt(java.time.LocalDateTime.now());
            if (versions != null) versions.finish(task, null, null);
            else { taskRepository.save(task); stateService.markCompleted(taskId); }
            return;
        }
        String bidDocText = loaded.text();
        Map<Long, ScoreResult> oldResults = snapshot != null ? snapshot.results() : resultRepository
                .findByScoreItemIdIn(items.stream().map(ScoreItem::getId).toList())
                .stream().collect(Collectors.toMap(ScoreResult::getScoreItemId, Function.identity(), (a, b) -> a));
        var previousStatuses = new java.util.HashMap<Long, String>();
        if (snapshot != null) previousStatuses.putAll(snapshot.previousStatuses());
        else oldResults.forEach((id, result) -> previousStatuses.put(id, result.getStatusStage2()));
        ScoreScoringItemPicker.Plan plan = itemPicker.plan(
                items, oldResults, bidDocText, last != null && java.util.Objects.equals(task.getItemSetHash(),last.getItemSetHash()) ? last : null,
                task.getStage(), parseItemIds(task.getChapterHashes()), previousStatuses);
        ScoreItemAssessor assessor = new ScoreItemAssessor(scoreAnalyzer, imageBudget);
        List<ScoreResult> results = new ArrayList<>(items.size());
        java.util.Set<Long> assessIds = plan.toAssess().stream().map(ScoreItem::getId).collect(Collectors.toSet());
        for (int i = 0; i < items.size(); i++) {
            ScoreItem item = items.get(i);
            if (!assessIds.contains(item.getId())) {
                results.add(oldResults.containsKey(item.getId()) ? assessor.reuse(task, oldResults.get(item.getId()))
                        : assessor.fallbackPending(task, List.of(item), "本次未选择此项，暂无当前规则评分").get(0));
                continue;
            }
            updateProgress(taskId, 10 + 80 * (i + 1) / items.size(),
                    "对标打分 " + (i + 1) + "/" + items.size());
            results.add(assessor.assess(task, item, bidDocText, loaded.images()));
        }
        task.setStage(plan.stageToken());
        task.setChapterHashes(BidChapterHashCodec.encode(
                BidChapterDirtySet.toHashMap(BidChapterDirtySet.split(bidDocText))));
        updateProgress(taskId, 95, "打分结果落库");
        if (versions != null && !versions.finish(task, results, null)) return;
        if (versions == null) { taskRepository.save(task); resultOverwriter.replaceAtomically(items.stream().map(ScoreItem::getId).toList(), results); stateService.markCompleted(taskId); }
        log.info("打分任务完成: taskId={}, items={}", taskId, results.size());
    }

    public ScoreParseProgressDTO getStatus(Long projectId) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        return progressService.getProgress(latestTask(projectId).getTaskId());
    }

    public ScoreScoringResultsDTO getResults(Long projectId) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        if (versions != null) return versions.read(projectId);
        List<ScoreItem> items = itemRepository.findByProjectIdOrderByItemIndexAsc(projectId);
        Map<Long, ScoreResult> resultMap = resultRepository
                .findByScoreItemIdIn(items.stream().map(ScoreItem::getId).toList())
                .stream().collect(Collectors.toMap(ScoreResult::getScoreItemId, Function.identity(), (a, b) -> a));
        return resultsAssembler.assemble(items, resultMap);
    }

    private ScoreParseTriggerDTO completeSkipped(Long projectId, ProjectDocument bidDoc,
                                                 String bidHash, String itemHash, String source) {
        String taskId = UUID.randomUUID().toString();
        ScoreParseTask created = stateService.createTask(
                taskId, projectId, TASK_TYPE_SCORING, bidDoc.getName(), bidDoc.getFileUrl(), source);
        if (versions != null) created.setPipelineVersion("SCORING_VERSION_V1");
        created.setStatus("COMPLETED");
        created.setProgress(100);
        created.setStage("SKIPPED");
        created.setBidContentHash(bidHash);
        created.setItemSetHash(itemHash);
        created.setCompletedAt(java.time.LocalDateTime.now());
        taskRepository.save(created);
        return new ScoreParseTriggerDTO(taskId, "COMPLETED", "SKIPPED", "文件未变化");
    }

    private void failScoringWithPending(String taskId, ScoreParseTask task, List<ScoreItem> items, String message) {
        // 原子覆盖走独立组件拿事务（@Async 线程无事务，AppService 内直调派生 delete 会抛 TransactionRequiredException）
        var pending = new ScoreItemAssessor(scoreAnalyzer, imageBudget).fallbackPending(task, items, message);
        if (versions != null) versions.finish(task, pending, message);
        else { resultOverwriter.replaceAtomically(items.stream().map(ScoreItem::getId).toList(), pending); stateService.failTask(taskId, message); }
    }

    private ScoreParseTask latestCompletedScoring(Long projectId) {
        return scoringTasks(projectId, List.of("COMPLETED"))
                .stream().max(Comparator.comparing(ScoreParseTask::getId)).orElse(null);
    }

    private List<ScoreParseTask> scoringTasks(Long projectId, List<String> statuses) {
        return versions != null && org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()
                ? versions.lockedTasks(projectId, statuses) : taskRepository.findByProjectIdAndTaskTypeAndStatusIn(projectId, TASK_TYPE_SCORING, statuses);
    }

    private List<Long> parseItemIds(String raw) {
        if (raw == null || !raw.startsWith("IDS:")) return List.of();
        List<Long> ids = new ArrayList<>();
        for (String part : raw.substring(4).split(",")) {
            if (!part.isBlank()) ids.add(Long.valueOf(part.trim()));
        }
        return ids;
    }

    private ScoreParseTask latestTask(Long projectId) {
        return taskRepository.findByProjectIdAndTaskTypeAndStatusIn(
                        projectId, TASK_TYPE_SCORING, List.of("PENDING", "PROCESSING", "COMPLETED", "FAILED"))
                .stream().max(Comparator.comparing(ScoreParseTask::getId))
                .orElseThrow(() -> new IllegalStateException("项目无打分任务: " + projectId));
    }

    private void updateProgress(String taskId, int progress, String stageText) {
        stateService.updateProgress(taskId, progress, stageText);
        progressService.updateProgress(taskId, new ScoreParseProgressDTO(
                taskId, "PROCESSING", progress, stageText, null, null, null));
    }
}
