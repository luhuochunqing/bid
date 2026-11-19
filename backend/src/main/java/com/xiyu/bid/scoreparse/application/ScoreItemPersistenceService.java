// Input: projectId/parseTaskId/合并去重后的候选列表
// Output: score_item 覆盖落库（含 FR-021 / PRD §3.7 指纹判定覆盖）
// Pos: scoreparse/application — 评分项持久化（spec 041 US1/US5）
// 维护声明: 维护者按项目SOP；从 ScoreParseAppService 拆出（300 行预算 + 单一职责）
package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.DeterministicScoreFactExtractor;
import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import com.xiyu.bid.scoreparse.domain.ScoreCategoryClassifier;
import com.xiyu.bid.scoreparse.domain.ScoreItemOrderPolicy;
import com.xiyu.bid.scoreparse.domain.ScoreItemTitleExtractor;
import com.xiyu.bid.scoreparse.domain.ScoreTypeClassificationPolicy;
import com.xiyu.bid.scoreparse.entity.ScoreItem;
import com.xiyu.bid.scoreparse.entity.ScoreResult;
import com.xiyu.bid.scoreparse.repository.ScoreItemRepository;
import com.xiyu.bid.scoreparse.repository.ScoreResultRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 评分项持久化服务（spec 041 / PRD §3.7）。
 *
 * <p>职责单一：候选 → {@link ScoreItem} 落库。
 * <p>PRD §3.7：重新解析默认不影响已有打分；仅编号/数量/权重变化时才清空打分结果。
 * <p>R025：评分类别以确定性规则优先（描述性/量化信号覆盖模型猜测，模型值仅兜底无信号文本）。
 */
@Service
@Slf4j
public class ScoreItemPersistenceService {

    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private com.xiyu.bid.scoreparse.repository.ScoreActiveVersionRepository activeVersionRepository;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private com.xiyu.bid.scoreparse.repository.ScoreParseTaskRepository taskRepository;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private com.xiyu.bid.scoreparse.repository.ScoreExtractionRecordRepository extractionSnapshotRepository;
    private final ScoreItemRepository itemRepository;
    private final ScoreResultRepository resultRepository;
    private final com.xiyu.bid.scoreparse.repository.ScoreParseFactRepository factRepository;
    private final com.xiyu.bid.scoreparse.repository.ScoreParseEvidenceRepository evidenceRepository;
    private final ScoreParseFactEvidenceWriter factEvidenceWriter;
    private final ScoreTypeClassificationPolicy classificationPolicy = new ScoreTypeClassificationPolicy();
    private final ScoreItemOrderPolicy orderPolicy = new ScoreItemOrderPolicy();

    @org.springframework.beans.factory.annotation.Autowired
    public ScoreItemPersistenceService(
            ScoreItemRepository itemRepository,
            ScoreResultRepository resultRepository,
            com.xiyu.bid.scoreparse.repository.ScoreParseFactRepository factRepository,
            com.xiyu.bid.scoreparse.repository.ScoreParseEvidenceRepository evidenceRepository,
            ObjectMapper objectMapper) {
        this.itemRepository = itemRepository;
        this.resultRepository = resultRepository;
        this.factRepository = factRepository;
        this.evidenceRepository = evidenceRepository;
        this.factEvidenceWriter = new ScoreParseFactEvidenceWriter(factRepository, evidenceRepository,
                objectMapper != null ? objectMapper : new ObjectMapper());
    }

    public ScoreItemPersistenceService(
            ScoreItemRepository itemRepository,
            ScoreResultRepository resultRepository,
            com.xiyu.bid.scoreparse.repository.ScoreParseFactRepository factRepository,
            com.xiyu.bid.scoreparse.repository.ScoreParseEvidenceRepository evidenceRepository) {
        this(itemRepository, resultRepository, factRepository, evidenceRepository, null);
    }

    public ScoreItemPersistenceService(ScoreItemRepository itemRepository, ScoreResultRepository resultRepository) {
        this(itemRepository, resultRepository, null, null, null);
    }

    /** 候选覆盖落库：比对指纹，结构变化时清理旧打分，未变化时保留旧打分并重挂 */
    @Transactional
    public void persistItems(Long projectId, Long parseTaskId, List<ScoreCandidate> candidates) {
        if (taskRepository != null) taskRepository.lockProjectForTaskChange(projectId);
        if (activeVersionRepository != null && activeVersionRepository.currentIdForUpdate(projectId).isPresent())
            throw new ScoreExtractionException(409, "VERSIONED_PROJECT_REQUIRES_ACTIVATION");
        if (extractionSnapshotRepository != null && parseTaskId != null && extractionSnapshotRepository.currentIdForUpdate(parseTaskId).isPresent())
            throw new ScoreExtractionException(409, "VERSIONED_TASK_REQUIRES_ACTIVATION");
        List<ScoreCandidate> ordered = orderPolicy.sort(candidates);
        List<String> dimensions = ordered.stream().map(ScoreItemPersistenceService::resolveDim).toList();
        if (dimensions.stream().anyMatch(ScoreCategoryClassifier::isInvalidOrPlaceholderName)) {
            throw new ScoreExtractionException(400, "INVALID_SCORE_ITEM_NAME");
        }
        List<ScoreItem> oldItems = itemRepository.findByProjectIdOrderByItemIndexAsc(projectId);
        boolean fingerprintUnchanged = isFingerprintUnchanged(oldItems, ordered);

        Map<String, ScoreResult> oldResultByCode = Map.of();
        if (fingerprintUnchanged && !oldItems.isEmpty()) {
            List<Long> oldItemIds = oldItems.stream().map(ScoreItem::getId).toList();
            List<ScoreResult> oldResults = resultRepository.findByScoreItemIdIn(oldItemIds);
            Map<Long, String> codeByItemId = oldItems.stream()
                    .collect(Collectors.toMap(ScoreItem::getId, ScoreItem::getCode, (a, b) -> a));
            oldResultByCode = oldResults.stream()
                    .filter(r -> codeByItemId.containsKey(r.getScoreItemId()))
                    .collect(Collectors.toMap(r -> codeByItemId.get(r.getScoreItemId()), r -> r, (a, b) -> a));
        }

        invalidatePreviousResults(projectId, !fingerprintUnchanged);

        List<ScoreItem> items = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            ScoreCandidate candidate = ordered.get(i);
            String scoreType = com.xiyu.bid.scoreparse.domain.GroupedCriteriaScoreTable.METHOD.equals(candidate.location())
                    ? candidate.scoreTypeGuess() : classificationPolicy.classify(candidate.detail(), candidate.scoreTypeGuess());
            String code = resolveCode(candidate.code());
            String detail = candidate.detail() == null || candidate.detail().isBlank()
                    ? "—" : candidate.detail();
            String location = resolveLocation(candidate.location());
            items.add(ScoreItem.builder()
                    .projectId(projectId)
                    .parseTaskId(parseTaskId)
                    .itemIndex(i + 1)
                    .code(code)
                    .dim(dimensions.get(i))
                    .detail(detail)
                    .weight(candidate.weight() == null ? BigDecimal.ZERO : candidate.weight())
                    .scoreType(scoreType)
                    .statusStage1("PENDING")
                    .contextNote(candidate.contextNote())
                    .sourceText(candidate.sourceText())
                    .location(location)
                    .extractionMethod("EVIDENCE_PIPELINE".equals(candidate.semanticPattern()) ? candidate.location() : "LEGACY_FALLBACK")
                    .confidence(BigDecimal.ONE)
                    .validationStatus("AUTO_APPROVED")
                    .build());
        }
        List<ScoreItem> savedItems = itemRepository.saveAll(items);

        if (fingerprintUnchanged && !oldResultByCode.isEmpty()) {
            List<ScoreResult> preservedResults = new ArrayList<>();
            for (ScoreItem saved : savedItems) {
                ScoreResult oldRes = oldResultByCode.get(saved.getCode());
                if (oldRes != null) {
                    preservedResults.add(ScoreResult.builder()
                            .scoreItemId(saved.getId())
                            .scoringTaskId(oldRes.getScoringTaskId())
                            .actualScore(oldRes.getActualScore())
                            .statusStage2(oldRes.getStatusStage2())
                            .evidence(oldRes.getEvidence())
                            .quote(oldRes.getQuote())
                            .missedReason(oldRes.getMissedReason())
                            .suggestion(oldRes.getSuggestion())
                            .matchRatio(oldRes.getMatchRatio())
                            .build());
                }
            }
            if (!preservedResults.isEmpty()) {
                resultRepository.saveAll(preservedResults);
                log.info("重新解析指纹未变，已保留 {} 条历史打分结果: projectId={}",
                        preservedResults.size(), projectId);
            }
        }
    }

    /** 双写持久化不可变事实与单元格证据（spec 059 Phase 5） */
    @Transactional
    public void persistFactsAndEvidence(Long projectId, Long parseTaskId, List<com.xiyu.bid.scoreparse.domain.ScoreFact> facts) {
        factEvidenceWriter.persist(projectId, parseTaskId, facts, List.of());
    }

    /** Persist source qualification beside each fact so review and adoption share the same eligibility decision. */
    @Transactional
    public void persistFactsAndEvidence(Long projectId, Long parseTaskId,
                                        List<com.xiyu.bid.scoreparse.domain.ScoreFact> facts,
                                        List<com.xiyu.bid.scoreparse.domain.OriginalScoreEvidencePolicy.Qualification> qualifications) {
        factEvidenceWriter.persist(projectId, parseTaskId, facts, qualifications);
    }

    /**
     * REVIEW_REQUIRED 候选快照：不覆盖正式 score_item，只写入 score_parse_fact 供复核 UI 展示最新候选。
     */
    @Transactional
    public void persistReviewCandidateFacts(Long projectId, Long parseTaskId, List<ScoreCandidate> candidates) {
        List<com.xiyu.bid.scoreparse.domain.ScoreFact> facts =
                ScoreReviewCandidateFactMapper.toFacts(parseTaskId, candidates);
        if (facts.isEmpty()) {
            return;
        }
        persistFactsAndEvidence(projectId, parseTaskId, facts);
        log.info("已保存 REVIEW_REQUIRED 候选事实快照: projectId={}, parseTaskId={}, facts={}",
                projectId, parseTaskId, facts.size());
    }

    private boolean isFingerprintUnchanged(List<ScoreItem> oldItems, List<ScoreCandidate> candidates) {
        if (oldItems == null || candidates == null || oldItems.size() != candidates.size()) {
            return false;
        }
        for (int i = 0; i < oldItems.size(); i++) {
            ScoreItem old = oldItems.get(i);
            ScoreCandidate candidate = candidates.get(i);
            if (!Objects.equals(old.getCode(), resolveCode(candidate.code()))
                    || !Objects.equals(old.getWeight(), candidate.weight())
                    || !Objects.equals(old.getDetail(), candidate.detail())) {
                return false;
            }
        }
        return true;
    }

    private void invalidatePreviousResults(Long projectId, boolean clearResults) {
        List<ScoreItem> oldItems = itemRepository.findByProjectIdOrderByItemIndexAsc(projectId);
        if (oldItems.isEmpty()) return;
        if (clearResults) {
            resultRepository.deleteByScoreItemIdIn(oldItems.stream().map(ScoreItem::getId).toList());
            log.info("评分结构变动，清理旧打分数据: projectId={}, oldItems={}", projectId, oldItems.size());
        }
        itemRepository.deleteByProjectId(projectId);
    }

    private String resolveCode(String code) {
        return com.xiyu.bid.scoreparse.domain.ScoreCodeSanitizer.clean(code);
    }

    public static boolean hasInvalidItemName(List<ScoreCandidate> candidates) {
        return candidates != null && candidates.stream()
                .map(ScoreItemPersistenceService::resolveDim)
                .anyMatch(ScoreCategoryClassifier::isInvalidOrPlaceholderName);
    }

    static String resolveDim(ScoreCandidate candidate) {
        boolean preferCandidateName = "MANUAL_REVIEW_ADOPTED".equals(candidate.semanticPattern())
                || ("EVIDENCE_PIPELINE".equals(candidate.semanticPattern())
                && DeterministicScoreFactExtractor.EVALUATION_CRITERIA_METHOD.equals(candidate.location()));
        return ScoreItemTitleExtractor.resolveCandidateDimension(candidate, preferCandidateName);
    }

    private String resolveLocation(String location) {
        if (location == null || location.isBlank()) return null;
        String trimmed = location.trim();
        return trimmed.length() > 200 ? trimmed.substring(0, 200) : trimmed;
    }
}
