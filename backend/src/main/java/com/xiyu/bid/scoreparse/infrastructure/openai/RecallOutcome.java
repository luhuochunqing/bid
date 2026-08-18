// Input: 召回四（LLM 全文语义）产出的候选列表 + 两连败跳过的 chunk 数
// Output: recallCandidates 的返回载体（spec 046 US3 可见性）
// Pos: scoreparse/infrastructure/openai — 召回结果值对象
package com.xiyu.bid.scoreparse.infrastructure.openai;

import com.xiyu.bid.scoreparse.domain.ScoreCandidate;

import java.util.List;

/**
 * 四路召回结果（spec 046 FR-005）：候选池 + LLM 提取跳过的 chunk 数。
 *
 * @param candidates    合并前候选池（召回一/二/三/四 concat）
 * @param skippedChunks 重试一次后仍失败的 chunk 数（0 表示无跳过）
 */
public record RecallOutcome(List<ScoreCandidate> candidates, int skippedChunks) {
}
