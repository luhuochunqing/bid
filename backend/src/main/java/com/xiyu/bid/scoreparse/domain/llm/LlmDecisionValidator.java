// Input: deterministic ScoreFact pool + LlmDecision
// Output: ValidationResult with violations for ops referencing unknown factIds
// Pos: scoreparse/domain/llm — 纯核心，约束 LLM 不得发明新 fact（spec 059 Phase 3）
package com.xiyu.bid.scoreparse.domain.llm;

import com.xiyu.bid.scoreparse.domain.ScoreFact;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
     * <p>对于 {@link LlmFactOp.MergeDuplicates}，{@code keepFactId} 与
     * {@code removeFactIds} 列表中每个 id 都必须存在于池内。
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
        Set<String> poolIds = new HashSet<>();
        for (ScoreFact fact : pool) {
            if (fact != null && fact.factId() != null && !fact.factId().isBlank()) {
                poolIds.add(fact.factId());
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
            violations = collectViolations(op, i, poolIds, violations);
        }
        return new ValidationResult(violations.isEmpty(), List.copyOf(violations));
    }

    /**
     * Returns the list of violations introduced by {@code op} against {@code poolIds}.
     * <p>Returns a fresh list rather than mutating a passed-in accumulator so the
     * caller's data flow stays value-oriented (FP-Java: methods should return values).
     */
    private static List<String> collectViolations(LlmFactOp op, int index,
                                                  Set<String> poolIds, List<String> existing) {
        List<String> out = new ArrayList<>(existing);
        String opName = op.getClass().getSimpleName();
        if (op instanceof LlmFactOp.SelectFact select) {
            out = appendPoolViolation(out, opName, index, "factId", select.factId(), poolIds);
        } else if (op instanceof LlmFactOp.MergeDuplicates merge) {
            out = appendPoolViolation(out, opName, index, "keepFactId", merge.keepFactId(), poolIds);
            List<String> removes = merge.removeFactIds();
            if (removes == null || removes.isEmpty()) {
                out.add("op[" + index + "] (" + opName + "): removeFactIds is empty");
                return out;
            }
            for (int j = 0; j < removes.size(); j++) {
                out = appendPoolViolation(out, opName, index,
                        "removeFactIds[" + j + "]", removes.get(j), poolIds);
            }
            // Defensive: keepFactId must not appear in removeFactIds.
            if (removes.contains(merge.keepFactId())) {
                out.add("op[" + index + "] (" + opName
                        + "): keepFactId must not appear in removeFactIds");
            }
        } else if (op instanceof LlmFactOp.MarkReviewRequired mark) {
            out = appendPoolViolation(out, opName, index, "factId", mark.factId(), poolIds);
        } else if (op instanceof LlmFactOp.AddObservation obs) {
            out = appendPoolViolation(out, opName, index, "factId", obs.factId(), poolIds);
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
