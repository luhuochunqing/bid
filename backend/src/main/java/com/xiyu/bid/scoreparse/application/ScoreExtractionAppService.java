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
import com.xiyu.bid.scoreparse.entity.ScoreResult;
import com.xiyu.bid.scoreparse.entity.ScoreActiveVersion;
import com.xiyu.bid.scoreparse.entity.ScoreExtractionRecord;
import com.xiyu.bid.scoreparse.entity.ScoreParseTask;
import com.xiyu.bid.scoreparse.entity.ScoreParseEvidence;
import com.xiyu.bid.scoreparse.entity.ScoreParseReview;
import com.xiyu.bid.scoreparse.entity.ScoreParseFact;
import com.xiyu.bid.scoreparse.entity.ScoreItem;
import com.xiyu.bid.scoreparse.repository.ScoreParseTaskRepository;
import com.xiyu.bid.scoreparse.repository.ScoreActiveVersionRepository;
import com.xiyu.bid.scoreparse.repository.ScoreParseEvidenceRepository;
import com.xiyu.bid.scoreparse.repository.ScoreItemRepository;
import com.xiyu.bid.scoreparse.repository.ScoreParseFactRepository;
import com.xiyu.bid.scoreparse.repository.ScoreExtractionRecordRepository;
import com.xiyu.bid.scoreparse.repository.ScoreParseReviewRepository;
import com.xiyu.bid.scoreparse.repository.ScoreResultRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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

@Service @RequiredArgsConstructor
public class ScoreExtractionAppService {
    private final ScoreParseTaskRepository tasks;
    private final ScoreParseFactRepository facts;
    private final ScoreExtractionRecordRepository snapshots;
    private final ScoreActiveVersionRepository versions;
    private final ScoreItemRepository items;
    private final ScoreVersionActivationService activation;
    private final ScoreExtractionCodec codec;
    private final ScoreExtractionVersionFeature feature;
    private final ProjectAccessScopeService projectAccessScopeService;
    public record Active(Long versionId,Long parseTaskId,String taskId,String fingerprint,LocalDateTime activatedAt,boolean hasManualEdits) {}
    public record Adoption(String status,List<String> adoptedItemIds,List<String> remainingItemIds) {}
    public record SchemeView(String schemeId,String title,int extractedCount,BigDecimal extractedKnownTotal,
            boolean extractedTotalComplete,int computableCount,BigDecimal computableTotal,int activeCount,
            BigDecimal activeTotal,boolean activeTotalComplete,BigDecimal declaredTotal,String declaredTotalStatus) {}
    public record View(Long projectId,String taskId,Long parseTaskId,String taskStatus,String validationStatus,
            LocalDateTime extractedAt,int payloadVersion,String snapshotStatus,List<SchemeView> schemes,
            List<ExtractedScoreItem> items,List<String> sourceDiagnostics,Map<String,Object> runDiagnostics,Adoption adoption,Active active) {}

    @Transactional(readOnly=true)
    public View getLatest(Long projectId) {
        access(projectId);
        var task=tasks.findByProjectIdAndTaskTypeAndStatusIn(projectId,"PARSE",List.of("PENDING","PROCESSING","COMPLETED","FAILED"))
                .stream().max(Comparator.comparing(ScoreParseTask::getId)).orElseThrow(() -> new ScoreExtractionException(404,"EXTRACTION_TASK_NOT_FOUND"));
        return view(projectId,task);
    }
    @Transactional(readOnly=true)
    public View getByTaskId(Long projectId,String taskId) {
        access(projectId);
        var task=tasks.findByTaskId(taskId).filter(t->projectId.equals(t.getProjectId()) && "PARSE".equals(t.getTaskType()))
                .orElseThrow(() -> new ScoreExtractionException(404,"EXTRACTION_TASK_NOT_FOUND"));
        return view(projectId,task);
    }
    private void access(Long projectId) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        if(!feature.enabled() && !versions.existsByProjectId(projectId))throw new ScoreExtractionException(409,"EXTRACTION_FEATURE_DISABLED");
    }
    private View view(Long projectId,ScoreParseTask task) {
        var record=snapshots.findById(task.getId()).orElse(null);
        var snapshot=record!=null && record.getPayload()!=null ? codec.decode(record.getPayload()) : historical(task);
        var version=versions.findFirstByProjectIdOrderByIdDesc(projectId).orElse(null);
        var token=activation.currentToken(projectId);
        var projection=items.findByProjectIdOrderByItemIndexAsc(projectId);
        var activeTask=version==null?null:tasks.findById(version.getParseTaskId()).orElse(null);
        var active=new Active(token.versionId(),version==null?null:version.getParseTaskId(),activeTask==null?null:activeTask.getTaskId(),token.fingerprint(),version==null?null:version.getCreatedAt(),token.hasManualEdits());
        Set<String> adoptedFacts=version!=null && version.getParseTaskId().equals(task.getId()) ? projection.stream().map(ScoreItem::getSourceFactId).collect(java.util.stream.Collectors.toSet()):Set.of();
        var adopted=snapshot.items().stream().filter(i->adoptedFacts.contains(i.factId())).map(ExtractedScoreItem::itemId).toList();
        String selectedScope=version!=null && version.getParseTaskId().equals(task.getId())?version.getSchemeId():null;
        var remaining=snapshot.items().stream().filter(i -> !i.schemeId().isBlank() && (selectedScope==null || selectedScope.equals(i.schemeId())) && !i.diagnostics().contains("NOT_EFFECTIVE_SCORING_UNIT")).filter(i->!adopted.contains(i.itemId())).map(ExtractedScoreItem::itemId).toList();
        var schemeViews=snapshot.schemes().stream().map(s->{
            boolean activeHere=version!=null && version.getParseTaskId().equals(task.getId()) && version.getSchemeId().equals(s.schemeId());
            return new SchemeView(s.schemeId(),s.title(),s.extractedCount(),s.extractedKnownTotal(),s.extractedTotalComplete(),s.computableCount(),s.computableTotal(),
                    activeHere?projection.size():0,activeHere?projection.stream().map(ScoreItem::getWeight).reduce(BigDecimal.ZERO,BigDecimal::add):BigDecimal.ZERO,true,s.declaredTotal(),s.declaredTotalStatus());
        }).toList();
        return new View(projectId,task.getTaskId(),task.getId(),task.getStatus(),task.getDecisionStatus(),record==null?null:record.getFrozenAt(),snapshot.payloadVersion(),
                record==null?"LEGACY_UNKNOWN":record.getFrozenAt()==null?"NOT_FROZEN":"FROZEN",schemeViews,snapshot.items(),snapshot.sourceDiagnostics(),snapshot.runDiagnostics(),
                new Adoption(adopted.isEmpty()?"NOT_ADOPTED":remaining.isEmpty()?"FULL":"PARTIAL",adopted,remaining),active);
    }
    private ScoreExtractionSnapshot historical(ScoreParseTask task) {
        var source=facts.findByParseTaskId(task.getId()).stream().map(f -> new ScoreFact(f.getFactId(),f.getSourceTableId(),List.of(),f.getSourceText(),f.getCode(),f.getDimension(),f.getWeight(),null,null,f.getParentFactId(),f.getExtractionMethod(),
                f.getConfidence()==null?0:f.getConfidence().doubleValue(),ScoreFact.ValidationStatus.REVIEW_REQUIRED,ChildAggregation.legacyDefault(f.getChildAggregation()),
                new ScoreSourceMetadata("","",List.of(),List.of("LEGACY_PAYLOAD_MISSING")))).toList();
        var mapped=ScoreExtractionSnapshot.from(task.getTaskId(),source,List.of(),List.of("LEGACY_PAYLOAD_MISSING"));
        return new ScoreExtractionSnapshot(0,mapped.items(),mapped.schemes(),mapped.sourceDiagnostics());
    }
}
