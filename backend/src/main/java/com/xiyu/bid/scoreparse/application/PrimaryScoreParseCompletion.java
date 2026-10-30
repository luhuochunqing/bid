package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.RecallOutcome;
import com.xiyu.bid.scoreparse.domain.ScoreParsePipeline;
import com.xiyu.bid.scoreparse.domain.PrimaryScoreSnapshot;
import com.xiyu.bid.scoreparse.domain.WeightSumCheck;
import com.xiyu.bid.scoreparse.entity.ScoreParseTask;
import com.xiyu.bid.scoreparse.repository.ScoreParseTaskRepository;
import java.util.List;
import java.util.Map;

/** Persists a verified primary result through existing publication/versioning boundaries. */
final class PrimaryScoreParseCompletion {
    private PrimaryScoreParseCompletion() {}

    static void complete(ScoreParseTask task, ScoreParseEntryRouter.Route route, TenderTextSource source,
            VersionedScoreParseCompletion versioned, ScoreParseTaskRepository tasks,
            ScoreItemPersistenceService items, EstimatedScoreService estimates,
            ScoreParseTaskStateService state, ScoreParseProgressService progress) {
        var result = route.primary();
        String decision = "AUTO_APPROVED";
        task.setPipelineVersion(ScoreParsePipeline.DUAL_TRACK_PRIMARY);
        task.setDecisionStatus(decision);
        task.setOriginalTotalWeight(route.sourceTotal().value());
        task.setFallbackReason(null);
        task.setNormalizationWarning(null);
        task.setRunSummary(RunSummaryAssembler.assemble(new RecallOutcome(List.of(), 0), 0,
                new RunSummaryAssembler.MergeView(false, false, 0, 0, 0),
                new WeightSumCheck.Result(result.totalWeight(), false, false, Map.of(), Map.of()),
                result.candidates().size(), route.model(), ScoreParsePipeline.DUAL_TRACK_PRIMARY, decision,
                null, List.of(), List.of("PRIMARY_TOTAL_SOURCE:" + (route.sourceTotal().value() == null
                        ? "DEFAULT_100_CONTRACT" : route.sourceTotal().source()))).json());
        if (versioned != null) {
            versioned.primary(task, PrimaryScoreSnapshot.from(task.getTaskId(), source.fileName(), source.text(),
                    result.candidates(), route.sourceTotal(), route.model()));
            return;
        }
        tasks.save(task);
        items.persistItems(task.getProjectId(), task.getId(), result.candidates());
        estimates.estimateForProject(task.getProjectId());
        state.markCompleted(task.getTaskId());
        progress.clearProgress(task.getTaskId());
    }
}
