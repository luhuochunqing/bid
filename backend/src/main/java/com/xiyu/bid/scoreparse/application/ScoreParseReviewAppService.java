package com.xiyu.bid.scoreparse.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@Service
@RequiredArgsConstructor
public class ScoreParseReviewAppService {

    private static final String TASK_TYPE_PARSE = "PARSE";
    private static final List<String> ANY_STATUS = List.of("PENDING", "PROCESSING", "COMPLETED", "FAILED");
    private static final Set<String> ALLOWED_ACTIONS = Set.of(
            "RETAIN_ORIGINAL", "MANUAL_ADJUST", "EXCLUDE", "ADOPT_CANDIDATES");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ScoreParseTaskRepository taskRepository;
    private final ScoreParseFactRepository factRepository;
    private final ScoreParseEvidenceRepository evidenceRepository;
    private final ScoreParseReviewRepository reviewRepository;
    private final ProjectAccessScopeService projectAccessScopeService;
    private final ScoreItemPersistenceService itemPersistenceService;
    private final EstimatedScoreService estimatedScoreService;
    private final com.xiyu.bid.scoreparse.domain.ScoreReviewAdoptionPolicy adoptionPolicy =
            new com.xiyu.bid.scoreparse.domain.ScoreReviewAdoptionPolicy();

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
                declaredTotal(task),
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

        boolean isAdopt = actions.stream().anyMatch(a -> a != null && "ADOPT_CANDIDATES".equals(normalizeAction(a.action())));
        if (isAdopt) {
            handleAdoptCandidates(projectId, task, facts, actions, fallbackReason, operatorName);
            return getReviewSnapshot(projectId);
        }

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

    private void handleAdoptCandidates(
            Long projectId,
            ScoreParseTask task,
            List<ScoreParseFact> facts,
            List<ScoreParseReviewCommand.Action> actions,
            String fallbackReason,
            String operatorName
    ) {
        java.util.Set<String> selectedFactIds = new java.util.LinkedHashSet<>();
        java.util.Map<String, BigDecimal> adjustedWeights = new java.util.LinkedHashMap<>();
        java.util.Map<String, ScoreParseReviewCommand.Action> edits = new java.util.LinkedHashMap<>();

        for (ScoreParseReviewCommand.Action a : actions) {
            if (a == null) continue;
            if (a.factId() != null && !a.factId().isBlank()) {
                selectedFactIds.add(a.factId().trim());
                edits.put(a.factId().trim(), a);
                if (a.adjustedWeight() != null) {
                    adjustedWeights.put(a.factId().trim(), a.adjustedWeight());
                }
            }
        }

        List<com.xiyu.bid.scoreparse.domain.ScoreReviewAdoptionPolicy.CandidateInput> inputs = facts.stream()
                .map(f -> toCandidateInput(f, edits.get(f.getFactId())))
                .toList();

        com.xiyu.bid.scoreparse.domain.ScoreReviewAdoptionPolicy.AdoptionResult result =
                adoptionPolicy.adopt(inputs, selectedFactIds, adjustedWeights);
        if (!result.success()) {
            throw new IllegalStateException(result.errorMessage());
        }
        BigDecimal declaredTotal = declaredTotal(task);
        if (declaredTotal == null) throw new IllegalStateException("招标文件存在多个冲突的声明总分，请人工确认分表后再采纳");
        if (result.totalWeight().compareTo(declaredTotal) != 0) {
            throw new IllegalStateException("修正后的叶子评分项合计必须为" + declaredTotal
                    + "分，当前为" + result.totalWeight() + "分");
        }

        if (itemPersistenceService != null) {
            itemPersistenceService.persistItems(projectId, task.getId(), result.candidates());
        }

        task.setDecisionStatus(com.xiyu.bid.scoreparse.domain.ScoreParseDecision.MANUAL_APPROVED.name());
        task.setOriginalTotalWeight(result.totalWeight());
        task.setFallbackReason(null);
        taskRepository.save(task);

        if (estimatedScoreService != null) {
            try {
                estimatedScoreService.estimateForProject(projectId);
            } catch (RuntimeException e) {
                log.warn("预估得分计算未完成（非致命，保留采纳结果）: projectId={}", projectId, e);
            }
        }

        reviewRepository.save(ScoreParseReview.builder()
                .projectId(projectId)
                .parseTaskId(task.getId())
                .factId("ALL_ADOPTED")
                .action("ADOPT_CANDIDATES")
                .originalWeight(null)
                .adjustedWeight(result.totalWeight())
                .reason(fallbackReason)
                .operatorName(normalizeOperatorName(operatorName))
                .build());
    }
    private com.xiyu.bid.scoreparse.domain.ScoreReviewAdoptionPolicy.CandidateInput toCandidateInput(
            ScoreParseFact fact, ScoreParseReviewCommand.Action edit) {
        return new com.xiyu.bid.scoreparse.domain.ScoreReviewAdoptionPolicy.CandidateInput(
                fact.getFactId(), prefer(edit == null ? null : edit.adjustedCode(), fact.getCode()),
                prefer(edit == null ? null : edit.adjustedDimension(), fact.getDimension()), fact.getWeight(),
                prefer(edit == null ? null : edit.adjustedSourceText(), fact.getSourceText()),
                prefer(edit == null ? null : edit.adjustedParentFactId(), fact.getParentFactId()),
                edit == null ? null : edit.adjustedScoreType());
    }
    private String prefer(String adjusted, String original) {
        return adjusted == null ? original : adjusted;
    }
    static BigDecimal declaredTotal(ScoreParseTask task) {
        if (task != null && task.getRunSummary() != null && !task.getRunSummary().isBlank()) {
            try {
                JsonNode splits = JSON.readTree(task.getRunSummary()).path("tableSplits");
                if (splits.isArray() && !splits.isEmpty()) {
                    if (splits.size() == 1) return numericTotal(splits.get(0));
                    BigDecimal total = BigDecimal.ZERO;
                    for (JsonNode split : splits) {
                        if ("NO_DECLARED_TOTAL".equals(split.path("tableId").asText())) {
                            return null;
                        }
                        JsonNode value = split.get("declaredTotal");
                        if (value == null || !value.isNumber()) {
                            return null;
                        }
                        total = total.add(value.decimalValue());
                    }
                    return total;
                }
            } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
                log.warn("解析任务声明总分失败，按100分闭合: taskId={}", task.getTaskId());
            }
        }
        return BigDecimal.valueOf(100);
    }
    private static BigDecimal numericTotal(JsonNode split) {
        JsonNode value = split.get("declaredTotal");
        return value != null && value.isNumber() ? value.decimalValue() : null;
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
                new com.xiyu.bid.scoreparse.domain.ScoreTypeClassificationPolicy().classify(fact.getSourceText()),
                fact.getValidationStatus(),
                evidences.stream().map(ScoreParseEvidence::getExplanation)
                        .filter(java.util.Objects::nonNull).filter(message -> !message.isBlank())
                        .distinct().collect(Collectors.joining("；")),
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
