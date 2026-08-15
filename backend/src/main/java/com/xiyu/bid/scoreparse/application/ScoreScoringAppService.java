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
import com.xiyu.bid.scoreparse.dto.ScoreScoringResultsDTO;
import com.xiyu.bid.scoreparse.dto.ScoreParseTriggerDTO;
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
import java.util.List;
import java.util.UUID;

/**
 * 阶段 2 投标文件实际打分应用服务（spec 041 US4 编排层）。
 *
 * <p>triggerScoring（同步，&lt;1s）：FR-019 前置校验（标书已上传 + 评分项就绪 +
 * 任务互斥）→ 创建 PENDING 任务 → 自代理触发异步执行。
 * <p>executeScoringAsync（异步，分钟级）：读取投标文件全文 → 逐项 LLM 对标
 * （客观项打分 / 主观项建议）→ {@link ScoreAssessmentGuard} 守卫 →
 * 全部成功后整批覆盖 score_result（FR-021；中途失败不删旧结果，US4 场景 6）。
 *
 * <p>纯核心决策在 domain（ScoreAssessmentGuard/ScoreStatusPolicy），
 * 本类只做编排与持久化（FP-Java Split-First）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScoreScoringAppService {

    private static final String TASK_TYPE_SCORING = "SCORING";
    private static final List<String> ACTIVE_STATUSES = List.of("PENDING", "PROCESSING");
    private static final String BID_DOCUMENT_CATEGORY = "BID_FILE";
    /** LLM 单项上下文节选上限（全文超长时截取，避免 prompt 超限） */
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

    /** 自身代理（解决 @Async 自调用失效）；@Lazy 避免循环依赖 */
    @Lazy
    @Autowired
    private ScoreScoringAppService self;

    /**
     * 触发实际打分（FR-019）。
     * <p>前置校验失败抛 IllegalArgumentException（Controller 转 400 语义）：
     * NO_BID_DOCUMENT（需先上传标书）/ SCORE_ITEMS_NOT_READY（需先完成解析）。
     * 已有进行中打分任务时幂等返回该任务。
     */
    public ScoreParseTriggerDTO triggerScoring(Long projectId) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);

        List<ProjectDocument> bidDocs = projectDocumentRepository
                .findByProjectIdAndFiltersOrderByCreatedAtDesc(
                        projectId, BID_DOCUMENT_CATEGORY, null, null);
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

        // FR-021 整批覆盖：全部 assess 成功后才删旧写新（失败路径不触及旧结果）
        updateProgress(taskId, 95, "PERSIST", "打分结果落库");
        resultRepository.deleteByScoreItemIdIn(
                items.stream().map(ScoreItem::getId).toList());
        resultRepository.saveAll(results);

        log.info("打分任务完成: taskId={}, items={}", taskId, results.size());
        stateService.markCompleted(taskId);
        progressService.clearProgress(taskId);
    }

    private String loadBidDocumentText(Long projectId) {
        ProjectDocument bidDoc = projectDocumentRepository
                .findByProjectIdAndFiltersOrderByCreatedAtDesc(
                        projectId, BID_DOCUMENT_CATEGORY, null, null)
                .stream().findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "投标文件不存在，项目: " + projectId));
        LoadedTenderDocument loaded = documentStorage.loadByFileUrl(bidDoc.getFileUrl())
                .orElseThrow(() -> new IllegalStateException(
                        "投标文件加载失败: " + bidDoc.getFileUrl()));
        ExtractedTenderDocument extracted = textExtractor.extract(
                bidDoc.getName(), null, loaded.content());
        return extracted.text();
    }

    /** 单项对标：LLM 输出 → ScoreAssessmentGuard 守卫 → ScoreResult（状态由 ScoreStatusPolicy 判定） */
    private ScoreResult assessItem(ScoreParseTask task, ScoreItem item, String bidDocText) {
        String excerpt = truncate(bidDocText);
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

    /** 查询打分状态（Redis 优先，DB fallback） */
    public ScoreParseProgressDTO getStatus(Long projectId) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        ScoreParseTask task = latestTask(projectId, TASK_TYPE_SCORING);
        return progressService.getProgress(task.getTaskId());
    }

    /**
     * 查询打分结果（契约 §7）。
     * <p>FR-018：阶段 2 输出不含 kbHit；未打分项 status=null（前端显示未打分）。
     * 汇总复用 {@link com.xiyu.bid.scoreparse.domain.SummaryAggregator}（得分换传 actualScore）。
     */
    public ScoreScoringResultsDTO getResults(Long projectId) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        List<ScoreItem> items = itemRepository.findByProjectIdOrderByItemIndexAsc(projectId);
        if (items.isEmpty()) {
            return new ScoreScoringResultsDTO(List.of(), new ScoreScoringResultsDTO.Summary(
                    BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, 0,
                    BigDecimal.ZERO, BigDecimal.ZERO, false));
        }
        java.util.Map<Long, ScoreResult> resultMap = resultRepository
                .findByScoreItemIdIn(items.stream().map(ScoreItem::getId).toList())
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        ScoreResult::getScoreItemId, java.util.function.Function.identity(),
                        (first, second) -> first));

        List<ScoreScoringResultsDTO.ScoreResultDTO> resultDTOs = items.stream()
                .map(item -> toResultDTO(item, resultMap.get(item.getId())))
                .toList();
        com.xiyu.bid.scoreparse.domain.SummaryAggregator.Result summary =
                summaryAggregator.aggregate(items.stream()
                        .map(item -> new com.xiyu.bid.scoreparse.domain.SummaryAggregator.Item(
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
                .max(java.util.Comparator.comparing(ScoreParseTask::getId))
                .orElseThrow(() -> new IllegalStateException("项目无打分任务: " + projectId));
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= BID_DOC_EXCERPT_MAX_CHARS
                ? text : text.substring(0, BID_DOC_EXCERPT_MAX_CHARS);
    }

    private void updateProgress(String taskId, int progress, String stageKey, String stageText) {
        stateService.updateProgress(taskId, progress, stageText);
        progressService.updateProgress(taskId, new ScoreParseProgressDTO(
                taskId, "PROCESSING", progress, stageText, null, null, null));
    }
}
