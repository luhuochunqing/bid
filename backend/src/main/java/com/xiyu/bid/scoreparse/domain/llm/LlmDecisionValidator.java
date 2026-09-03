// Input: deterministic ScoreFact pool + LlmDecision
// Output: ValidationResult with violations for ops referencing unknown factIds
// Pos: scoreparse/domain/llm — 纯核心，委托 LlmFactOperationValidator（spec 059 Phase 3）
package com.xiyu.bid.scoreparse.domain.llm;

import com.xiyu.bid.scoreparse.domain.ScoreFact;

import java.util.List;

/**
 * 历史别名类，委托给 {@link LlmFactOperationValidator}（spec 059 Phase 3）。
 */
public final class LlmDecisionValidator {

    private LlmDecisionValidator() {
    }

    public static ValidationResult validate(List<ScoreFact> pool, LlmDecision decision) {
        var res = LlmFactOperationValidator.validate(pool, decision);
        return new ValidationResult(res.valid(), res.violations());
    }

    public record ValidationResult(boolean valid, List<String> violations) {
        public ValidationResult {
            violations = violations == null ? List.of() : List.copyOf(violations);
        }
    }
}
