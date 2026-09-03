// Input: LlmFactOp 列表（LLM 调用结果反序列化产物）
// Output: 不可变 LlmDecision 值对象
// Pos: scoreparse/domain/llm — 纯核心，sealed LlmFactOp 序列（spec 059 Phase 3）
package com.xiyu.bid.scoreparse.domain.llm;

import java.util.List;

/**
 * LLM 对 ScoreFact 池做出的决策（spec 059 Phase 3）。
 * <p>由 {@link LlmDecisionValidator} 校验每个 op 引用的 {@code factId} 都
 * 已存在于上游的事实池中。任何不在池内的引用都构成 violation，调用方按
 * fail-open / reject 策略自行处理。
 *
 * @param ops LLM 给出的有序操作列表；不可为空但可为空列表（空决策合法）
 */
public record LlmDecision(List<LlmFactOp> ops) {
    public LlmDecision {
        ops = ops == null ? List.of() : List.copyOf(ops);
    }
}
