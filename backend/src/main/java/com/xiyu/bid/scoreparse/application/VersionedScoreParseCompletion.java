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
import com.xiyu.bid.scoreparse.entity.ScoreParseTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** New completion route never invokes legacy formal-item publishing. */
@Service @RequiredArgsConstructor
public class VersionedScoreParseCompletion {
    private final ScoreExtractionSnapshotWriter writer;
    private final ScoreVersionActivationService activation;
    private final ScoreExtractionTaskFinalizer finalizer;
    private final ScoreParseProgressService progress;
    public boolean enabledFor(ScoreParseTask task){return writer.usesVersioning(task.getId());}
    public boolean source(ScoreParseTask task,TenderTextSource source){return finalizer.source(task,source);}
    public void structured(ScoreParseTask task,StructuredTableResult result) {
        Map<String,Object> diagnostics=new LinkedHashMap<>();
        diagnostics.put("completeness",result.completeness());diagnostics.put("tables",result.tableCompletenesses());
        diagnostics.put("provider",result.aiProviderDiagnostics());diagnostics.put("failureReason",result.failureReason());
        var snapshot=ScoreExtractionSnapshot.from(task.getTaskId(),result.facts(),result.qualifications(),result.sourceDiagnostics());
        writer.freeze(task.getProjectId(),task.getId(),new ScoreExtractionSnapshot(1,snapshot.items(),snapshot.schemes(),snapshot.sourceDiagnostics(),diagnostics));
        task.setDecisionStatus(result.structureIncomplete()?"STRUCTURE_INCOMPLETE":result.completeness()==null?"REVIEW_REQUIRED":result.completeness().decision().name());
        if(snapshot.schemes().size()==1) task.setOriginalTotalWeight(snapshot.schemes().get(0).extractedKnownTotal());
        task.setPipelineVersion("EXTRACTION_VERSION_V1");
        String failure=result.structureIncomplete() || snapshot.items().isEmpty()?
                (result.failureReason()==null?"NO_EXTRACTED_CANDIDATES":result.failureReason()):null;
        if(finalizer.finish(task,failure)) activation.tryAutoActivate(task.getProjectId(),task.getTaskId());
        progress.clearProgress(task.getTaskId());
    }
    public ScoreExtractionSnapshot legacyDraft(ScoreParseTask task,List<ScoreCandidate> raw,int skippedChunks) {
        List<ScoreFact> all=new ArrayList<>();
        for(int n=0;n<raw.size();n++) {
            var c=raw.get(n);
            all.add(new ScoreFact("legacy-"+task.getId()+"-"+n,"",List.of(),c.sourceText(),c.code(),c.dim(),c.weight(),null,null,null,
                    "LEGACY_RECALL",.5,ScoreFact.ValidationStatus.REVIEW_REQUIRED,ChildAggregation.SUM,
                    new ScoreSourceMetadata(c.detail(),c.sourceText(),List.of(),List.of("LEGACY_SOURCE_UNVERIFIED"),c.dim(),c.scoreTypeGuess())));
        }
        var snapshot=ScoreExtractionSnapshot.from(task.getTaskId(),all,List.of(),skippedChunks>0?List.of("RECALL_CHUNKS_SKIPPED"):List.of());
        writer.stage(task.getProjectId(),task.getId(),snapshot);return snapshot;
    }
    public void legacyFinish(ScoreParseTask task,ScoreExtractionSnapshot draft,String failure) {
        var snapshot=new ScoreExtractionSnapshot(1,draft.items(),draft.schemes(),draft.sourceDiagnostics(),Map.of("legacyRunSummary",task.getRunSummary()==null?"":task.getRunSummary()));
        writer.freeze(task.getProjectId(),task.getId(),snapshot);
        if(finalizer.finish(task,failure)) activation.tryAutoActivate(task.getProjectId(),task.getTaskId());
        progress.clearProgress(task.getTaskId());
    }
    public void failure(String taskId){writer.freezeFailure(taskId,"PARSE_EXECUTION_FAILED");}
}
