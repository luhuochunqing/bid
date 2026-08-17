// Input: projectId（触发打分）/ taskId（异步执行）
// Output: ScoreParseTriggerDTO（触发）/ score_result 落库（执行）
// Pos: scoreparse/application — 阶段 2 实际打分编排（spec 041 US4 / 044 花费守卫）
// 维护声明: 维护者按项目SOP；FR-012/FR-019/FR-021；spec 031 异步四件套范式
package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.biddraftagent.application.TenderDocumentStorage;
import com.xiyu.bid.biddraftagent.application.TenderDocumentTextExtractor;
import com.xiyu.bid.scoreparse.domain.AutoFailCircuit;
import com.xiyu.bid.scoreparse.domain.BidChapterDirtySet;
import com.xiyu.bid.scoreparse.domain.BidScoreSkipPolicy;
import com.xiyu.bid.scoreparse.domain.SummaryAggregator;
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
import com.xiyu.bid.file.application.ObsShareUrlSigner;
import com.xiyu.bid.projectworkflow.service.ProjectDocumentFileStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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
    private final ProjectDocumentRepository projectDocumentRepository;
    private final TenderDocumentStorage documentStorage;
    private final TenderDocumentTextExtractor textExtractor;
    private final ScoreParseTaskStateService stateService;
    private final ScoreParseProgressService progressService;
    private final ProjectAccessScopeService projectAccessScopeService;
    private final OpenAiScoreAnalyzer scoreAnalyzer;
    private final ProjectDocumentFileStorage fileStorage;
    private final ObsShareUrlSigner obsShareUrlSigner;
    private final SummaryAggregator summaryAggregator = new SummaryAggregator();
    private final ScoreScoringItemPicker itemPicker = new ScoreScoringItemPicker();

    @Lazy
    @Autowired
    private ScoreScoringAppService self;

    @Autowired
    public ScoreScoringAppService(
            ScoreParseTaskRepository taskRepository, ScoreItemRepository itemRepository,
            ScoreResultRepository resultRepository, ProjectDocumentRepository projectDocumentRepository,
            TenderDocumentStorage documentStorage, TenderDocumentTextExtractor textExtractor,
            ScoreParseTaskStateService stateService, ScoreParseProgressService progressService,
            ProjectAccessScopeService projectAccessScopeService, OpenAiScoreAnalyzer scoreAnalyzer,
            ProjectDocumentFileStorage fileStorage, ObsShareUrlSigner obsShareUrlSigner
    ) {
        this.taskRepository = taskRepository;
        this.itemRepository = itemRepository;
        this.resultRepository = resultRepository;
        this.projectDocumentRepository = projectDocumentRepository;
        this.documentStorage = documentStorage;
        this.textExtractor = textExtractor;
        this.stateService = stateService;
        this.progressService = progressService;
        this.projectAccessScopeService = projectAccessScopeService;
        this.scoreAnalyzer = scoreAnalyzer;
        this.fileStorage = fileStorage;
        this.obsShareUrlSigner = obsShareUrlSigner;
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
        ScoreBidDocumentLookup docs = docs();
        List<ProjectDocument> bidDocs = docs.findBidDocuments(projectId);
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
        byte[] bytes = docs.loadBytes(bidDoc.getFileUrl());
        String bidHash = BidScoreSkipPolicy.hashBytes(bytes);
        String itemHash = BidScoreSkipPolicy.hashItems(items.stream()
                .map(item -> BidScoreSkipPolicy.itemFingerprint(item.getId(), item.getWeight(), item.getDetail()))
                .toList());
        ScoreParseTask last = latestCompletedScoring(projectId);
        if (BidScoreSkipPolicy.shouldSkip(bidHash, itemHash,
                last == null ? null : last.getBidContentHash(), last == null ? null : last.getItemSetHash())) {
            return completeSkipped(projectId, bidDoc, bidHash, itemHash, cmd.normalizedSource());
        }
        String taskId = UUID.randomUUID().toString();
        ScoreParseTask created = stateService.createTask(
                taskId, projectId, TASK_TYPE_SCORING, bidDoc.getName(), bidDoc.getFileUrl(), cmd.normalizedSource());
        created.setStage(cmd.normalizedScope());
        created.setBidContentHash(bidHash);
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
        String bidDocText;
        try {
            bidDocText = docs().loadText(task.getProjectId());
        } catch (RuntimeException ex) {
            String prdMsg = "投标文件解析失败，无法完成打分，请检查文件内容或重新上传";
            log.warn("投标文件解析失败，写入全员待确认: taskId={}, msg={}", taskId, ex.getMessage());
            resultRepository.deleteByScoreItemIdIn(items.stream().map(ScoreItem::getId).toList());
            resultRepository.saveAll(new ScoreItemAssessor(scoreAnalyzer).fallbackPending(task, items, prdMsg));
            stateService.failTask(taskId, prdMsg);
            progressService.clearProgress(taskId);
            return;
        }
        Map<Long, ScoreResult> oldResults = resultRepository
                .findByScoreItemIdIn(items.stream().map(ScoreItem::getId).toList())
                .stream().collect(Collectors.toMap(ScoreResult::getScoreItemId, Function.identity(), (a, b) -> a));
        ScoreScoringItemPicker.Plan plan = itemPicker.plan(
                items, oldResults, bidDocText, latestCompletedScoring(task.getProjectId()),
                task.getStage(), parseItemIds(task.getChapterHashes()));
        ScoreItemAssessor assessor = new ScoreItemAssessor(scoreAnalyzer);
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
            results.add(assessor.assess(task, item, bidDocText));
        }
        task.setStage(plan.stageToken());
        task.setChapterHashes(BidChapterHashCodec.encode(
                BidChapterDirtySet.toHashMap(BidChapterDirtySet.split(bidDocText))));
        taskRepository.save(task);
        updateProgress(taskId, 95, "打分结果落库");
        resultRepository.deleteByScoreItemIdIn(items.stream().map(ScoreItem::getId).toList());
        resultRepository.saveAll(results);
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
        if (items.isEmpty()) {
            return new ScoreScoringResultsDTO(List.of(), new ScoreScoringResultsDTO.Summary(
                    BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, false));
        }
        Map<Long, ScoreResult> resultMap = resultRepository
                .findByScoreItemIdIn(items.stream().map(ScoreItem::getId).toList())
                .stream().collect(Collectors.toMap(ScoreResult::getScoreItemId, Function.identity(), (a, b) -> a));
        List<ScoreScoringResultsDTO.ScoreResultDTO> resultDTOs = items.stream()
                .map(item -> toResultDTO(item, resultMap.get(item.getId()))).toList();
        SummaryAggregator.Result summary = summaryAggregator.aggregate(items.stream()
                .map(item -> new SummaryAggregator.Item(item.getWeight(), item.getScoreType(),
                        resultMap.get(item.getId()) == null ? null : resultMap.get(item.getId()).getActualScore(),
                        resultMap.get(item.getId()) == null ? null : resultMap.get(item.getId()).getStatusStage2()))
                .toList());
        return new ScoreScoringResultsDTO(resultDTOs, new ScoreScoringResultsDTO.Summary(
                summary.totalWeight(), summary.totalEstScore(),
                summary.okCount(), summary.dangerCount(), summary.pendingCount(),
                summary.objectiveWeight(), summary.subjectiveWeight(), summary.weightWarning()));
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

    private ScoreBidDocumentLookup docs() {
        return new ScoreBidDocumentLookup(projectDocumentRepository, documentStorage, textExtractor, fileStorage, obsShareUrlSigner);
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

    private ScoreScoringResultsDTO.ScoreResultDTO toResultDTO(ScoreItem item, ScoreResult result) {
        return new ScoreScoringResultsDTO.ScoreResultDTO(
                item.getId(), item.getCode(), item.getDim(), item.getDetail(), item.getWeight(), item.getScoreType(),
                result == null ? null : result.getStatusStage2(), result == null ? null : result.getActualScore(),
                result == null ? null : result.getEvidence(), result == null ? null : result.getQuote(),
                result == null ? null : result.getMissedReason(), result == null ? null : result.getSuggestion(),
                result == null ? null : result.getMatchRatio(), result == null ? null : result.getReuseKind());
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
