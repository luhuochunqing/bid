// Input: 结构化表格/证据管线提取结果（候选、完整性、决策）
// Output: 不可变值对象 + 三态工厂（ok / structureIncomplete / evidencePipeline）
// Pos: scoreparse/application — 由 StructuredTableExtractionHandler.Result 提升为顶层（spec 065 行预算拆分，行为零变化）
package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import com.xiyu.bid.scoreparse.domain.OriginalScoreEvidencePolicy;
import com.xiyu.bid.scoreparse.domain.ScoreCompletenessPolicy;
import com.xiyu.bid.scoreparse.domain.ScoreFact;
import com.xiyu.bid.scoreparse.domain.TableCompleteness;

import java.util.List;

record StructuredTableResult(List<ScoreCandidate> candidates,
                             boolean structureIncomplete,
                             String failureReason,
                             boolean evidencePipeline,
                             List<ScoreFact> facts,
                             ScoreCompletenessPolicy.ScoreCompletenessResult completeness,
                             List<TableCompleteness> tableCompletenesses,
                             List<OriginalScoreEvidencePolicy.PublicationProof> originalProofs) {

    boolean originalVerified() {
        return originalProofs != null && !originalProofs.isEmpty();
    }

    static StructuredTableResult ok(List<ScoreCandidate> candidates) {
        return new StructuredTableResult(candidates, false, null, false, List.of(), null, List.of(), List.of());
    }

    static StructuredTableResult structureIncomplete(String failureReason) {
        return new StructuredTableResult(List.of(), true, failureReason, false, List.of(), null, List.of(), List.of());
    }

    static StructuredTableResult evidencePipeline(List<ScoreFact> facts,
                                                  List<ScoreCandidate> candidates,
                                                  ScoreCompletenessPolicy.ScoreCompletenessResult completeness,
                                                  List<TableCompleteness> tableCompletenesses) {
        return evidencePipeline(facts, candidates, completeness, tableCompletenesses, List.of());
    }

    static StructuredTableResult evidencePipeline(List<ScoreFact> facts,
                                                  List<ScoreCandidate> candidates,
                                                  ScoreCompletenessPolicy.ScoreCompletenessResult completeness,
                                                  List<TableCompleteness> tableCompletenesses,
                                                  List<OriginalScoreEvidencePolicy.PublicationProof> originalProofs) {
        return new StructuredTableResult(candidates, false, null, true,
                facts == null ? List.of() : List.copyOf(facts), completeness,
                tableCompletenesses == null ? List.of() : List.copyOf(tableCompletenesses),
                originalProofs == null ? List.of() : List.copyOf(originalProofs));
    }
}
