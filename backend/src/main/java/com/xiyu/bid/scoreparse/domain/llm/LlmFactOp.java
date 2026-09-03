// Input: deterministic ScoreFact pool + LLM-emitted decision referencing factIds
// Output: validated LlmDecision that only references existing factIds
// Pos: scoreparse/domain/llm — 纯核心，约束 LLM 不得发明新 fact（spec 059 Phase 3）
package com.xiyu.bid.scoreparse.domain.llm;

import java.util.List;

/**
 * LLM 允许的 fact 操作类型（spec 059 Plan 契约：KEEP / CLASSIFY /
 * LINK_PARENT / MARK_DUPLICATE / REVIEW_REQUIRED）。
 * <p>Phase 3 的核心约束：LLM 不得从无到有创造新 fact，也不得改 evidence /
 * weight / factId；它只能对已有的 {@code factId} 执行保留、归类、父子关联、
 * 折叠近重复与标记复核。
 * <p>本 sealed interface 在编译期枚举允许的操作；任何"造新 fact / 改证据"的
 * 企图都被类型系统拒之门外。
 *
 * <h3>不变量的处理</h3>
 * <p>FP-Java 原则：domain 层不抛业务异常。{@link LlmDecisionValidator} 对引用不存在
 * {@code factId} 的 op 收集到 {@code ValidationResult.violations}，调用方按
 * fail-open / reject 策略自行处理。
 */
public sealed interface LlmFactOp
        permits LlmFactOp.Keep, LlmFactOp.Classify, LlmFactOp.LinkParent,
                LlmFactOp.MarkDuplicate, LlmFactOp.MarkReviewRequired {

    /** KEEP：选取某个 fact 作为权威版本（confidence 由 LLM 自报）。 */
    record Keep(String factId, double confidence) implements LlmFactOp {
        public Keep {
            factId = factId == null ? "" : factId;
        }
    }

    /** CLASSIFY：对已有 fact 做语义归类；{@code category} 仅作为元数据，不改证据链路。 */
    record Classify(String factId, String category, double confidence) implements LlmFactOp {
        public Classify {
            factId = factId == null ? "" : factId;
            category = category == null ? "" : category;
        }
    }

    /** LINK_PARENT：把 {@code childFactId} 挂到已存在的 {@code parentFactId} 下（父子关联）。 */
    record LinkParent(String childFactId, String parentFactId) implements LlmFactOp {
        public LinkParent {
            childFactId = childFactId == null ? "" : childFactId;
            parentFactId = parentFactId == null ? "" : parentFactId;
        }
    }

    /** MARK_DUPLICATE：将 {@code duplicateFactIds} 折叠到 {@code keepFactId}；重复项被下游标记弃用。 */
    record MarkDuplicate(String keepFactId, List<String> duplicateFactIds) implements LlmFactOp {
        public MarkDuplicate {
            keepFactId = keepFactId == null ? "" : keepFactId;
            duplicateFactIds = duplicateFactIds == null ? List.of() : List.copyOf(duplicateFactIds);
        }
    }

    /** REVIEW_REQUIRED：标记某个 fact 需要人工复核；reason 是人类可读的简短说明。 */
    record MarkReviewRequired(String factId, String reason) implements LlmFactOp {
        public MarkReviewRequired {
            factId = factId == null ? "" : factId;
            reason = reason == null ? "" : reason;
        }
    }
}
