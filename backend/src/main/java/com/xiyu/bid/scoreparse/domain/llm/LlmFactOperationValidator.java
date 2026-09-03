// Input: deterministic ScoreFact pool + LlmDecision / RawFactOperation list
// Output: ValidationResult with violations for illegal ops, unknown factIds, or tampered fields
// Pos: scoreparse/domain/llm — pure domain core (spec 059 Phase 3, T302)
package com.xiyu.bid.scoreparse.domain.llm;

import com.xiyu.bid.scoreparse.domain.ScoreFact;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 校验 LLM 事实操作是否严格遵守受限适配器契约（spec 059 Phase 3）。
 * <p>拒绝任何试图发明新 fact、修改 weight/detail/sourceText、跨表引用、以及低置信度（&lt;0.80）分类。
 * 违规操作绝不回写事实，一律转为人工复核（REVIEW_REQUIRED）。
 */
public final class LlmFactOperationValidator {

    public static final double MIN_CLASSIFY_CONFIDENCE = 0.80;
    public static final Set<String> ALLOWED_ACTIONS = Set.of(
            "KEEP", "CLASSIFY", "LINK_PARENT", "MARK_DUPLICATE", "REVIEW_REQUIRED"
    );

    private LlmFactOperationValidator() {
    }

    public record ValidationResult(boolean valid, List<String> violations) {
        public ValidationResult {
            violations = violations == null ? List.of() : List.copyOf(violations);
        }
        public boolean hasViolations() {
            return !valid || !violations.isEmpty();
        }
    }

    /** 校验已类型化的领域决策。 */
    public static ValidationResult validate(List<ScoreFact> pool, LlmDecision decision) {
        if (pool == null) {
            return new ValidationResult(false, List.of("pool is null"));
        }
        if (decision == null) {
            return new ValidationResult(false, List.of("decision is null"));
        }
        Map<String, ScoreFact> poolById = toPoolMap(pool);
        List<String> violations = new ArrayList<>();
        List<LlmFactOp> ops = decision.ops();
        for (int i = 0; i < ops.size(); i++) {
            LlmFactOp op = ops.get(i);
            if (op == null) {
                violations.add("op[" + i + "] is null");
                continue;
            }
            violations = validateOp(op, i, poolById, violations);
        }
        return new ValidationResult(violations.isEmpty(), List.copyOf(violations));
    }

    /** 校验原始 wire payload（含非法动作与受保护字段检测）。 */
    public static ValidationResult validateRaw(List<ScoreFact> pool, List<RawFactOperation> rawOps) {
        if (pool == null) {
            return new ValidationResult(false, List.of("pool is null"));
        }
        if (rawOps == null) {
            return new ValidationResult(false, List.of("rawOps is null"));
        }
        Map<String, ScoreFact> poolById = toPoolMap(pool);
        List<String> violations = new ArrayList<>();
        for (int i = 0; i < rawOps.size(); i++) {
            RawFactOperation raw = rawOps.get(i);
            if (raw == null) {
                violations.add("rawOp[" + i + "] is null");
                continue;
            }
            // 1. 受保护字段篡改检测
            if (raw.weight() != null) {
                violations.add("rawOp[" + i + "]: illegal modification of protected field 'weight'");
            }
            if (raw.detail() != null && !raw.detail().isBlank()) {
                violations.add("rawOp[" + i + "]: illegal modification of protected field 'detail'");
            }
            if (raw.sourceText() != null && !raw.sourceText().isBlank()) {
                violations.add("rawOp[" + i + "]: illegal modification of protected field 'sourceText'");
            }
            if (raw.newFact() != null && !raw.newFact().isBlank()) {
                violations.add("rawOp[" + i + "]: illegal creation of 'newFact'");
            }
            // 2. 非法动作类型检测
            if (!ALLOWED_ACTIONS.contains(raw.action())) {
                violations.add("rawOp[" + i + "]: illegal action '" + raw.action() + "'");
                continue;
            }
            // 3. 基础字段与事实存在性检测
            if (raw.factId().isBlank() || !poolById.containsKey(raw.factId())) {
                violations.add("rawOp[" + i + "]: unknown factId '" + raw.factId() + "'");
                continue;
            }
            ScoreFact fact = poolById.get(raw.factId());
            // 4. 动作具体规则
            if ("CLASSIFY".equals(raw.action())) {
                if (raw.category().isBlank()) {
                    violations.add("rawOp[" + i + "]: category is blank");
                }
                if (raw.confidence() < MIN_CLASSIFY_CONFIDENCE) {
                    violations.add("rawOp[" + i + "]: low confidence classification " + raw.confidence());
                }
            } else if ("LINK_PARENT".equals(raw.action())) {
                if (raw.parentFactId().isBlank() || !poolById.containsKey(raw.parentFactId())) {
                    violations.add("rawOp[" + i + "]: unknown parentFactId '" + raw.parentFactId() + "'");
                } else {
                    ScoreFact parent = poolById.get(raw.parentFactId());
                    if (raw.factId().equals(raw.parentFactId())) {
                        violations.add("rawOp[" + i + "]: childFactId must differ from parentFactId");
                    } else if (!fact.sourceTableId().equals(parent.sourceTableId())) {
                        violations.add("rawOp[" + i + "]: cross-table link rejected (child '"
                                + fact.sourceTableId() + "' vs parent '" + parent.sourceTableId() + "')");
                    }
                }
            } else if ("MARK_DUPLICATE".equals(raw.action())) {
                if (raw.duplicateFactIds().isEmpty()) {
                    violations.add("rawOp[" + i + "]: duplicateFactIds is empty");
                } else if (raw.duplicateFactIds().contains(raw.factId())) {
                    violations.add("rawOp[" + i + "]: keepFactId must not appear in duplicateFactIds");
                }
                for (String dupId : raw.duplicateFactIds()) {
                    if (!poolById.containsKey(dupId)) {
                        violations.add("rawOp[" + i + "]: unknown duplicateFactId '" + dupId + "'");
                    }
                }
            }
            // 5. cell 引用跨表检测
            for (String cellId : raw.reasonCellIds()) {
                if (!cellIdBelongsToTable(cellId, fact)) {
                    violations.add("rawOp[" + i + "]: cross-table cell reference '" + cellId + "'");
                }
            }
        }
        return new ValidationResult(violations.isEmpty(), List.copyOf(violations));
    }

    private static List<String> validateOp(LlmFactOp op, int i, Map<String, ScoreFact> poolById, List<String> acc) {
        List<String> out = new ArrayList<>(acc);
        String opName = op.getClass().getSimpleName();
        if (!poolById.containsKey(op.factId())) {
            out.add("op[" + i + "] (" + opName + "): factId '" + op.factId() + "' is not in fact pool");
            return out;
        }
        ScoreFact fact = poolById.get(op.factId());
        for (String cellId : op.reasonCellIds()) {
            if (!cellIdBelongsToTable(cellId, fact)) {
                out.add("op[" + i + "] (" + opName + "): cross-table cell reference '" + cellId + "'");
            }
        }
        if (op instanceof LlmFactOp.Classify classify) {
            if (classify.category().isBlank()) {
                out.add("op[" + i + "] (" + opName + "): category is blank");
            }
            if (classify.confidence() < MIN_CLASSIFY_CONFIDENCE) {
                out.add("op[" + i + "] (" + opName + "): low confidence classification " + classify.confidence());
            }
        } else if (op instanceof LlmFactOp.LinkParent link) {
            if (!poolById.containsKey(link.parentFactId())) {
                out.add("op[" + i + "] (" + opName + "): parentFactId '" + link.parentFactId() + "' is not in fact pool");
            } else {
                ScoreFact parent = poolById.get(link.parentFactId());
                if (link.childFactId().equals(link.parentFactId())) {
                    out.add("op[" + i + "] (" + opName + "): childFactId must differ from parentFactId");
                } else if (!fact.sourceTableId().equals(parent.sourceTableId())) {
                    out.add("op[" + i + "] (" + opName + "): cross-table link rejected");
                }
            }
        } else if (op instanceof LlmFactOp.MarkDuplicate merge) {
            if (merge.duplicateFactIds().isEmpty()) {
                out.add("op[" + i + "] (" + opName + "): duplicateFactIds is empty");
            } else if (merge.duplicateFactIds().contains(merge.keepFactId())) {
                out.add("op[" + i + "] (" + opName + "): keepFactId must not appear in duplicateFactIds");
            }
            for (String dupId : merge.duplicateFactIds()) {
                if (!poolById.containsKey(dupId)) {
                    out.add("op[" + i + "] (" + opName + "): duplicateFactId '" + dupId + "' is not in fact pool");
                }
            }
        }
        return out;
    }

    /** 纯函数：将经过校验的决策安全应用到事实池。若决策含违规，绝不回写事实，受影响项转 REVIEW_REQUIRED。 */
    public static List<ScoreFact> apply(List<ScoreFact> pool, LlmDecision decision) {
        if (pool == null) {
            return List.of();
        }
        if (decision == null || decision.ops().isEmpty()) {
            return List.copyOf(pool);
        }
        ValidationResult validation = validate(pool, decision);
        if (!validation.valid()) {
            return markViolatedFacts(pool, decision, validation);
        }
        Map<String, ScoreFact> current = new LinkedHashMap<>();
        for (ScoreFact f : pool) {
            current.put(f.factId(), f);
        }
        for (LlmFactOp op : decision.ops()) {
            if (op instanceof LlmFactOp.Classify classify && classify.confidence() >= MIN_CLASSIFY_CONFIDENCE) {
                ScoreFact f = current.get(classify.factId());
                if (f != null && !classify.category().isBlank()) {
                    current.put(f.factId(), f.withDimension(classify.category()));
                }
            } else if (op instanceof LlmFactOp.LinkParent link) {
                ScoreFact child = current.get(link.childFactId());
                if (child != null) {
                    current.put(child.factId(), child.withParentFactId(link.parentFactId()));
                }
            } else if (op instanceof LlmFactOp.MarkDuplicate dup) {
                for (String dupId : dup.duplicateFactIds()) {
                    ScoreFact f = current.get(dupId);
                    if (f != null) {
                        current.put(dupId, f.withValidationStatus(ScoreFact.ValidationStatus.DUPLICATE));
                    }
                }
            } else if (op instanceof LlmFactOp.MarkReviewRequired rev) {
                ScoreFact f = current.get(rev.factId());
                if (f != null) {
                    current.put(rev.factId(), f.withValidationStatus(ScoreFact.ValidationStatus.REVIEW_REQUIRED));
                }
            }
        }
        return List.copyOf(current.values());
    }

    /** 违规时保持原事实不修改字段，将违规 op 涉及的 fact 置为 REVIEW_REQUIRED。 */
    public static List<ScoreFact> markViolatedFacts(List<ScoreFact> pool, List<RawFactOperation> rawOps) {
        if (pool == null) {
            return List.of();
        }
        Map<String, ScoreFact> current = new LinkedHashMap<>();
        for (ScoreFact f : pool) {
            current.put(f.factId(), f);
        }
        if (rawOps != null) {
            for (RawFactOperation raw : rawOps) {
                if (raw != null && current.containsKey(raw.factId())) {
                    ScoreFact f = current.get(raw.factId());
                    current.put(f.factId(), f.withValidationStatus(ScoreFact.ValidationStatus.REVIEW_REQUIRED));
                }
            }
        }
        return List.copyOf(current.values());
    }

    public static List<ScoreFact> markViolatedFacts(List<ScoreFact> pool, LlmDecision decision, ValidationResult val) {
        if (pool == null) {
            return List.of();
        }
        Map<String, ScoreFact> current = new LinkedHashMap<>();
        for (ScoreFact f : pool) {
            current.put(f.factId(), f);
        }
        if (decision != null) {
            for (LlmFactOp op : decision.ops()) {
                if (op != null && current.containsKey(op.factId())) {
                    ScoreFact f = current.get(op.factId());
                    current.put(f.factId(), f.withValidationStatus(ScoreFact.ValidationStatus.REVIEW_REQUIRED));
                }
            }
        }
        return List.copyOf(current.values());
    }

    private static boolean cellIdBelongsToTable(String cellId, ScoreFact fact) {
        if (cellId == null || cellId.isBlank() || fact == null) {
            return false;
        }
        if (fact.sourceCellIds().contains(cellId)) {
            return true;
        }
        return !fact.sourceTableId().isBlank() && cellId.startsWith(fact.sourceTableId());
    }

    private static Map<String, ScoreFact> toPoolMap(List<ScoreFact> pool) {
        Map<String, ScoreFact> map = new LinkedHashMap<>();
        for (ScoreFact fact : pool) {
            if (fact != null && fact.factId() != null && !fact.factId().isBlank()) {
                map.put(fact.factId(), fact);
            }
        }
        return Collections.unmodifiableMap(map);
    }
}
