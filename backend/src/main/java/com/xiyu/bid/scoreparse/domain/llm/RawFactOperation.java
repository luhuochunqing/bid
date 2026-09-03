// Input: Unvalidated LLM structured output wire payload
// Output: Value object passed to LlmFactOperationValidator for audit
// Pos: scoreparse/domain/llm — pure domain DTO (spec 059 Phase 3)
package com.xiyu.bid.scoreparse.domain.llm;

import java.util.ArrayList;
import java.util.List;

/**
 * Wire-level representation of an operation emitted by LLM before validation.
 * <p>Contains trap fields (weight, detail, sourceText, newFact) to detect and reject
 * any attempt by LLM to invent facts or tamper with protected evidence.
 */
public record RawFactOperation(
        String factId,
        String action,
        String category,
        String parentFactId,
        List<String> duplicateFactIds,
        Double confidence,
        List<String> reasonCellIds,
        String reason,
        Double weight,
        String detail,
        String sourceText,
        String newFact
) {
    public RawFactOperation {
        factId = factId == null ? "" : factId;
        action = action == null ? "" : action.trim().toUpperCase();
        category = category == null ? "" : category;
        parentFactId = parentFactId == null ? "" : parentFactId;
        duplicateFactIds = duplicateFactIds == null ? List.of() : List.copyOf(duplicateFactIds);
        confidence = confidence == null ? 1.0 : confidence;
        reasonCellIds = reasonCellIds == null ? List.of() : List.copyOf(reasonCellIds);
        reason = reason == null ? "" : reason;
    }

    public static RawFactOperation of(String factId, String action, String category,
                                      String parentFactId, List<String> duplicateFactIds,
                                      Double confidence, List<String> reasonCellIds, String reason) {
        return new RawFactOperation(factId, action, category, parentFactId, duplicateFactIds,
                confidence, reasonCellIds, reason, null, null, null, null);
    }

    /** 将已审计/待处理的原始 Wire 操作统一映射为领域不可变操作对象 */
    public static LlmDecision toDecision(List<RawFactOperation> rawOps) {
        if (rawOps == null || rawOps.isEmpty()) {
            return new LlmDecision(List.of());
        }
        List<LlmFactOp> ops = new ArrayList<>();
        for (RawFactOperation raw : rawOps) {
            if (raw == null) {
                continue;
            }
            switch (raw.action()) {
                case "KEEP" -> ops.add(new LlmFactOp.Keep(raw.factId(), raw.confidence(), raw.reasonCellIds()));
                case "CLASSIFY" -> ops.add(new LlmFactOp.Classify(raw.factId(), raw.category(), raw.confidence(), raw.reasonCellIds()));
                case "LINK_PARENT" -> ops.add(new LlmFactOp.LinkParent(raw.factId(), raw.parentFactId(), raw.confidence(), raw.reasonCellIds()));
                case "MARK_DUPLICATE" -> ops.add(new LlmFactOp.MarkDuplicate(raw.factId(), raw.duplicateFactIds(), raw.confidence(), raw.reasonCellIds()));
                case "REVIEW_REQUIRED" -> ops.add(new LlmFactOp.MarkReviewRequired(raw.factId(), raw.reason(), raw.confidence(), raw.reasonCellIds()));
                default -> {}
            }
        }
        return new LlmDecision(ops);
    }
}
