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

/**
 * 阶段 2 投标文件实际打分应用服务（spec 041 US4 / 044 编排层）。
 */
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
        ScoreScoringCommand cmd = command == null ? ScoreScoringCommand.defaults() : command;
        List<ScoreItem> items = itemRepository.findByProjectIdOrderByItemIndexAsc(projectId);
        if (items.isEmpty()) {
            throw new IllegalArgumentException("SCORE_ITEMS_NOT_READY");
        }
        List<ProjectDocument> bidDocs = bidFileLoader.findBidDocuments(projectId);
        if (bidDocs.isEmpty()) {
            throw new IllegalArgumentException("NO_BID_DOCUMENT");
        }
        List<ScoreParseTask> active = taskRepository.findByProjectIdAndTaskTypeAndStatusIn(
                projectId, TASK_TYPE_SCORING, ACTIVE_STATUSES);
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
        String itemHash = BidScoreSkipPolicy.hashItems(items.stream()
                .map(item -> BidScoreSkipPolicy.itemFingerprint(item.getId(), item.getWeight(), item.getDetail()))
                .toList());
        ScoreParseTask last = latestCompletedScoring(projectId);
        // 快速指纹预检：若投标文件 URL 与评分项集均与上次成功任务一致，无需下载直接跳过（0ms 响应，零双重下载）
        if (last != null && bidDoc.getFileUrl() != null && bidDoc.getFileUrl().equals(last.getFileUrl())
                && itemHash != null && itemHash.equals(last.getItemSetHash())) {
            return completeSkipped(projectId, bidDoc, last.getBidContentHash(), itemHash, cmd.normalizedSource());
        }

        ScoreParseTask created = stateService.createTask(
                taskId, projectId, TASK_TYPE_SCORING, bidDoc.getName(), bidDoc.getFileUrl(), cmd.normalizedSource());
        created.setStage(cmd.normalizedScope());
        if (last != null && bidDoc.getFileUrl() != null && bidDoc.getFileUrl().equals(last.getFileUrl())) {
            created.setBidContentHash(last.getBidContentHash());
        }
        created.setItemSetHash(itemHash);
        if ("ITEMS".equals(cmd.normalizedScope()) && cmd.itemIds() != null && !cmd.itemIds().isEmpty()) {
            created.setChapterHashes("IDS:" + cmd.itemIds().stream().map(String::valueOf)
                    .collect(Collectors.joining(",")));
        }
        taskRepository.save(created);
        self.executeScoringAsync(taskId);
        return new ScoreParseTriggerDTO(taskId, "PENDING");
    }

    @Async("scoreParseExecutor")
    public void executeScoringAsync(String taskId) {
        try {
            doExecuteScoring(taskId);
        } catch (RuntimeException exception) {
            log.error("打分任务异常终止: taskId={}", taskId, exception);
            stateService.failTask(taskId, "打分失败: " + exception.getMessage());
        }
    }

    private void doExecuteScoring(String taskId) {
        stateService.markProcessing(taskId);
        ScoreParseTask task = taskRepository.findByTaskId(taskId)
                .orElseThrow(() -> new IllegalStateException("任务不存在: " + taskId));
        List<ScoreItem> items = itemRepository.findByProjectIdOrderByItemIndexAsc(task.getProjectId());
        updateProgress(taskId, 5, "读取投标文件");
        BidFileLoadingCoordinator.LoadedBidDocument loaded;
        try {
            loaded = bidFileLoader.load(task.getProjectId(), taskId);
        } catch (RuntimeException ex) {
            // 兜底文案不给用户看堆栈，但运维必须能看到根因（045 T025 排查教训：异常被吞导致盲飞）
            log.warn("投标文件加载/提取失败: taskId={}, projectId={}, type={}, msg={}",
                    taskId, task.getProjectId(), ex.getClass().getSimpleName(), ex.getMessage(), ex);
            failScoringWithPending(taskId, task, items, ex instanceof OversizedBidTextException
                    || ex instanceof BidFileQuotaException ? ex.getMessage()
                    : "投标文件解析失败，无法完成打分，请检查文件内容或重新上传");
            return;
        }
        task.setBidContentHash(loaded.contentHash());
        ScoreParseTask last = latestCompletedScoring(task.getProjectId());
        if (last != null && BidScoreSkipPolicy.shouldSkip(loaded.contentHash(), task.getItemSetHash(),
                last.getBidContentHash(), last.getItemSetHash())) {
            task.setStatus("COMPLETED");
            task.setProgress(100);
            task.setStage("SKIPPED");
            task.setCompletedAt(java.time.LocalDateTime.now());
            taskRepository.save(task);
            stateService.markCompleted(taskId);
            progressService.clearProgress(taskId);
            return;
        }
        String bidDocText = loaded.text();
        Map<Long, ScoreResult> oldResults = resultRepository
                .findByScoreItemIdIn(items.stream().map(ScoreItem::getId).toList())
                .stream().collect(Collectors.toMap(ScoreResult::getScoreItemId, Function.identity(), (a, b) -> a));
        ScoreScoringItemPicker.Plan plan = itemPicker.plan(
                items, oldResults, bidDocText, last,
                task.getStage(), parseItemIds(task.getChapterHashes()));
        ScoreItemAssessor assessor = new ScoreItemAssessor(scoreAnalyzer, imageBudget);
        List<ScoreResult> results = new ArrayList<>(items.size());
        java.util.Set<Long> assessIds = plan.toAssess().stream().map(ScoreItem::getId).collect(Collectors.toSet());
        for (int i = 0; i < items.size(); i++) {
            ScoreItem item = items.get(i);
            if (!assessIds.contains(item.getId()) && oldResults.containsKey(item.getId())) {
                results.add(assessor.reuse(task, oldResults.get(item.getId())));
                continue;
            }
            updateProgress(taskId, 10 + 80 * (i + 1) / items.size(),
                    "对标打分 " + (i + 1) + "/" + items.size());
            results.add(assessor.assess(task, item, bidDocText, loaded.images()));
        }
        task.setStage(plan.stageToken());
        task.setChapterHashes(BidChapterHashCodec.encode(
                BidChapterDirtySet.toHashMap(BidChapterDirtySet.split(bidDocText))));
        taskRepository.save(task);
        updateProgress(taskId, 95, "打分结果落库");
        resultOverwriter.replaceAtomically(items.stream().map(ScoreItem::getId).toList(), results);
        log.info("打分任务完成: taskId={}, items={}", taskId, results.size());
        stateService.markCompleted(taskId);
        progressService.clearProgress(taskId);
    }

    public ScoreParseProgressDTO getStatus(Long projectId) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        return progressService.getProgress(latestTask(projectId).getTaskId());
    }

    public ScoreScoringResultsDTO getResults(Long projectId) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
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
        resultOverwriter.replaceAtomically(
                items.stream().map(ScoreItem::getId).toList(),
                new ScoreItemAssessor(scoreAnalyzer, imageBudget).fallbackPending(task, items, message));
        stateService.failTask(taskId, message);
        progressService.clearProgress(taskId);
    }

    private ScoreParseTask latestCompletedScoring(Long projectId) {
        return taskRepository.findByProjectIdAndTaskTypeAndStatusIn(projectId, TASK_TYPE_SCORING, List.of("COMPLETED"))
                .stream().max(Comparator.comparing(ScoreParseTask::getId)).orElse(null);
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
