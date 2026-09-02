// Input: deterministic ScoreFact pool + LLM-emitted decision referencing factIds
// Output: validated LlmDecision that only references existing factIds
// Pos: scoreparse/domain/llm — 纯核心，约束 LLM 不得发明新 fact（spec 059 Phase 3）
package com.xiyu.bid.scoreparse.domain.llm;

import java.util.List;

/**
 * LLM 允许的 fact 操作类型（spec 059 Phase 3）。
 * <p>Phase 3 的核心约束：LLM 不得从无到有创造新 fact；它只能对已有的
 * {@code factId} 进行选择、合并近重复、标记复核、附加备注。
 * <p>本 sealed interface 在编译期枚举允许的操作；任何"造新 fact / 改 evidence /
 * 改 weight / 改 factId"的企图都被类型系统拒之门外。
 *
 * <h3>不变量的处理</h3>
 * <p>FP-Java 原则：domain 层不抛业务异常。{@link LlmDecisionValidator} 对引用不存在
 * {@code factId} 的 op 收集到 {@code ValidationResult.violations}，调用方按
 * fail-open / reject 策略自行处理。
 */
public sealed interface LlmFactOp
        permits LlmFactOp.SelectFact, LlmFactOp.MergeDuplicates,
        LlmFactOp.MarkReviewRequired, LlmFactOp.AddObservation {

    /** 选取某个 fact 作为权威版本（confidence 由 LLM 自报）。 */
    record SelectFact(String factId, double confidence) implements LlmFactOp {
        public SelectFact {
            factId = factId == null ? "" : factId;
        }
    }

    /** 将多个近重复 fact 折叠到 {@code keepFactId}；{@code removeFactIds} 列表将被下游标记为弃用。 */
    record MergeDuplicates(String keepFactId, List<String> removeFactIds) implements LlmFactOp {
        public MergeDuplicates {
            keepFactId = keepFactId == null ? "" : keepFactId;
            removeFactIds = removeFactIds == null ? List.of() : List.copyOf(removeFactIds);
        }
    }

    /** 标记某个 fact 需要人工复核；reason 是人类可读的简短说明。 */
    record MarkReviewRequired(String factId, String reason) implements LlmFactOp {
        public MarkReviewRequired {
            factId = factId == null ? "" : factId;
            reason = reason == null ? "" : reason;
        }
    }

    /** 给某个 fact 附加备注；note 仅作为元数据，不参与证据链路。 */
    record AddObservation(String factId, String note) implements LlmFactOp {
        public AddObservation {
            factId = factId == null ? "" : factId;
            note = note == null ? "" : note;
        }
    }
}
