package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import com.xiyu.bid.scoreparse.domain.ScoreFact;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 结构化表格提取与模型提取的入口路由仲裁器。
 * <p>契约：
 * 1. 结构化抽取优先；模型抽取作为懒加载 fallback；
 * 2. 满分闭合与真实超额证据保护：已核验完整或超额的证据（如 1135 案 180 分待审事实）严禁被 fallback 覆盖；
 * 3. 方案与总分一致性核验：fallback 声明的局部总分（如 20/20）绝不能伪充全案完整覆盖目标总分（如 48/100）；
 * 4. 严格防倒退（Monotonicity & Non-Regression）：若 fallback 提取有效分值更低或距离目标总分更远，
 *    严禁覆盖已有结构化事实，必须保留原事实及其诊断；
 * 5. 诊断保全：当 fallback 证明更优被采纳时，原结构化抽取的 sourceDiagnostics（如漏项诊断）必须完整合并保留；
 *    若存在方案总分冲突，记录冲突诊断。
 */
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
        if (result == null || result.structureIncomplete()) {
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
        if (result == null) {
            return recovered;
        }
        if (result.structureIncomplete()) {
            if (!hasContent(recovered)) {
                return result;
            }
            return mergeDiagnostics(recovered, result.sourceDiagnostics());
        }
        if (!hasContent(result)) {
            return recovered;
        }
        if (!hasContent(recovered)) {
            return result;
        }

        BigDecimal declared = resolveDeclaredTotal(result, recovered);
        BigDecimal scoreResult = effectiveWeight(result);
        BigDecimal scoreRecovered = effectiveWeight(recovered);

        if (isSuperior(scoreResult, result, scoreRecovered, recovered, declared)) {
            return mergeDiagnostics(recovered, result.sourceDiagnostics());
        }

        // fallback 未能证明更优或存在倒退风险，坚决保留原结构化事实与漏项诊断
        // 若 fallback 声明了冲突总分，记录冲突诊断以保留双方证据
        if (recovered.completeness() != null && recovered.completeness().declaredTotalScore() != null) {
            BigDecimal recDeclared = recovered.completeness().declaredTotalScore();
            if (recDeclared.compareTo(declared) != 0) {
                result = mergeDiagnostics(result, List.of("FALLBACK_SCHEME_CONFLICT:declared=" + recDeclared + ",target=" + declared));
            }
        }
        return result;
    }

    private static boolean hasContent(StructuredTableResult r) {
        if (r == null || r.structureIncomplete()) {
            return false;
        }
        return (r.facts() != null && !r.facts().isEmpty())
                || (r.candidates() != null && !r.candidates().isEmpty());
    }

    private static boolean isSuperior(BigDecimal scoreResult, StructuredTableResult result,
                                      BigDecimal scoreRecovered, StructuredTableResult recovered,
                                      BigDecimal targetDeclared) {
        if (recovered.structureIncomplete()) {
            return false;
        }
        if (recovered.candidates().isEmpty() && recovered.facts().isEmpty()) {
            return false;
        }

        // 1. 防倒退硬约束：无论 fallback 在其自身局部声明总分下是否判定为“完整”，
        // 只要有效分值不大于主结果，绝对不允许覆盖主结果（杜绝 20/20 覆盖 48/100）
        if (scoreRecovered.compareTo(scoreResult) <= 0) {
            return false;
        }

        // 2. 方案与总分一致性核验：若主结果已确立目标总分（如 100），而 fallback 声明了一个更小的冲突总分（如 20），
        // 说明 fallback 仅抽取了局部子方案，绝不能覆盖主结果
        BigDecimal recoveredDeclared = recovered.completeness() != null
                ? recovered.completeness().declaredTotalScore() : null;
        if (recoveredDeclared != null && recoveredDeclared.compareTo(targetDeclared) < 0) {
            return false;
        }

        // 3. 目标总分下的完整性判定：必须在目标总分下完整闭合
        boolean resultComplete = isCompleteForTarget(result, targetDeclared);
        boolean recoveredComplete = isCompleteForTarget(recovered, targetDeclared);

        if (resultComplete) {
            return false;
        }
        if (recoveredComplete) {
            return true;
        }

        // 4. 两者均未在目标总分下闭合时，fallback 必须更接近目标总分
        BigDecimal distResult = targetDeclared.subtract(scoreResult).abs();
        BigDecimal distRecovered = targetDeclared.subtract(scoreRecovered).abs();
        return distRecovered.compareTo(distResult) < 0;
    }

    private static BigDecimal effectiveWeight(StructuredTableResult r) {
        if (r == null || r.structureIncomplete()) {
            return BigDecimal.ZERO;
        }
        if (r.evidencePipeline()) {
            if (r.completeness() != null && r.completeness().leafWeightSum() != null) {
                return r.completeness().leafWeightSum();
            }
            if (r.facts() != null && !r.facts().isEmpty()) {
                return r.facts().stream()
                        .map(ScoreFact::weight)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }
        }
        if (r.candidates() != null && !r.candidates().isEmpty()) {
            return r.candidates().stream()
                    .map(ScoreCandidate::weight)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        return BigDecimal.ZERO;
    }

    private static boolean isCompleteForTarget(StructuredTableResult r, BigDecimal targetTotal) {
        if (r == null || r.structureIncomplete()) {
            return false;
        }
        if (r.evidencePipeline() && r.completeness() != null) {
            if (!r.completeness().complete()) {
                return false;
            }
            BigDecimal declared = r.completeness().declaredTotalScore();
            if (declared != null && declared.compareTo(targetTotal) != 0) {
                return false;
            }
            BigDecimal leafSum = r.completeness().leafWeightSum();
            return leafSum != null && leafSum.compareTo(targetTotal) >= 0;
        }
        BigDecimal weight = effectiveWeight(r);
        return weight.compareTo(targetTotal) >= 0;
    }

    private static BigDecimal resolveDeclaredTotal(StructuredTableResult result, StructuredTableResult recovered) {
        if (result != null && result.completeness() != null && result.completeness().declaredTotalScore() != null) {
            return result.completeness().declaredTotalScore();
        }
        if (recovered != null && recovered.completeness() != null && recovered.completeness().declaredTotalScore() != null) {
            return recovered.completeness().declaredTotalScore();
        }
        return new BigDecimal("100");
    }

    private static StructuredTableResult mergeDiagnostics(StructuredTableResult target,
                                                          List<String> extraDiagnostics) {
        if (extraDiagnostics == null || extraDiagnostics.isEmpty()) {
            return target;
        }
        List<String> merged = new ArrayList<>(target.sourceDiagnostics());
        for (String diag : extraDiagnostics) {
            if (!merged.contains(diag)) {
                merged.add(diag);
            }
        }
        return new StructuredTableResult(target.candidates(), target.structureIncomplete(),
                target.failureReason(), target.evidencePipeline(), target.facts(),
                target.completeness(), target.tableCompletenesses(), target.originalProofs(),
                target.aiProviderDiagnostics(), target.qualifications(), List.copyOf(merged));
    }
}
