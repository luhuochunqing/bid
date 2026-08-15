// Input: projectId/parseTaskId/合并去重后的候选列表
// Output: score_item 覆盖落库（含 FR-021 旧数据失效清理）
// Pos: scoreparse/application — 评分项持久化（spec 041 US1/US5）
// 维护声明: 维护者按项目SOP；从 ScoreParseAppService 拆出（300 行预算 + 单一职责）
package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import com.xiyu.bid.scoreparse.domain.ScoreTypeClassificationPolicy;
import com.xiyu.bid.scoreparse.entity.ScoreItem;
import com.xiyu.bid.scoreparse.repository.ScoreItemRepository;
import com.xiyu.bid.scoreparse.repository.ScoreResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 评分项持久化服务（spec 041）。
 *
 * <p>职责单一：候选 → {@link ScoreItem} 落库，含 FR-021 重新解析覆盖语义。
 * <p>FR-003：客观/主观判定唯一真相源是 {@link ScoreTypeClassificationPolicy}
 * （LLM 的 scoreTypeGuess 仅参考，不落库）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScoreItemPersistenceService {

    private final ScoreItemRepository itemRepository;
    private final ScoreResultRepository resultRepository;
    private final ScoreTypeClassificationPolicy classificationPolicy = new ScoreTypeClassificationPolicy();

    /** 候选覆盖落库：先失效旧数据（FR-021）再批量写入 */
    public void persistItems(Long projectId, Long parseTaskId, List<ScoreCandidate> candidates) {
        invalidatePreviousResults(projectId);
        List<ScoreItem> items = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            ScoreCandidate candidate = candidates.get(i);
            String scoreType = classificationPolicy.classify(candidate.detail());
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
        itemRepository.saveAll(items);
    }

    /**
     * FR-021 重新解析覆盖语义：旧打分结果随旧评分项失效清理。
     * <p>score_result.score_item_id 无 FK 级联，须按旧 item ID 显式删除；
     * 首次解析（无旧数据）零删除。
     */
    private void invalidatePreviousResults(Long projectId) {
        List<ScoreItem> oldItems = itemRepository.findByProjectIdOrderByItemIndexAsc(projectId);
        if (oldItems.isEmpty()) {
            return;
        }
        resultRepository.deleteByScoreItemIdIn(
                oldItems.stream().map(ScoreItem::getId).toList());
        itemRepository.deleteByProjectId(projectId);
        log.info("重新解析清理旧数据: projectId={}, oldItems={}", projectId, oldItems.size());
    }
}
