// Input: Deterministic score parsing result
// Output: Immutable auditable ScoreFact
// Pos: scoreparse/domain — pure core (spec 059 Phase 2)
package com.xiyu.bid.scoreparse.domain;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** A score fact whose value can be traced back to source cells and text. */
public record ScoreFact(
        String factId,
        String sourceTableId,
        List<String> sourceCellIds,
        String sourceText,
        String code,
        String dimension,
        BigDecimal weight,
        ScoreEvidence weightEvidence,
        ScoreRule scoreRule,
        String parentFactId,
        String extractionMethod,
        double confidence,
        ValidationStatus validationStatus,
        ChildAggregation childAggregation,
        ScoreSourceMetadata sourceMetadata
) {
    public ScoreFact {
        sourceMetadata = sourceMetadata == null ? ScoreSourceMetadata.EMPTY : sourceMetadata;
        factId = factId == null ? "" : factId;
        sourceTableId = sourceTableId == null ? "" : sourceTableId;
        sourceCellIds = sourceCellIds == null ? List.of() : List.copyOf(sourceCellIds);
        sourceText = sourceText == null ? "" : sourceText;
        code = code == null ? "" : code;
        dimension = dimension == null ? "" : dimension;
        extractionMethod = extractionMethod == null ? "" : extractionMethod;
        scoreRule = scoreRule == null ? ScoreRule.MAX_SCORE : scoreRule;
        validationStatus = validationStatus == null ? ValidationStatus.REVIEW_REQUIRED : validationStatus;
        childAggregation = childAggregation == null ? ChildAggregation.SUM : childAggregation;
    }

    public ScoreFact(String factId, String sourceTableId, List<String> sourceCellIds, String sourceText,
                     String code, String dimension, BigDecimal weight, ScoreEvidence weightEvidence,
                     ScoreRule scoreRule, String parentFactId, String extractionMethod, double confidence,
                     ValidationStatus validationStatus, ChildAggregation childAggregation) {
        this(factId, sourceTableId, sourceCellIds, sourceText, code, dimension, weight, weightEvidence,
                scoreRule, parentFactId, extractionMethod, confidence, validationStatus, childAggregation,
                ScoreSourceMetadata.EMPTY);
    }

    public ScoreFact(String factId, String sourceTableId, List<String> sourceCellIds, String sourceText,
                     String code, String dimension, BigDecimal weight, ScoreEvidence weightEvidence,
                     ScoreRule scoreRule, String parentFactId, String extractionMethod, double confidence,
                     ValidationStatus validationStatus) {
        this(factId, sourceTableId, sourceCellIds, sourceText, code, dimension, weight, weightEvidence,
                scoreRule, parentFactId, extractionMethod, confidence, validationStatus, ChildAggregation.SUM);
    }

    /**
     * {@code true} 当 {@code fact} 在 {@code facts} 集合中是叶子（末级）。
     * <p>叶子是<b>集合属性</b>：没有兄弟 fact 把 {@code fact.factId()} 引用为
     * {@code parentFactId}。空 {@code parentFactId} 只表示根节点（无父），
     * 与是否叶子无关——被引用的必然是父项，未被引用的才是叶子。
     */
    public static boolean isLeaf(ScoreFact fact, List<ScoreFact> facts) {
        if (fact == null) {
            return false;
        }
        return !parentIds(facts).contains(fact.factId());
    }

    /** 集合中作为 {@code parentFactId} 出现过的全部 factId（空值剔除）。 */
    public static Set<String> parentIds(List<ScoreFact> facts) {
        if (facts == null) {
            return Set.of();
        }
        Set<String> ids = new HashSet<>();
        for (ScoreFact fact : facts) {
            if (fact != null && fact.parentFactId() != null && !fact.parentFactId().isBlank()) {
                ids.add(fact.parentFactId());
            }
        }
        return Set.copyOf(ids);
    }

    public boolean isAutoApprovable() {
        return validationStatus == ValidationStatus.AUTO_APPROVED;
    }

    /** Returns a copy of this fact with {@code parentFactId} replaced (structural linkage). */
    public ScoreFact withParentFactId(String newParentFactId) {
        return new ScoreFact(factId, sourceTableId, sourceCellIds, sourceText, code, dimension,
                weight, weightEvidence, scoreRule, newParentFactId, extractionMethod,
                confidence, validationStatus, childAggregation, sourceMetadata);
    }

    /** Returns a copy of this fact with {@code dimension} replaced (semantic classification). */
    public ScoreFact withDimension(String newDimension) {
        return new ScoreFact(factId, sourceTableId, sourceCellIds, sourceText, code, newDimension,
                weight, weightEvidence, scoreRule, parentFactId, extractionMethod,
                confidence, validationStatus, childAggregation, sourceMetadata);
    }

    /** Returns a copy of this fact with {@code validationStatus} replaced. */
    public ScoreFact withValidationStatus(ValidationStatus newStatus) {
        return new ScoreFact(factId, sourceTableId, sourceCellIds, sourceText, code, dimension,
                weight, weightEvidence, scoreRule, parentFactId, extractionMethod,
                confidence, newStatus, childAggregation, sourceMetadata);
    }

    /**
     * 仅限已通过 {@link ScoreFactValidator#validateScoringSet(List)} 判定为
     * 叶子且 {@code AUTO_APPROVED} 的事实调用；父项（被引用为 parent 的
     * fact）调用本方法绕过了"父项不得计入总权重"底线，由调用方负责。
     */
    public Optional<ScoreCandidate> toCandidate() {
        if (!isAutoApprovable() || (weight != null && weight.compareTo(BigDecimal.ZERO) == 0
                && dimension.replaceAll("\\s+", "").matches("(?s).*[（(]不计分[）)].*"))) {
            return Optional.empty();
        }
        return Optional.of(new ScoreCandidate(
                code, dimension, sourceText, weight, new ScoreTypeClassificationPolicy().classify(sourceText), null,
                sourceText, extractionMethod, "EVIDENCE_PIPELINE"));
    }

    public enum ValidationStatus {
        AUTO_APPROVED,
        REVIEW_REQUIRED,
        STRUCTURE_INCOMPLETE,
        DUPLICATE
    }
}
