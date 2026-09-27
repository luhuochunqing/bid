package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.ScoreParseDecision;
import com.xiyu.bid.scoreparse.domain.WeightNormalizationPolicy;
import com.xiyu.bid.scoreparse.domain.KnowledgeCategoryPolicy;
import com.xiyu.bid.scoreparse.domain.AutoFailCircuit;
import com.xiyu.bid.scoreparse.domain.ScoreFactValidator;
import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import com.xiyu.bid.scoreparse.domain.OriginalScoringContext;
import com.xiyu.bid.scoreparse.domain.ScoreAssessmentGuard;
import com.xiyu.bid.scoreparse.domain.ScoreItemChapterMatch;
import com.xiyu.bid.scoreparse.domain.RecallOutcome;
import com.xiyu.bid.scoreparse.domain.ScoreTableSectionPolicy;
import com.xiyu.bid.scoreparse.domain.StructuredTableCandidateExtractor;
import com.xiyu.bid.scoreparse.domain.SourceSpan;
import com.xiyu.bid.scoreparse.domain.ScoreParseConstants;
import com.xiyu.bid.scoreparse.domain.ScoreStatusPolicy;
import com.xiyu.bid.scoreparse.domain.ModelSourceClaim;
import com.xiyu.bid.scoreparse.domain.ChildAggregation;
import com.xiyu.bid.scoreparse.domain.ScoreVersionFingerprint;
import com.xiyu.bid.scoreparse.domain.ScoreReviewAdoptionPolicy;
import com.xiyu.bid.scoreparse.domain.MatchTierPolicy;
import com.xiyu.bid.scoreparse.domain.ScoreExtractionSnapshot;
import com.xiyu.bid.scoreparse.domain.OriginalScoreEvidencePolicy;
import com.xiyu.bid.scoreparse.domain.BidChapterDirtySet;
import com.xiyu.bid.scoreparse.domain.ScoreParsePipeline;
import com.xiyu.bid.scoreparse.domain.ScoreVocabularyRegistry;
import com.xiyu.bid.scoreparse.domain.ScoreItemTitleExtractor;
import com.xiyu.bid.scoreparse.domain.ScoreTableRecallLane;
import com.xiyu.bid.scoreparse.domain.EvidenceText;
import com.xiyu.bid.scoreparse.domain.ScoreRangeGuard;
import com.xiyu.bid.scoreparse.domain.ScoreTableBlockLocator;
import com.xiyu.bid.scoreparse.domain.ScoreUnitPolicy;
import com.xiyu.bid.scoreparse.domain.AutoParseGate;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import com.xiyu.bid.scoreparse.domain.TableSplitResult;
import com.xiyu.bid.scoreparse.domain.WeightSumCheck;
import com.xiyu.bid.scoreparse.domain.AiProviderDiagnostics;
import com.xiyu.bid.scoreparse.domain.ScoreItemOrderPolicy;
import com.xiyu.bid.scoreparse.domain.ScoreCodeSanitizer;
import com.xiyu.bid.scoreparse.domain.TableCompleteness;
import com.xiyu.bid.scoreparse.domain.SourceAnchorCatalog;
import com.xiyu.bid.scoreparse.domain.ScoreCompletenessPolicy;
import com.xiyu.bid.scoreparse.domain.DocumentImage;
import com.xiyu.bid.scoreparse.domain.ScoreTableSummaryPolicy;
import com.xiyu.bid.scoreparse.domain.DeterministicScoreFactExtractor;
import com.xiyu.bid.scoreparse.domain.MultimodalFactParentCodeNormalizer;
import com.xiyu.bid.scoreparse.domain.UnknownDeclaredTotalReviewPolicy;
import com.xiyu.bid.scoreparse.domain.ItemCountCheck;
import com.xiyu.bid.scoreparse.domain.SidecarTableExtraction;
import com.xiyu.bid.scoreparse.domain.OriginalRuleUnitText;
import com.xiyu.bid.scoreparse.domain.ExtractedScoreItem;
import com.xiyu.bid.scoreparse.domain.ScoreSourceMetadata;
import com.xiyu.bid.scoreparse.domain.SummaryAggregator;
import com.xiyu.bid.scoreparse.domain.ScoreTableSplitPolicy;
import com.xiyu.bid.scoreparse.domain.PartialScorePolicy;
import com.xiyu.bid.scoreparse.domain.SourceRecheckPlan;
import com.xiyu.bid.scoreparse.domain.ScoreMergeResultGuard;
import com.xiyu.bid.scoreparse.domain.ScoreTypeClassificationPolicy;
import com.xiyu.bid.scoreparse.domain.ScoreTableRowParser;
import com.xiyu.bid.scoreparse.domain.ScoreOriginalTextAligner;
import com.xiyu.bid.scoreparse.domain.ImageBudget;
import com.xiyu.bid.scoreparse.domain.WeightOverloadTrimPolicy;
import com.xiyu.bid.scoreparse.domain.GhostCandidateFilter;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFactValidator;
import com.xiyu.bid.scoreparse.domain.ScoreItemMergePolicy;
import com.xiyu.bid.scoreparse.domain.SourceSpanResolver;
import com.xiyu.bid.scoreparse.domain.ScoreParseReviewSnapshot;
import com.xiyu.bid.scoreparse.domain.ScoreEvidence;
import com.xiyu.bid.scoreparse.domain.ScoreFact;
import com.xiyu.bid.scoreparse.domain.BidScoreSkipPolicy;
import com.xiyu.bid.scoreparse.domain.ScoreRule;
import com.xiyu.bid.scoreparse.domain.OriginalRuleRestoration;
import com.xiyu.bid.scoreparse.domain.ProjectEvidenceRequirementPolicy;
import com.xiyu.bid.scoreparse.domain.LogicalRuleSourceUnit;
import com.xiyu.bid.scoreparse.domain.DeductionItemPolicy;
import com.xiyu.bid.scoreparse.domain.ImageAssignmentPlan;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.util.ArrayList;
import java.util.List;

/** Retains alternative extraction proposals; a closed fallback is one selected proposal, never their sum. */
final class FallbackCandidateRetention {
    private FallbackCandidateRetention() {}
    private static ScoreFact locate(ScoreFact f, DocumentArtifact artifact) {
        var m=f.sourceMetadata();
        var spans=m.sourceSpans().isEmpty()?SourceSpanResolver.resolve(artifact,f.sourceCellIds()):m.sourceSpans();
        return new ScoreFact(f.factId(),f.sourceTableId(),f.sourceCellIds(),f.sourceText(),f.code(),f.dimension(),
                f.weight(),f.weightEvidence(),f.scoreRule(),f.parentFactId(),f.extractionMethod(),f.confidence(),
                f.validationStatus(),f.childAggregation(),new ScoreSourceMetadata(m.modelRuleText(),m.modelQuotedText(),
                spans,java.util.stream.Stream.concat(m.diagnostics().stream(),java.util.stream.Stream.of("EXTRACTION_SET:SELECTED_DETERMINISTIC_FALLBACK")).toList(),
                m.modelCategory(),m.modelScoreType(),m.modelSourceClaim()));
    }
    static StructuredTableResult retain(StructuredTableResult selected,
            MultimodalScoreFactValidator.ValidationReport validation, DocumentArtifact artifact) {
        var facts = new ArrayList<>(selected.facts().stream().map(f -> locate(f, artifact)).toList());
        String selectedIds = selected.facts().stream().map(ScoreFact::factId).sorted()
                .collect(java.util.stream.Collectors.joining(","));
        for (var model : validation.facts()) {
            var f = TextPrimaryScoreFactMapper.map(model, artifact, validation);
            var m = f.sourceMetadata();
            var diagnostics = new ArrayList<>(m.diagnostics());
            diagnostics.add("ALTERNATIVE_EXTRACTION_SET_NOT_SELECTED");
            diagnostics.add("EXTRACTION_SET:MODEL_BEFORE_DETERMINISTIC_FALLBACK");
            diagnostics.add("SELECTED_EXTRACTION_FACTS:" + selectedIds);
            facts.add(new ScoreFact(f.factId(), f.sourceTableId(), f.sourceCellIds(), f.sourceText(),
                    f.code(), f.dimension(), f.weight(), f.weightEvidence(), f.scoreRule(), f.parentFactId(),
                    f.extractionMethod(), f.confidence(), f.validationStatus(), f.childAggregation(),
                    new ScoreSourceMetadata(m.modelRuleText(), m.modelQuotedText(), m.sourceSpans(), diagnostics,
                            m.modelCategory(), m.modelScoreType(), m.modelSourceClaim())));
        }
        var qualifications = new ArrayList<>(selected.qualifications());
        qualifications.addAll(validation.sourceVerification().qualifications());
        var diagnostics = new ArrayList<>(selected.sourceDiagnostics());
        diagnostics.add("DETERMINISTIC_FALLBACK_SELECTED;MODEL_PROPOSAL_RETAINED");
        return new StructuredTableResult(selected.candidates(), selected.structureIncomplete(), selected.failureReason(),
                true, List.copyOf(facts), selected.completeness(), selected.tableCompletenesses(),
                selected.originalProofs(), selected.aiProviderDiagnostics(), List.copyOf(qualifications), diagnostics);
    }
}
