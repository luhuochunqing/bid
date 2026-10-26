package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.biddraftagent.entity.BidTenderDocumentSnapshot;
import com.xiyu.bid.biddraftagent.repository.BidTenderDocumentSnapshotRepository;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.scoreparse.dto.ScoreParseItemsDTO;
import com.xiyu.bid.scoreparse.entity.ScoreItem;
import com.xiyu.bid.scoreparse.entity.ScoreParseTask;
import com.xiyu.bid.scoreparse.entity.ScoreResult;
import com.xiyu.bid.scoreparse.repository.ScoreItemRepository;
import com.xiyu.bid.scoreparse.repository.ScoreParseTaskRepository;
import com.xiyu.bid.scoreparse.repository.ScoreResultRepository;

import java.util.List;

/** 抽屉 meta：解析/打分结果形态、熔断、沿用项数。 */
final class ScoreParseItemsMetaBuilder {

    private final InitiationTenderTextResolver tenderResolver;
    private final BidTenderDocumentSnapshotRepository snapshotRepository;
    private final ScoreParseTaskRepository taskRepository;
    private final ScoreItemRepository itemRepository;
    private final ScoreResultRepository resultRepository;
    private final ScoreBidDocumentLookup bidDocs;

    ScoreParseItemsMetaBuilder(InitiationTenderTextResolver tenderResolver,
                               BidTenderDocumentSnapshotRepository snapshotRepository,
                               ScoreParseTaskRepository taskRepository,
                               ScoreItemRepository itemRepository,
                               ScoreResultRepository resultRepository,
                               ScoreBidDocumentLookup bidDocs) {
        this.tenderResolver = tenderResolver;
        this.snapshotRepository = snapshotRepository;
        this.taskRepository = taskRepository;
        this.itemRepository = itemRepository;
        this.resultRepository = resultRepository;
        this.bidDocs = bidDocs;
    }

    ScoreParseItemsDTO.Meta build(Long projectId, ScoreParseTask parseTask, ScoreParseTask scoringTask) {
        ScoreParseTask activeSource = itemRepository.findByProjectIdOrderByItemIndexAsc(projectId).stream()
                .filter(i -> i.getParseTaskId() != null).findFirst().flatMap(i -> taskRepository.findById(i.getParseTaskId())).orElse(null);
        String source = activeSource != null ? activeSource.getFileName() : tenderResolver.findLatestTenderDocument(projectId)
                .map(ProjectDocument::getName)
                .or(() -> snapshotRepository.findTopByProjectIdOrderByCreatedAtDescIdDesc(projectId)
                        .map(BidTenderDocumentSnapshot::getFileName))
                .orElse(null);
        String stage = scoringTask == null ? null : scoringTask.getStage();
        String outcome = ScoreScoringHint.outcomeOf(stage);
        Integer reused = countReused(projectId);
        boolean failed = parseTask != null && "FAILED".equals(parseTask.getStatus());
        String bidName = scoringTask != null && scoringTask.getFileName() != null
                ? scoringTask.getFileName() : bidDocs.latestName(projectId);
        int total = itemRepository.findByProjectIdOrderByItemIndexAsc(projectId).size();
        return new ScoreParseItemsDTO.Meta(
                source,
                activeSource != null ? activeSource.getCompletedAt() : parseTask == null ? null : parseTask.getCompletedAt(),
                bidName,
                scoringTask == null ? null : scoringTask.getCompletedAt(),
                parseTask == null ? null : parseTask.getStatus(),
                failed ? parseTask.getErrorMessage() : null,
                outcome, ScoreScoringHint.text(outcome, reused, total, stage),
                new ScoreParseAutoPolicy(taskRepository, itemRepository).circuitOpen(projectId),
                reused,
                parseTask == null ? null : parseTask.getSkippedChunks(),
                parseTask == null ? null : parseTask.getRunSummary(),
                parseTask == null ? null : parseTask.getPipelineVersion(),
                parseTask == null ? null : parseTask.getDecisionStatus(),
                parseTask == null ? null : parseTask.getFallbackReason(),
                parseTask == null ? null : parseTask.getArtifactSha256(),
                parseTask == null ? null : parseTask.getReviewReason());
    }

    private Integer countReused(Long projectId) {
        List<ScoreItem> items = itemRepository.findByProjectIdOrderByItemIndexAsc(projectId);
        if (items.isEmpty()) {
            return null;
        }
        int reused = 0;
        int marked = 0;
        for (ScoreResult result : resultRepository.findByScoreItemIdIn(
                items.stream().map(ScoreItem::getId).toList())) {
            if (result.getReuseKind() != null) {
                marked++;
                if ("REUSED".equals(result.getReuseKind())) {
                    reused++;
                }
            }
        }
        return marked == 0 ? null : reused;
    }
}
