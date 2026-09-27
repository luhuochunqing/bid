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
import com.xiyu.bid.scoreparse.dto.ActivateScoreExtractionCommand;
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

/** Project row lock is shared with parse creation and the scoring version boundary. */
@Service @RequiredArgsConstructor
public class ScoreVersionActivationService {
    private final ScoreParseTaskRepository tasks;
    private final ScoreExtractionRecordRepository snapshots;
    private final ScoreActiveVersionRepository versions;
    private final ScoreItemRepository items;
    private final ScoreResultRepository results;
    private final ScoreExtractionCodec codec;
    private final ScoreExtractionVersionFeature feature;
    private final ProjectAccessScopeService projectAccessScopeService;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private EstimatedScoreService estimator;
    public record VersionToken(Long versionId,String fingerprint,boolean hasManualEdits) {}

    /** Caller must hold the project lock if the token guards a write. Estimated fields are excluded. */
    public VersionToken currentToken(Long projectId) {
        var projection=items.findByProjectIdOrderByItemIndexAsc(projectId);
        var active=versions.findFirstByProjectIdOrderByIdDesc(projectId).orElse(null);
        String digest=ScoreActiveProjection.fingerprint(projection,codec);
        return new VersionToken(active==null?null:active.getId(),active==null?
                (projection.isEmpty()?"empty:v1":"legacy:"+digest):"v1:"+active.getId()+":"+digest,
                active!=null && (active.isHasManualEdits() || !digest.equals(active.getProjectionFingerprint())));
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public VersionToken activate(Long projectId,String taskId,ActivateScoreExtractionCommand command,String operator) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        if(!feature.enabled()) throw new ScoreExtractionException(409,"EXTRACTION_FEATURE_DISABLED");
        lock(projectId);
        var task=task(projectId,taskId);
        return activateLocked(projectId,task,command,operator,"MANUAL");
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public boolean tryAutoActivate(Long projectId,String taskId) {
        lock(projectId);
        var task=task(projectId,taskId);
        var record=snapshots.findById(task.getId()).orElse(null);
        if(record==null || record.getFrozenAt()==null || !"COMPLETED".equals(task.getStatus()) || Boolean.TRUE.equals(task.getTimeoutMarked())) return false;
        var latest=tasks.findLatestParseTaskForUpdate(projectId).orElse(null);
        if(latest==null || !latest.getId().equals(task.getId())) return false;
        var token=currentToken(projectId);
        if(!token.fingerprint().equals(record.getBaseFingerprint()) || token.hasManualEdits()) return false;
        var snapshot=codec.decode(record.getPayload());
        if(snapshot.schemes().size()!=1) return false;
        var scheme=snapshot.schemes().get(0);
        if(scheme.computableCount()==0) return false;
        var current=items.findByProjectIdOrderByItemIndexAsc(projectId);
        if(!current.isEmpty() && (token.versionId()==null || !coversCurrentRules(snapshot,current) || !snapshot.sourceDiagnostics().isEmpty() || !scheme.extractedTotalComplete()
                || scheme.declaredTotal()==null || !"KNOWN".equals(scheme.declaredTotalStatus())
                || scheme.declaredTotal().compareTo(scheme.computableTotal())!=0 || scheme.computableCount()<current.size()
                || scheme.computableTotal().compareTo(current.stream().map(ScoreItem::getWeight).reduce(java.math.BigDecimal.ZERO,java.math.BigDecimal::add))<0
                || snapshot.items().stream().anyMatch(i -> !i.computable() && !i.diagnostics().contains("NOT_EFFECTIVE_SCORING_UNIT") && !i.diagnostics().contains("OUT_OF_SCHEME")))) return false;
        activateLocked(projectId,task,new ActivateScoreExtractionCommand(scheme.schemeId(),token.fingerprint(),"自动采用可计算候选",null),"SYSTEM","AUTO");
        return true;
    }
    private VersionToken activateLocked(Long projectId,ScoreParseTask task,ActivateScoreExtractionCommand command,String operator,String source) {
        if(command==null || command.schemeId()==null || command.expectedActiveFingerprint()==null || command.expectedActiveFingerprint().isBlank()
                || command.reason()==null || command.reason().isBlank()) throw new ScoreExtractionException(400,"ACTIVATION_INPUT_REQUIRED");
        var token=currentToken(projectId);
        if(!command.expectedActiveFingerprint().equals(token.fingerprint())) throw new ScoreExtractionException(409,"ACTIVE_FINGERPRINT_CONFLICT");
        var record=snapshots.findById(task.getId()).orElseThrow(() -> new ScoreExtractionException(409,"EXTRACTION_NOT_FROZEN"));
        if(record.getFrozenAt()==null || !"COMPLETED".equals(task.getStatus()) || Boolean.TRUE.equals(task.getTimeoutMarked())) throw new ScoreExtractionException(409,"EXTRACTION_NOT_COMPLETED");
        var snapshot=codec.decode(record.getPayload());
        if(snapshot.schemes().stream().noneMatch(s -> s.schemeId().equals(command.schemeId()))) throw new ScoreExtractionException(400,"SCHEME_NOT_FOUND");
        var selection=snapshot.items().stream().filter(i -> i.schemeId().equals(command.schemeId()) && i.computable())
                .filter(i -> command.selectedItemIds()==null || command.selectedItemIds().contains(i.itemId())).toList();
        if(selection.isEmpty() || command.selectedItemIds()!=null && (new HashSet<>(command.selectedItemIds()).size()!=command.selectedItemIds().size()
                || selection.size()!=command.selectedItemIds().size())) throw new ScoreExtractionException(400,"INVALID_OR_NONCOMPUTABLE_SELECTION");
        var old=items.findByProjectIdOrderByItemIndexAsc(projectId);
        var version=versions.saveAndFlush(ScoreActiveVersion.builder().projectId(projectId).parseTaskId(task.getId()).schemeId(command.schemeId())
                .previousVersionId(token.versionId()).projectionFingerprint("pending").selectionPayload(codec.encode(selection.stream().map(ExtractedScoreItem::itemId).toList()))
                .activationSource(source).hasManualEdits("MANUAL".equals(source)).reason(command.reason()).operatorName(operator).createdAt(LocalDateTime.now()).build());
        if(!old.isEmpty()) results.deleteByScoreItemIdIn(old.stream().map(ScoreItem::getId).toList());
        items.deleteByProjectId(projectId);items.flush();
        List<ScoreItem> next=new ArrayList<>();
        for(int n=0;n<selection.size();n++) next.add(ScoreActiveProjection.item(projectId,task.getId(),version.getId(),n+1,selection.get(n),codec));
        items.saveAllAndFlush(next);
        version.setProjectionFingerprint(ScoreActiveProjection.fingerprint(next,codec));versions.saveAndFlush(version);
        String scoringFingerprint=ScoreVersionFingerprint.hash("scoring-version:v1:"+version.getProjectionFingerprint());
        if(estimator!=null) ScoreAfterCommit.dispatch(()->estimator.estimateForVersion(projectId,scoringFingerprint));
        return currentToken(projectId);
    }
    private boolean coversCurrentRules(ScoreExtractionSnapshot next,List<ScoreItem> current) {
        var keys=next.items().stream().filter(ExtractedScoreItem::computable)
                .map(i -> i.fact().code()+"\u0000"+i.name()).collect(java.util.stream.Collectors.toSet());
        return current.stream().allMatch(i -> keys.contains(i.getCode()+"\u0000"+i.getDim()));
    }
    private void lock(Long projectId) { if(tasks.lockProjectForTaskChange(projectId)==null)throw new ScoreExtractionException(404,"PROJECT_NOT_FOUND"); }
    private ScoreParseTask task(Long projectId,String taskId) {
        return tasks.findByTaskId(taskId).filter(t -> projectId.equals(t.getProjectId()) && "PARSE".equals(t.getTaskType()))
                .orElseThrow(() -> new ScoreExtractionException(404,"EXTRACTION_TASK_NOT_FOUND"));
    }
}
