package com.xiyu.bid.scoreparse.application;

import java.math.BigDecimal;
import java.util.function.Supplier;

/** Structured code extraction first; legacy model extraction is a lazy fallback. */
final class ScoreParseEntryRouter {
    private ScoreParseEntryRouter() {}

    static StructuredTableResult extract(Supplier<StructuredTableResult> structured,
                                         Supplier<StructuredTableResult> fallback) {
        var result = structured.get();
        if (isSatisfactory(result)) {
            return result;
        }
        var recovered = fallback.get();
        return selectBest(result, recovered);
    }

    private static boolean isSatisfactory(StructuredTableResult result) {
        if (result == null) {
            return false;
        }
        if (result.structureIncomplete()) {
            return false;
        }
        if (!result.candidates().isEmpty() && !result.evidencePipeline()) {
            return true;
        }
        if (result.evidencePipeline() && !result.facts().isEmpty()) {
            var completeness = result.completeness();
            if (completeness == null || completeness.complete()) {
                return true;
            }
            // 保护超额/重复项真实证据（如 1135 案 180 分待审核）：不得被 fallback 覆盖
            BigDecimal declared = completeness.declaredTotalScore();
            BigDecimal leafSum = completeness.leafWeightSum();
            if (declared != null && leafSum != null && leafSum.compareTo(declared) >= 0) {
                return true;
            }
            // 若权重严重不足（欠分缺口），不视为 satisfactory，允许 fallback 尝试补救
            return false;
        }
        return false;
    }

    private static StructuredTableResult selectBest(StructuredTableResult result,
                                                    StructuredTableResult recovered) {
        if (recovered == null) {
            return result;
        }
        if (result.structureIncomplete() && !recovered.evidencePipeline()
                && recovered.candidates().isEmpty()) {
            return result;
        }
        // 如果结构化抽取具备事实证据但存在欠分缺口，而 fallback 没有任何有效候选，依然保留原证据
        if (result.evidencePipeline() && !result.facts().isEmpty()
                && recovered.candidates().isEmpty() && recovered.facts().isEmpty()) {
            return result;
        }
        return recovered;
    }
}
