// Input: projectId/parseTaskId/合并去重后的候选列表
// Output: score_item 覆盖落库（含 FR-021 / PRD §3.7 指纹判定覆盖）
// Pos: scoreparse/application — 评分项持久化（spec 041 US1/US5）
// 维护声明: 维护者按项目SOP；从 ScoreParseAppService 拆出（300 行预算 + 单一职责）
package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import com.xiyu.bid.scoreparse.domain.ScoreTypeClassificationPolicy;
import com.xiyu.bid.scoreparse.entity.ScoreItem;
import com.xiyu.bid.scoreparse.entity.ScoreResult;
import com.xiyu.bid.scoreparse.repository.ScoreItemRepository;
import com.xiyu.bid.scoreparse.repository.ScoreResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
 * <p>R025：评分类别以 LLM scoreTypeGuess 优先，报价类强制主观。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScoreItemPersistenceService {

    private final ScoreItemRepository itemRepository;
    private final ScoreResultRepository resultRepository;
    private final ScoreTypeClassificationPolicy classificationPolicy = new ScoreTypeClassificationPolicy();

    /** 候选覆盖落库：比对指纹，结构变化时清理旧打分，未变化时保留旧打分并重挂 */
    public void persistItems(Long projectId, Long parseTaskId, List<ScoreCandidate> candidates) {
        List<ScoreItem> oldItems = itemRepository.findByProjectIdOrderByItemIndexAsc(projectId);
        boolean fingerprintUnchanged = isFingerprintUnchanged(oldItems, candidates);

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
        for (int i = 0; i < candidates.size(); i++) {
            ScoreCandidate candidate = candidates.get(i);
            String scoreType = classificationPolicy.classify(candidate.detail(), candidate.scoreTypeGuess());
            items.add(ScoreItem.builder()
                    .projectId(projectId)
                    .parseTaskId(parseTaskId)
                    .itemIndex(i + 1)
                    .code(candidate.code() == null || candidate.code().isBlank()
                            ? String.valueOf(i + 1) : candidate.code())
                    .dim(candidate.dim())
                    .detail(candidate.detail())
                    .weight(candidate.weight())
                    .scoreType(scoreType)
                    .statusStage1("PENDING")
                    .contextNote(candidate.contextNote())
                    .sourceText(candidate.sourceText())
                    .location(candidate.location())
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

    private boolean isFingerprintUnchanged(List<ScoreItem> oldItems, List<ScoreCandidate> candidates) {
        if (oldItems == null || candidates == null || oldItems.size() != candidates.size()) {
            return false;
        }
        for (int i = 0; i < oldItems.size(); i++) {
            ScoreItem old = oldItems.get(i);
            ScoreCandidate candidate = candidates.get(i);
            String code = candidate.code() == null || candidate.code().isBlank()
                    ? String.valueOf(i + 1) : candidate.code();
            if (!Objects.equals(old.getCode(), code)
                    || !Objects.equals(old.getWeight(), candidate.weight())
                    || !Objects.equals(old.getDetail(), candidate.detail())) {
                return false;
            }
        }
        return true;
    }

    private void invalidatePreviousResults(Long projectId, boolean clearResults) {
        List<ScoreItem> oldItems = itemRepository.findByProjectIdOrderByItemIndexAsc(projectId);
        if (oldItems.isEmpty()) {
            return;
        }
        if (clearResults) {
            resultRepository.deleteByScoreItemIdIn(
                    oldItems.stream().map(ScoreItem::getId).toList());
            log.info("评分结构变动，清理旧打分数据: projectId={}, oldItems={}", projectId, oldItems.size());
        }
        itemRepository.deleteByProjectId(projectId);
    }
}
