// Input: projectId（触发打分）/ taskId（异步执行）
// Output: ScoreParseTriggerDTO（触发）/ score_result 落库（执行）
// Pos: scoreparse/application — 阶段 2 实际打分编排（spec 041 US4）
// 维护声明: 维护者按项目SOP；FR-012/FR-019/FR-021；spec 031 异步四件套范式
package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.biddraftagent.application.ExtractedTenderDocument;
import com.xiyu.bid.biddraftagent.application.LoadedTenderDocument;
import com.xiyu.bid.biddraftagent.application.TenderDocumentStorage;
import com.xiyu.bid.biddraftagent.application.TenderDocumentTextExtractor;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.scoreparse.domain.ScoreAssessmentGuard;
import com.xiyu.bid.scoreparse.domain.ScoreStatusPolicy;
import com.xiyu.bid.scoreparse.domain.SummaryAggregator;
import com.xiyu.bid.scoreparse.dto.ScoreParseProgressDTO;
import com.xiyu.bid.scoreparse.dto.ScoreParseTriggerDTO;
import com.xiyu.bid.scoreparse.dto.ScoreScoringResultsDTO;
import com.xiyu.bid.scoreparse.entity.ScoreItem;
import com.xiyu.bid.scoreparse.entity.ScoreParseTask;
import com.xiyu.bid.scoreparse.entity.ScoreResult;
import com.xiyu.bid.scoreparse.infrastructure.openai.OpenAiScoreAnalyzer;
import com.xiyu.bid.scoreparse.infrastructure.openai.ScoreAssessmentOutput;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 阶段 2 投标文件实际打分应用服务（spec 041 US4 编排层）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScoreScoringAppService {

    private static final String TASK_TYPE_SCORING = "SCORING";
    private static final List<String> ACTIVE_STATUSES = List.of("PENDING", "PROCESSING");
    private static final int BID_DOC_EXCERPT_MAX_CHARS = 12000;

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
    private final ScoreAssessmentGuard assessmentGuard = new ScoreAssessmentGuard();
    private final ScoreStatusPolicy statusPolicy = new ScoreStatusPolicy();
    private final SummaryAggregator summaryAggregator = new SummaryAggregator();

    @Lazy
    @Autowired
    private ScoreScoringAppService self;

    /** 触发实际打分（FR-019 前置校验与创建任务） */
    public ScoreParseTriggerDTO triggerScoring(Long projectId) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);

        List<ProjectDocument> bidDocs = findBidDocuments(projectId);
        if (bidDocs.isEmpty()) {
            throw new IllegalArgumentException("NO_BID_DOCUMENT");
        }
        List<ScoreItem> items = itemRepository.findByProjectIdOrderByItemIndexAsc(projectId);
        if (items.isEmpty()) {
            throw new IllegalArgumentException("SCORE_ITEMS_NOT_READY");
        }
        List<ScoreParseTask> activeTasks = taskRepository.findByProjectIdAndTaskTypeAndStatusIn(
                projectId, TASK_TYPE_SCORING, ACTIVE_STATUSES);
        if (!activeTasks.isEmpty()) {
            log.info("项目 {} 已有进行中打分任务 {}，拒绝重复触发", projectId, activeTasks.get(0).getTaskId());
            throw new IllegalStateException("TASK_IN_PROGRESS");
        }

        ProjectDocument bidDoc = bidDocs.get(0);
        String taskId = UUID.randomUUID().toString();
        stateService.createTask(taskId, projectId, TASK_TYPE_SCORING,
                bidDoc.getName(), bidDoc.getFileUrl());
        log.info("创建打分任务: taskId={}, projectId={}, bidDoc={}",
                taskId, projectId, bidDoc.getName());
        self.executeScoringAsync(taskId);
        return new ScoreParseTriggerDTO(taskId, "PENDING");
    }

    /** 异步执行打分（scoreParseExecutor，分钟级 LLM 调用） */
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

        updateProgress(taskId, 5, "READ_BID_DOCUMENT", "读取投标文件");
        String bidDocText = loadBidDocumentText(task.getProjectId());

        List<ScoreItem> items = itemRepository.findByProjectIdOrderByItemIndexAsc(task.getProjectId());
        List<ScoreResult> results = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            ScoreItem item = items.get(i);
            updateProgress(taskId, 10 + 80 * (i + 1) / items.size(), "ASSESS",
                    "对标打分 " + (i + 1) + "/" + items.size());
            results.add(assessItem(task, item, bidDocText));
        }

        // FR-021 整批覆盖：全部 assess 成功后才删旧写新
        updateProgress(taskId, 95, "PERSIST", "打分结果落库");
        resultRepository.deleteByScoreItemIdIn(
                items.stream().map(ScoreItem::getId).toList());
        resultRepository.saveAll(results);

        log.info("打分任务完成: taskId={}, items={}", taskId, results.size());
        stateService.markCompleted(taskId);
        progressService.clearProgress(taskId);
    }

    private List<ProjectDocument> findBidDocuments(Long projectId) {
        List<ProjectDocument> docs = projectDocumentRepository
                .findByProjectIdAndFiltersOrderByCreatedAtDesc(projectId, "BID", null, null);
        if (!docs.isEmpty()) {
            return docs;
        }
        docs = projectDocumentRepository
                .findByProjectIdAndFiltersOrderByCreatedAtDesc(projectId, "BID_FILE", null, null);
        if (!docs.isEmpty()) {
            return docs;
        }
        return projectDocumentRepository
                .findByProjectIdAndFiltersOrderByCreatedAtDesc(projectId, "BID_DOCUMENT", null, null);
    }

    private String loadBidDocumentText(Long projectId) {
        List<ProjectDocument> bidDocs = findBidDocuments(projectId);
        if (bidDocs.isEmpty()) {
            throw new IllegalStateException("投标文件不存在，项目: " + projectId);
        }
        ProjectDocument bidDoc = bidDocs.get(0);
        LoadedTenderDocument loaded = documentStorage.loadByFileUrl(bidDoc.getFileUrl())
                .orElseThrow(() -> new IllegalStateException("投标文件加载失败: " + bidDoc.getFileUrl()));
        ExtractedTenderDocument extracted = textExtractor.extract(
                bidDoc.getName(), null, loaded.content());
        return extracted.text();
    }

    /** 单项对标：LLM 输出 → ScoreAssessmentGuard 守卫 → ScoreResult */
    private ScoreResult assessItem(ScoreParseTask task, ScoreItem item, String bidDocText) {
        String excerpt = ScoreDocExcerptExtractor.extractRelevantExcerpt(
                bidDocText, item, BID_DOC_EXCERPT_MAX_CHARS);
        ScoreAssessmentOutput output;
        if (ScoreAssessmentGuard.TYPE_SUBJECTIVE.equals(item.getScoreType())) {
            output = scoreAnalyzer.assessSubjective(item.getDetail(), excerpt);
        } else {
            output = scoreAnalyzer.assessObjective(item.getDetail(), item.getWeight(), excerpt);
        }

        ScoreAssessmentGuard.Result assessment = assessmentGuard.guard(
                toGuardInput(output), item.getWeight(), item.getScoreType());
        if (assessment.rangeInvalid()) {
            log.warn("AI 得分超出 [0, {}] 区间，置空待确认: itemId={}",
                    item.getWeight(), item.getId());
        }
        if (assessment.subjectiveDropped()) {
            log.info("主观项数字输出已丢弃（SC-003）: itemId={}", item.getId());
        }

        String status = statusPolicy.evaluate(
                assessment.actualScore(), item.getWeight(), item.getScoreType(), false);
        return ScoreResult.builder()
                .scoreItemId(item.getId())
                .scoringTaskId(task.getId())
                .actualScore(assessment.actualScore())
                .statusStage2(status)
                .evidence(assessment.evidence())
                .quote(assessment.quote())
                .missedReason(assessment.missedReason())
                .suggestion(assessment.suggestion())
                .matchRatio(assessment.matchRatio())
                .build();
    }

    private ScoreAssessmentGuard.Input toGuardInput(ScoreAssessmentOutput output) {
        return ScoreAssessmentGuard.Input.builder()
                .actualScore(output.actualScore == null
                        ? null : BigDecimal.valueOf(output.actualScore))
                .matchRatio(output.matchRatio)
                .evidence(output.evidence)
                .quote(output.quote)
                .quoteMissing(output.quoteMissing)
                .missedReason(output.missedReason)
                .suggestion(output.suggestion)
                .build();
    }

    /** 查询打分状态 */
    public ScoreParseProgressDTO getStatus(Long projectId) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        ScoreParseTask task = latestTask(projectId, TASK_TYPE_SCORING);
        return progressService.getProgress(task.getTaskId());
    }

    /** 阶段 2 打分结果查询（契约 §7）：按 item_index 升序，合并 item 基础信息与打分结果 */
    public ScoreScoringResultsDTO getResults(Long projectId) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        List<ScoreItem> items = itemRepository.findByProjectIdOrderByItemIndexAsc(projectId);
        if (items.isEmpty()) {
            return new ScoreScoringResultsDTO(List.of(), new ScoreScoringResultsDTO.Summary(
                    BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, 0,
                    BigDecimal.ZERO, BigDecimal.ZERO, false));
        }
        Map<Long, ScoreResult> resultMap = resultRepository
                .findByScoreItemIdIn(items.stream().map(ScoreItem::getId).toList())
                .stream()
                .collect(Collectors.toMap(
                        ScoreResult::getScoreItemId, Function.identity(),
                        (first, second) -> first));

        List<ScoreScoringResultsDTO.ScoreResultDTO> resultDTOs = items.stream()
                .map(item -> toResultDTO(item, resultMap.get(item.getId())))
                .toList();

        SummaryAggregator.Result summary = summaryAggregator.aggregate(items.stream()
                .map(item -> new SummaryAggregator.Item(
                        item.getWeight(), item.getScoreType(),
                        resultMap.get(item.getId()) == null
                                ? null : resultMap.get(item.getId()).getActualScore(),
                        resultMap.get(item.getId()) == null
                                ? null : resultMap.get(item.getId()).getStatusStage2()))
                .toList());
        return new ScoreScoringResultsDTO(resultDTOs, new ScoreScoringResultsDTO.Summary(
                summary.totalWeight(), summary.totalEstScore(),
                summary.okCount(), summary.dangerCount(), summary.pendingCount(),
                summary.objectiveWeight(), summary.subjectiveWeight(),
                summary.weightWarning()));
    }

    private ScoreScoringResultsDTO.ScoreResultDTO toResultDTO(ScoreItem item, ScoreResult result) {
        return new ScoreScoringResultsDTO.ScoreResultDTO(
                item.getId(), item.getCode(), item.getDim(), item.getDetail(),
                item.getWeight(), item.getScoreType(),
                result == null ? null : result.getStatusStage2(),
                result == null ? null : result.getActualScore(),
                result == null ? null : result.getEvidence(),
                result == null ? null : result.getQuote(),
                result == null ? null : result.getMissedReason(),
                result == null ? null : result.getSuggestion(),
                result == null ? null : result.getMatchRatio());
    }

    private ScoreParseTask latestTask(Long projectId, String taskType) {
        List<ScoreParseTask> tasks = taskRepository.findByProjectIdAndTaskTypeAndStatusIn(
                projectId, taskType, List.of("PENDING", "PROCESSING", "COMPLETED", "FAILED"));
        return tasks.stream()
                .max(Comparator.comparing(ScoreParseTask::getId))
                .orElseThrow(() -> new IllegalStateException("项目无打分任务: " + projectId));
    }

    private void updateProgress(String taskId, int progress, String stageKey, String stageText) {
        stateService.updateProgress(taskId, progress, stageText);
        progressService.updateProgress(taskId, new ScoreParseProgressDTO(
                taskId, "PROCESSING", progress, stageText, null, null, null));
    }
}
