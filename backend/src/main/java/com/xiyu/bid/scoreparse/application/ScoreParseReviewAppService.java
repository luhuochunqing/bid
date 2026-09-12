package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.dto.ScoreParseReviewCommand;
import com.xiyu.bid.scoreparse.dto.ScoreParseReviewDTO;
import com.xiyu.bid.scoreparse.entity.ScoreParseEvidence;
import com.xiyu.bid.scoreparse.entity.ScoreParseFact;
import com.xiyu.bid.scoreparse.entity.ScoreParseReview;
import com.xiyu.bid.scoreparse.entity.ScoreParseTask;
import com.xiyu.bid.scoreparse.repository.ScoreParseEvidenceRepository;
import com.xiyu.bid.scoreparse.repository.ScoreParseFactRepository;
import com.xiyu.bid.scoreparse.repository.ScoreParseReviewRepository;
import com.xiyu.bid.scoreparse.repository.ScoreParseTaskRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AI 评分解析人工复核应用服务（spec 059 Phase 5 task3）。
 */
@Service
@RequiredArgsConstructor
public class ScoreParseReviewAppService {

    private static final String TASK_TYPE_PARSE = "PARSE";
    private static final List<String> ANY_STATUS = List.of("PENDING", "PROCESSING", "COMPLETED", "FAILED");
    private static final Set<String> ALLOWED_ACTIONS = Set.of("RETAIN_ORIGINAL", "MANUAL_ADJUST", "EXCLUDE");

    private final ScoreParseTaskRepository taskRepository;
    private final ScoreParseFactRepository factRepository;
    private final ScoreParseEvidenceRepository evidenceRepository;
    private final ScoreParseReviewRepository reviewRepository;
    private final ProjectAccessScopeService projectAccessScopeService;

    @Transactional(readOnly = true)
    public ScoreParseReviewDTO getReviewSnapshot(Long projectId) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        ScoreParseTask task = latestParseTask(projectId);
        List<ScoreParseFact> facts = factRepository.findByParseTaskId(task.getId());
        Map<String, List<ScoreParseEvidence>> evidenceByFactId = evidenceRepository.findByParseTaskId(task.getId())
                .stream()
                .collect(Collectors.groupingBy(ScoreParseEvidence::getFactId));
        List<ScoreParseReview> audits = reviewRepository.findByParseTaskId(task.getId());

        return new ScoreParseReviewDTO(
                projectId,
                task.getId(),
                task.getTaskId(),
                task.getStatus(),
                task.getDecisionStatus(),
                task.getFallbackReason(),
                task.getReviewReason(),
                task.getPipelineVersion(),
                task.getArtifactSha256(),
                facts.stream()
                        .sorted(Comparator.comparing(ScoreParseFact::getId))
                        .map(fact -> toFactDTO(fact, evidenceByFactId.getOrDefault(fact.getFactId(), List.of())))
                        .toList(),
                audits.stream()
                        .sorted(Comparator.comparing(ScoreParseReview::getCreatedAt,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                        .map(this::toAuditDTO)
                        .toList());
    }

    @Transactional
    public ScoreParseReviewDTO submitReview(Long projectId, ScoreParseReviewCommand command, String operatorName) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        ScoreParseTask task = latestParseTask(projectId);
        List<ScoreParseFact> facts = factRepository.findByParseTaskId(task.getId());
        Map<String, ScoreParseFact> factsById = facts.stream()
                .collect(Collectors.toMap(ScoreParseFact::getFactId, Function.identity(), (a, b) -> a));
        List<ScoreParseReviewCommand.Action> actions = command == null ? List.of() : command.actions();
        if (actions == null || actions.isEmpty()) {
            throw new IllegalArgumentException("请至少提交一条复核动作");
        }
        String fallbackReason = normalizeReason(command.reason(), "人工复核");
        for (ScoreParseReviewCommand.Action action : actions) {
            String actionName = action == null ? null : normalizeAction(action.action());
            if (!ALLOWED_ACTIONS.contains(actionName)) {
                throw new IllegalArgumentException("不支持的复核动作: " + (action == null ? null : action.action()));
            }
            ScoreParseFact fact = null;
            if (action.factId() != null && !action.factId().isBlank()) {
                fact = factsById.get(action.factId());
                if (fact == null) {
                    throw new IllegalArgumentException("复核事实不存在: " + action.factId());
                }
            }
            if ("MANUAL_ADJUST".equals(actionName) && action.adjustedWeight() == null) {
                throw new IllegalArgumentException("手工调整必须填写调整后权重");
            }
            reviewRepository.save(ScoreParseReview.builder()
                    .projectId(projectId)
                    .parseTaskId(task.getId())
                    .factId(fact == null ? action.factId() : fact.getFactId())
                    .action(actionName)
                    .originalWeight(fact == null ? null : fact.getWeight())
                    .adjustedWeight(normalizeWeight(action.adjustedWeight()))
                    .reason(normalizeReason(action.reason(), fallbackReason))
                    .operatorName(normalizeOperatorName(operatorName))
                    .build());
        }
        return getReviewSnapshot(projectId);
    }

    private ScoreParseTask latestParseTask(Long projectId) {
        return taskRepository.findByProjectIdAndTaskTypeAndStatusIn(projectId, TASK_TYPE_PARSE, ANY_STATUS)
                .stream()
                .max(Comparator.comparing(ScoreParseTask::getId))
                .orElseThrow(() -> new IllegalStateException("项目无解析任务: " + projectId));
    }

    private ScoreParseReviewDTO.Fact toFactDTO(ScoreParseFact fact, List<ScoreParseEvidence> evidences) {
        return new ScoreParseReviewDTO.Fact(
                fact.getFactId(),
                fact.getCode(),
                fact.getDimension(),
                fact.getWeight(),
                fact.getValidationStatus(),
                fact.getSourceText(),
                fact.getSourceCellIds(),
                fact.getWeightEvidence(),
                fact.getParentFactId(),
                fact.getExtractionMethod(),
                fact.getConfidence(),
                evidences.stream()
                        .sorted(Comparator.comparing(ScoreParseEvidence::getId))
                        .map(evidence -> new ScoreParseReviewDTO.Evidence(
                                evidence.getCellId(),
                                evidence.getKind(),
                                evidence.getQuotedText(),
                                evidence.getStartOffset(),
                                evidence.getEndOffset(),
                                evidence.getExplanation()))
                        .toList());
    }

    private ScoreParseReviewDTO.Audit toAuditDTO(ScoreParseReview review) {
        return new ScoreParseReviewDTO.Audit(
                review.getFactId(),
                review.getAction(),
                review.getOriginalWeight(),
                review.getAdjustedWeight(),
                review.getReason(),
                review.getOperatorId(),
                review.getOperatorName(),
                review.getCreatedAt());
    }

    private String normalizeAction(String action) {
        return action == null ? "" : action.trim().toUpperCase();
    }

    private String normalizeReason(String reason, String fallback) {
        String value = reason == null ? "" : reason.trim();
        return value.isEmpty() ? fallback : value;
    }

    private String normalizeOperatorName(String operatorName) {
        String value = operatorName == null ? "" : operatorName.trim();
        return value.isEmpty() ? "unknown" : value;
    }

    private BigDecimal normalizeWeight(BigDecimal weight) {
        return weight == null ? null : weight.stripTrailingZeros();
    }
}
