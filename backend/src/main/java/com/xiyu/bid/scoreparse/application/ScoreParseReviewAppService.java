package com.xiyu.bid.scoreparse.application;
import com.xiyu.bid.scoreparse.dto.ScoreParseReviewCommand;
import com.xiyu.bid.scoreparse.dto.ScoreParseReviewDTO;
import com.xiyu.bid.scoreparse.domain.ScoreReviewAdoptionPolicy;
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
/** AI 评分解析人工复核应用服务（spec 059 Phase 5 task3）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScoreParseReviewAppService {
    private static final String TASK_TYPE_PARSE = "PARSE";
    private static final List<String> ANY_STATUS = List.of("PENDING", "PROCESSING", "COMPLETED", "FAILED");
    private static final Set<String> ALLOWED_ACTIONS = Set.of(
            "RETAIN_ORIGINAL", "MANUAL_ADJUST", "EXCLUDE", "ADOPT_CANDIDATES");

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
        List<ScoreParseEvidence> allEvidence = evidenceRepository.findByParseTaskId(task.getId());
        Map<String, List<ScoreParseEvidence>> evidenceByFactId = allEvidence
                .stream()
                .collect(Collectors.groupingBy(ScoreParseEvidence::getFactId));
        List<ScoreParseReview> audits = reviewRepository.findByParseTaskId(task.getId());

        List<ScoreParseReviewDTO.Fact> reviewFacts = facts.stream()
                .sorted(Comparator.comparing(ScoreParseFact::getId))
                .map(fact -> ScoreParseReviewFactMapper.toFactDTO(fact, evidenceByFactId.getOrDefault(fact.getFactId(), List.of())))
                .toList();
        List<ScoreParseReviewDTO.Scheme> schemes = ScoreParseReviewFactMapper.schemes(reviewFacts);
        boolean selectionRequired = schemes.stream().map(ScoreParseReviewDTO.Scheme::schemeId)
                .filter(id -> id != null && !id.isBlank()).distinct().count() > 1;
        String selectedSchemeId = selectionRequired ? null : schemes.stream()
                .map(ScoreParseReviewDTO.Scheme::schemeId).filter(id -> id != null && !id.isBlank())
                .findFirst().orElse(null);
        BigDecimal declaredTotal = schemes.size() == 1 ? schemes.get(0).declaredTotal()
                : schemes.isEmpty() ? declaredTotal(task) : null;

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
                declaredTotal,
                ScoreParseReviewTotalPolicy.adoptionStatus(task, schemes),
                reviewFacts,
                audits.stream()
                        .sorted(Comparator.comparing(ScoreParseReview::getCreatedAt,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                        .map(this::toAuditDTO)
                        .toList(),
                selectionRequired, selectedSchemeId, schemes);
    }
    @Transactional
    public ScoreParseReviewDTO submitReview(Long projectId, ScoreParseReviewCommand command, String operatorName) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        List<ScoreParseReviewCommand.Action> actions = command == null ? List.of() : command.actions();
        if (actions == null || actions.isEmpty()) {
            throw new IllegalArgumentException("请至少提交一条复核动作");
        }
        String fallbackReason = normalizeReason(command.reason(), "人工复核");
        boolean isAdopt = actions.stream().anyMatch(a -> a != null && "ADOPT_CANDIDATES".equals(normalizeAction(a.action())));
        if (isAdopt) taskRepository.lockProjectForTaskChange(projectId);
        ScoreParseTask task = isAdopt
                ? taskRepository.findLatestParseTaskForUpdate(projectId)
                    .orElseThrow(() -> new IllegalArgumentException("项目没有可采纳的评分标准解析任务"))
                : latestParseTask(projectId);
        List<ScoreParseFact> facts = factRepository.findByParseTaskId(task.getId());
        Map<String, ScoreParseFact> factsById = facts.stream()
                .collect(Collectors.toMap(ScoreParseFact::getFactId, Function.identity(), (a, b) -> a));
        if (isAdopt) {
            if (!"COMPLETED".equals(task.getStatus())) {
                throw new IllegalArgumentException("当前评分标准解析失败或尚未完成，请重新解析后再采纳");
            }
            if (command.parseTaskId() == null || !command.parseTaskId().equals(task.getId())) {
                throw new IllegalArgumentException("评分标准解析任务已变化，请刷新页面核对最新候选项");
            }
            if ("MANUAL_APPROVED".equals(task.getDecisionStatus())) {
                throw new IllegalArgumentException("当前评分标准已采纳，请刷新页面查看正式评分项");
            }
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
        List<ScoreParseEvidence> qualificationEvidence = evidenceRepository.findByParseTaskId(task.getId());
        Map<String, ScoreParseReviewFactMapper.Qualification> qualifications = qualificationEvidence.stream()
                .filter(evidence -> "SCHEME_QUALIFICATION".equals(evidence.getKind()))
                .collect(Collectors.toMap(ScoreParseEvidence::getFactId, ScoreParseReviewFactMapper::qualification,
                        (first, ignored) -> first));
        qualificationEvidence.stream().filter(evidence -> "ITEM_SCHEME_SCOPE_MISMATCH".equals(evidence.getKind()))
                .forEach(evidence -> qualifications.putIfAbsent(evidence.getFactId(), ScoreParseReviewFactMapper.qualification(evidence)));
        List<ScoreParseFact> eligibleFacts = facts.stream().filter(fact -> {
            ScoreParseReviewFactMapper.Qualification qualification = qualifications.get(fact.getFactId());
            return qualification != null && qualification.candidateEligible();
        }).toList();
        Set<String> eligibleIds = eligibleFacts.stream().map(ScoreParseFact::getFactId)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        if (!eligibleIds.containsAll(selectedFactIds)) {
            throw new IllegalArgumentException("方案外或来源未验证的评分事实不能采纳");
        }
        if (selectedFactIds.isEmpty()) {
            if (eligibleFacts.isEmpty()) {
                throw new IllegalArgumentException("没有来源已验证且属于评分方案的候选项");
            }
            Set<String> schemeIds = eligibleFacts.stream().map(fact -> qualifications.get(fact.getFactId()).schemeId())
                    .filter(id -> id != null && !id.isBlank()).collect(Collectors.toSet());
            if (schemeIds.size() != 1) {
                throw new IllegalArgumentException("存在多个评分方案，请先选择一个方案再采纳");
            }
            selectedFactIds.addAll(eligibleIds);
        }
        Set<String> selectedSchemeIds = selectedFactIds.stream().map(qualifications::get)
                .filter(java.util.Objects::nonNull).map(ScoreParseReviewFactMapper.Qualification::schemeId)
                .filter(id -> id != null && !id.isBlank()).collect(Collectors.toSet());
        if (selectedSchemeIds.size() != 1) {
            throw new IllegalArgumentException("一次采纳只能包含同一评分方案的事实");
        }
        String adoptedSchemeId = selectedSchemeIds.iterator().next();
        List<com.xiyu.bid.scoreparse.domain.ScoreReviewAdoptionPolicy.CandidateInput> inputs = eligibleFacts.stream()
                .filter(fact -> adoptedSchemeId.equals(qualifications.get(fact.getFactId()).schemeId()))
                .map(f -> toCandidateInput(f, edits.get(f.getFactId())))
                .toList();

        com.xiyu.bid.scoreparse.domain.ScoreReviewAdoptionPolicy.AdoptionResult result =
                adoptionPolicy.adopt(inputs, selectedFactIds, adjustedWeights);
        if (!result.success()) {
            throw new IllegalArgumentException(result.errorMessage());
        }
        ScoreParseReviewFactMapper.Qualification selectedScheme = qualifications.values().stream()
                .filter(qualification -> adoptedSchemeId.equals(qualification.schemeId())).findFirst().orElse(null);
        BigDecimal declaredTotal = selectedScheme == null ? null : selectedScheme.declaredTotal();
        String totalEvidenceStatus = selectedScheme == null ? "UNKNOWN" : selectedScheme.totalEvidenceStatus();
        if (declaredTotal == null && "UNKNOWN".equals(totalEvidenceStatus)
                && selectedScheme != null && selectedScheme.tableIds().size() == 1
                && ScoreParseReviewTotalPolicy.isSingleUnknownTable(task)) declaredTotal = result.totalWeight();
        if ("CONFLICT".equals(totalEvidenceStatus)) {
            throw new IllegalArgumentException("所选评分方案的原文总分存在冲突，请先核验冲突证据");
        }
        if (declaredTotal == null) {
            throw new IllegalArgumentException("所选评分方案没有可验证的原文总分，不能自动按表格分值合并");
        }
        if (!ScoreReviewAdoptionPolicy.isWithinDeclaredTotal(result.totalWeight(), declaredTotal)) {
            throw new IllegalArgumentException("候选评分项合计不能超过原文声明总分" + declaredTotal
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
            // The caller owns the project lock until its actual commit, including outer transactions.
            ScoreAfterCommit.dispatch(() -> estimatedScoreService.estimateForProject(projectId));
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
                edit == null ? null : edit.adjustedScoreType(), fact.getChildAggregation());
    }
    private String prefer(String adjusted, String original) {
        return adjusted == null ? original : adjusted;
    }
    static BigDecimal declaredTotal(ScoreParseTask task) {
        return ScoreParseReviewFactMapper.declaredTotal(task);
    }

    private ScoreParseTask latestParseTask(Long projectId) {
        return taskRepository.findByProjectIdAndTaskTypeAndStatusIn(projectId, TASK_TYPE_PARSE, ANY_STATUS)
                .stream()
                .max(Comparator.comparing(ScoreParseTask::getId))
                .orElseThrow(() -> new IllegalStateException("项目无解析任务: " + projectId));
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
