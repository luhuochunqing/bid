// Input: deterministic ScoreFact pool + LlmDecision
// Output: ValidationResult with violations for ops referencing unknown factIds
// Pos: scoreparse/domain/llm — 纯核心，约束 LLM 不得发明新 fact（spec 059 Phase 3）
package com.xiyu.bid.scoreparse.domain.llm;

import com.xiyu.bid.scoreparse.domain.ScoreFact;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 校验 LLM 决策是否只引用已存在的 {@code factId}（spec 059 Phase 3）。
 * <p>不允许的操作（造新 fact / 改 evidence / 改 weight / 改 factId）在编译期就被
 * sealed {@link LlmFactOp} 接口拒绝；本校验器只关心"引用的 factId 是否在池内"。
 *
 * <h3>使用方式</h3>
 * <p>编排层（如 ScoreGlobalMergeExecutor）在执行 LLM 决策前先调
 * {@link #validate(List, LlmDecision)}：
 * <ul>
 *   <li>{@code result.valid()} 为 true → 决策可应用</li>
 *   <li>{@code result.valid()} 为 false → 决策全部或部分违反 spec 059 Phase 3
 *       约束；调用方按降级策略处理（典型：仅采纳 valid 的 op，或整体 fail-open）</li>
 * </ul>
 *
 * <h3>不变量的处理</h3>
 * <p>FP-Java 原则：domain 层不抛业务异常。所有违规（null 输入、未知 factId）收集到
 * {@link ValidationResult#violations()}，调用方按 Result 模式自行分支。
 */
public final class LlmDecisionValidator {

    private LlmDecisionValidator() {
    }

    /**
     * 校验 LLM 决策：每个 op 引用的 factId 都必须在 {@code pool} 中存在。
     * <p>除池内存在性外，还有两条硬规则：
     * <ul>
     *   <li>{@link LlmFactOp.LinkParent}：child ≠ parent，且两者不得来自不同
     *       表（跨文件/跨表 cell 引用违反证据边界，spec 059 T302）。</li>
     *   <li>{@link LlmFactOp.MarkDuplicate}：{@code keepFactId} 不得出现在
     *       {@code duplicateFactIds} 中。</li>
     * </ul>
     *
     * @return ValidationResult；{@code valid} 为 true 表示决策可应用（无违规）
     */
    public static ValidationResult validate(List<ScoreFact> pool, LlmDecision decision) {
        if (pool == null) {
            return new ValidationResult(false, List.of("pool is null"));
        }
        if (decision == null) {
            return new ValidationResult(false, List.of("decision is null"));
        }
        Map<String, ScoreFact> poolById = new LinkedHashMap<>();
        for (ScoreFact fact : pool) {
            if (fact != null && fact.factId() != null && !fact.factId().isBlank()) {
                poolById.put(fact.factId(), fact);
            }
        }
        List<String> violations = new ArrayList<>();
        List<LlmFactOp> ops = decision.ops();
        for (int i = 0; i < ops.size(); i++) {
            LlmFactOp op = ops.get(i);
            if (op == null) {
                violations.add("op[" + i + "] is null");
                continue;
            }
            violations = collectViolations(op, i, poolById, violations);
        }
        return new ValidationResult(violations.isEmpty(), List.copyOf(violations));
    }

    /**
     * Returns the list of violations introduced by {@code op} against {@code poolById}.
     * <p>Returns a fresh list rather than mutating a passed-in accumulator so the
     * caller's data flow stays value-oriented (FP-Java: methods should return values).
     */
    private static List<String> collectViolations(LlmFactOp op, int index,
                                                  Map<String, ScoreFact> poolById,
                                                  List<String> existing) {
        List<String> out = new ArrayList<>(existing);
        String opName = op.getClass().getSimpleName();
        if (op instanceof LlmFactOp.Keep keep) {
            out = appendPoolViolation(out, opName, index, "factId", keep.factId(), poolById.keySet());
        } else if (op instanceof LlmFactOp.Classify classify) {
            out = appendPoolViolation(out, opName, index, "factId", classify.factId(), poolById.keySet());
            if (classify.category() == null || classify.category().isBlank()) {
                out.add("op[" + index + "] (" + opName + "): category is blank");
            }
        } else if (op instanceof LlmFactOp.LinkParent link) {
            out = appendPoolViolation(out, opName, index, "childFactId", link.childFactId(), poolById.keySet());
            out = appendPoolViolation(out, opName, index, "parentFactId", link.parentFactId(), poolById.keySet());
            if (!link.childFactId().isBlank() && link.childFactId().equals(link.parentFactId())) {
                out.add("op[" + index + "] (" + opName + "): childFactId must differ from parentFactId");
            }
            ScoreFact child = poolById.get(link.childFactId());
            ScoreFact parent = poolById.get(link.parentFactId());
            if (child != null && parent != null
                    && !child.sourceTableId().equals(parent.sourceTableId())) {
                out.add("op[" + index + "] (" + opName + "): cross-table link rejected (child '"
                        + child.sourceTableId() + "' vs parent '" + parent.sourceTableId() + "')");
            }
        } else if (op instanceof LlmFactOp.MarkDuplicate merge) {
            out = appendPoolViolation(out, opName, index, "keepFactId", merge.keepFactId(), poolById.keySet());
            List<String> duplicates = merge.duplicateFactIds();
            if (duplicates == null || duplicates.isEmpty()) {
                out.add("op[" + index + "] (" + opName + "): duplicateFactIds is empty");
                return out;
            }
            for (int j = 0; j < duplicates.size(); j++) {
                out = appendPoolViolation(out, opName, index,
                        "duplicateFactIds[" + j + "]", duplicates.get(j), poolById.keySet());
            }
            // Defensive: keepFactId must not appear in duplicateFactIds.
            if (duplicates.contains(merge.keepFactId())) {
                out.add("op[" + index + "] (" + opName
                        + "): keepFactId must not appear in duplicateFactIds");
            }
        } else if (op instanceof LlmFactOp.MarkReviewRequired mark) {
            out = appendPoolViolation(out, opName, index, "factId", mark.factId(), poolById.keySet());
        }
        // No other permitted types: sealed interface + exhaustive instanceof covers all cases.
        return out;
    }

    /**
     * Returns a fresh list containing any new pool-membership violation for {@code factId}.
     * The original list is copied first so the caller's accumulator stays unchanged on no-op.
     */
    private static List<String> appendPoolViolation(List<String> base, String opName, int index,
                                                   String field, String factId,
                                                   Set<String> poolIds) {
        List<String> out = new ArrayList<>(base);
        if (factId == null || factId.isBlank()) {
            out.add("op[" + index + "] (" + opName + "): " + field + " is blank");
            return out;
        }
        if (!poolIds.contains(factId)) {
            out.add("op[" + index + "] (" + opName + "): " + field
                    + " '" + factId + "' is not in fact pool");
        }
        return out;
    }

    /**
     * @param valid      true 表示决策可应用（无违规）
     * @param violations 违规列表；空表示全部合法
     */
    public record ValidationResult(boolean valid, List<String> violations) {
        public ValidationResult {
            violations = violations == null ? List.of() : List.copyOf(violations);
        }
    }
}
