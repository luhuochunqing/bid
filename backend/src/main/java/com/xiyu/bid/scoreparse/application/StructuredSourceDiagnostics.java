package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.ScoreCompletenessPolicy;
import com.xiyu.bid.scoreparse.domain.ScoreFact;
import com.xiyu.bid.scoreparse.domain.ScoreParseDecision;
import com.xiyu.bid.scoreparse.domain.ScoreSourceMetadata;
import java.util.ArrayList;
import java.util.List;

/** Retains source failures independently of candidates, and never turns incomplete acquisition into proof. */
final class StructuredSourceDiagnostics {
    private StructuredSourceDiagnostics() {}
    static StructuredTableResult attach(StructuredTableResult result, List<String> diagnostics) {
        if (diagnostics.isEmpty()) return result;
        var notes = new ArrayList<>(result.sourceDiagnostics());
        notes.addAll(diagnostics);
        List<String> all = notes.stream().distinct().toList();
        var facts = result.facts().stream().map(fact -> annotate(fact, all)).toList();
        var old = result.completeness();
        ScoreCompletenessPolicy.ScoreCompletenessResult completeness = old == null ? null
                : new ScoreCompletenessPolicy.ScoreCompletenessResult(false, ScoreParseDecision.REVIEW_REQUIRED,
                "SOURCE_EVIDENCE_INCOMPLETE", old.declaredTotalScore(), old.declaredTotalSource(), old.leafWeightSum(),
                old.leafCount(), old.parentCount(), old.missingEvidenceCount(),
                java.util.stream.Stream.concat(old.defects().stream(), all.stream()).distinct().toList(),
                old.leafFacts().stream().map(fact -> annotate(fact, all)).toList(),
                old.parentFacts().stream().map(fact -> annotate(fact, all)).toList());
        return new StructuredTableResult(List.of(), result.structureIncomplete(), result.failureReason(),
                result.evidencePipeline(), facts, completeness, result.tableCompletenesses(), List.of(),
                result.aiProviderDiagnostics(), result.qualifications(), all);
    }

    static List<String> forValidation(com.xiyu.bid.scoreparse.domain.SourceRecheckPlan plan,
            com.xiyu.bid.scoreparse.domain.MultimodalScoreFactValidator.ValidationReport validation) {
        return java.util.stream.Stream.concat(plan == null ? java.util.stream.Stream.empty() : plan.diagnostics().stream(),
                validation.violations().stream().filter(v -> "SOURCE_SCORE_ROW_UNCOVERED".equals(v.code()))
                        .map(v -> v.code() + ":" + v.evidence())).distinct().toList();
    }

    static StructuredTableResult attachFallback(StructuredTableResult result,
            com.xiyu.bid.scoreparse.domain.SourceRecheckPlan plan,
            com.xiyu.bid.scoreparse.domain.OriginalScoringContext context,
            com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact artifact) {
        var verification = com.xiyu.bid.scoreparse.domain.OriginalScoreEvidencePolicy.verifyFacts(context, artifact, result.facts());
        var notes = verification.problems().stream().filter(p -> "SOURCE_SCORE_ROW_UNCOVERED".equals(p.code()))
                .map(p -> p.code() + ":" + p.locator());
        return attach(result, java.util.stream.Stream.concat(
                plan == null ? java.util.stream.Stream.empty() : plan.diagnostics().stream(), notes).distinct().toList());
    }

    private static ScoreFact annotate(ScoreFact fact, List<String> diagnostics) {
        var old = fact.sourceMetadata();
        var metadata = new ScoreSourceMetadata(old.modelRuleText(), old.modelQuotedText(), old.sourceSpans(),
                java.util.stream.Stream.concat(old.diagnostics().stream(), diagnostics.stream()).distinct().toList(),
                old.modelCategory(), old.modelScoreType(), old.modelSourceClaim());
        return new ScoreFact(fact.factId(), fact.sourceTableId(), fact.sourceCellIds(), fact.sourceText(), fact.code(),
                fact.dimension(), fact.weight(), fact.weightEvidence(), fact.scoreRule(), fact.parentFactId(),
                fact.extractionMethod(), fact.confidence(), ScoreFact.ValidationStatus.REVIEW_REQUIRED,
                fact.childAggregation(), metadata);
    }
}
