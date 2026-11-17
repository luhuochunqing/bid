package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.BidScoreSkipPolicy;
import com.xiyu.bid.scoreparse.domain.ScoreResultReusePolicy;
import com.xiyu.bid.scoreparse.domain.ScoreVersionFingerprint;
import com.xiyu.bid.scoreparse.domain.ScoringExtractionReport;
import com.xiyu.bid.scoreparse.dto.ScoreScoringResultsDTO;
import com.xiyu.bid.scoreparse.entity.ScoreItem;
import com.xiyu.bid.scoreparse.entity.ScoreParseTask;
import com.xiyu.bid.scoreparse.entity.ScoreResult;
import com.xiyu.bid.scoreparse.repository.ScoreParseTaskRepository;
import com.xiyu.bid.scoreparse.repository.ScoreResultRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import com.fasterxml.jackson.core.JsonProcessingException;

/** Short project-lock transactions. No network or model invocation belongs inside this boundary. */
@Service @RequiredArgsConstructor
public class ScoreScoringVersionService {
    private final ScoreParseTaskRepository tasks;
    private final ScoreResultRepository results;
    private final ScoreExtractionCodec codec;
    @PersistenceContext private EntityManager em;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private ScoreEstimateFreshness estimateFreshness;
    public record Snapshot(List<ScoreItem> items,String fingerprint,Map<Long,ScoreResult> results,Map<Long,ScoreResult> visibleResults,Map<Long,String> previousStatuses,Map<Long,ScoreResultReusePolicy.Source> sources,Map<Long,ScoringExtractionReport> coverages) {}

    /** Joins caller transaction so dispatch can be registered on its actual commit. */
    @Transactional
    public <T> T prepare(Long projectId,Supplier<T> operation) {
        lock(projectId);
        return operation.get();
    }
    /** Current reads + refresh defeat both an older RR read view and JPA's identity map. */
    @SuppressWarnings("unchecked")
    Snapshot lockedSnapshot(Long projectId) {
        List<ScoreItem> current=em.createNativeQuery("SELECT * FROM score_item WHERE project_id = :p ORDER BY item_index FOR UPDATE",ScoreItem.class)
                .setParameter("p",projectId).getResultList();
        current.forEach(i->em.refresh(i,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE));
        String hash=ScoreVersionFingerprint.hash("scoring-version:v1:"+ScoreActiveProjection.fingerprint(current,codec));
        boolean legacyProjection=current.stream().allMatch(i->i.getActiveVersionId()==null);
        String legacyHash=BidScoreSkipPolicy.hashItems(current.stream().map(i->BidScoreSkipPolicy.itemFingerprint(i.getId(),i.getWeight(),i.getDetail())).toList());
        Map<Long,String> previousStatuses=new HashMap<>();
        Map<Long,ScoreResultReusePolicy.Source> sources=new HashMap<>();
        Map<Long,ScoringExtractionReport> coverages=new HashMap<>();
        var reports=new ScoringRunReportCodec();
        String currentFileHash=null;
        var recent=lockedTasks(projectId,List.of("PENDING","PROCESSING","COMPLETED","FAILED")).stream()
                .sorted(Comparator.comparing(ScoreParseTask::getId).reversed()).toList();
        for(var recentTask:recent) {
            String verified=reports.scoring(recentTask.getRunSummary()).path("input").path("fileHash").asText(null);
            if(verified!=null){currentFileHash=verified;break;}
        }
        Map<Long,ScoreParseTask> owners=new HashMap<>();
        Map<Long,ScoreResult> valid=new HashMap<>();
        Map<Long,ScoreResult> visible=new HashMap<>();
        if(!current.isEmpty()) {
            List<ScoreResult> rows=em.createNativeQuery("SELECT r.* FROM score_result r JOIN score_item i ON i.id=r.score_item_id WHERE i.project_id=:p FOR UPDATE",ScoreResult.class)
                    .setParameter("p",projectId).getResultList();
            for(var r:rows) {
                em.refresh(r,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
                var owner=owners.computeIfAbsent(r.getScoringTaskId(),this::currentTask);
                var source=owner==null?null:reports.source(owner,r.getScoreItemId());
                if(source!=null){
                    sources.put(r.getScoreItemId(),source);
                    try {coverages.put(r.getScoreItemId(),new com.fasterxml.jackson.databind.ObjectMapper().treeToValue(
                            reports.scoring(owner.getRunSummary()).path("input").path("extraction"),ScoringExtractionReport.class));}
                    catch(JsonProcessingException ignored){ /* Unknown old coverage is not auto-skippable. */ }
                }
                // Selection can use the old status of this current item, never its invalid score.
                if(owner!=null && "SCORING".equals(owner.getTaskType()) && projectId.equals(owner.getProjectId())) {
                    previousStatuses.put(r.getScoreItemId(),Objects.toString(r.getStatusStage2(),""));
                }
                if(owner!=null && "SCORING".equals(owner.getTaskType()) && projectId.equals(owner.getProjectId()) && hash.equals(owner.getItemSetHash())
                        && List.of("COMPLETED","FAILED").contains(owner.getStatus())) {
                    boolean expired=currentFileHash!=null && (source==null || !currentFileHash.equals(source.identity().fileHash()));
                    visible.put(r.getScoreItemId(),expired?expiredDisplay(r):r);
                    if(!expired && source!=null && new ScoreResultReusePolicy().preserveEligible(
                            new ScoreResultReusePolicy.Identity(source.identity().fileHash(),hash,ScoringRunReportCodec.ENGINE,source.identity().modelPolicyFingerprint()),source)
                            && validValue(r,current))valid.put(r.getScoreItemId(),r);
                } else if(legacyProjection && owner!=null && "SCORING".equals(owner.getTaskType()) && projectId.equals(owner.getProjectId())
                        && "COMPLETED".equals(owner.getStatus()) && !"SCORING_VERSION_V1".equals(owner.getPipelineVersion())
                        && (owner.getItemSetHash()==null || legacyHash.equals(owner.getItemSetHash()))) {
                    visible.put(r.getScoreItemId(),legacyDisplay(r));
                }
            }
        }
        for(var item:current) visible.putIfAbsent(item.getId(),ScoreResult.builder().scoreItemId(item.getId())
                .statusStage2("PENDING").missedReason("当前规则尚未评分，请重新评分").build());
        return new Snapshot(List.copyOf(current),hash,Map.copyOf(valid),Map.copyOf(visible),Map.copyOf(previousStatuses),Map.copyOf(sources),Map.copyOf(coverages));
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.READ_COMMITTED)
    public Snapshot capture(Long projectId) {lock(projectId);return lockedSnapshot(projectId);}

    @Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.READ_COMMITTED)
    public Snapshot start(String taskId) {
        var task=tasks.findByTaskId(taskId).orElseThrow();lock(task.getProjectId());em.refresh(task);
        var snapshot=lockedSnapshot(task.getProjectId());
        if(expired(task)){timeout(task);return null;}
        if(!writable(task,snapshot)) {reject(task);return null;}
        task.setStatus("PROCESSING");task.setStartedAt(LocalDateTime.now());
        return snapshot;
    }
    /** Commit verified input before slow extraction; a timeout may record identity, never results. */
    @Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.READ_COMMITTED)
    public boolean recordInput(ScoreParseTask detached,Long documentId) {
        lock(detached.getProjectId());var task=currentTask(detached.getId());
        var snapshot=lockedSnapshot(detached.getProjectId());
        if(task==null || !Objects.equals(task.getProjectId(),detached.getProjectId())
                || !Objects.equals(task.getItemSetHash(),detached.getItemSetHash()) || !currentVersion(task,snapshot)) {
            if(task!=null)reject(task);return false;
        }
        boolean active=List.of("PENDING","PROCESSING").contains(task.getStatus());
        if(!active && !("FAILED".equals(task.getStatus()) && Boolean.TRUE.equals(task.getTimeoutMarked())))return false;
        task.setBidContentHash(detached.getBidContentHash());
        var report=new ScoringRunReportCodec().bindInput(task,documentId);
        task.setRunSummary(report.json());
        if(!active)return false;
        if(expired(task)){timeout(task);return false;}
        if(report.tooLarge()){reject(task);task.setErrorMessage("REPORT_TOO_LARGE");return false;}
        return !Boolean.TRUE.equals(task.getTimeoutMarked());
    }
    /** Results bind durable input through scoring_task_id, including reused rows. */
    @Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.READ_COMMITTED)
    public boolean finish(ScoreParseTask detached,List<ScoreResult> replacement,String failure) {
        lock(detached.getProjectId());var task=currentTask(detached.getId());
        var snapshot=lockedSnapshot(detached.getProjectId());
        if(task==null || !Objects.equals(task.getItemSetHash(),detached.getItemSetHash()) || !writable(task,snapshot)) {if(task!=null)reject(task);return false;}
        var bounded=new ScoringRunReportCodec().encodeReport(detached.getRunSummary(),detached.getTaskId());
        if(bounded.tooLarge()){replacement=null;failure="REPORT_TOO_LARGE";}
        if(expired(task)){timeout(task);return false;}
        if(replacement!=null) {
            Set<Long> ids=new HashSet<>(snapshot.items().stream().map(ScoreItem::getId).toList());
            if(replacement.size()!=ids.size() || !ids.equals(new HashSet<>(replacement.stream().map(ScoreResult::getScoreItemId).toList()))
                    || replacement.stream().anyMatch(r->!task.getId().equals(r.getScoringTaskId()))) throw new IllegalArgumentException("SCORING_RESULT_IDENTITY_MISMATCH");
            results.deleteByScoreItemIdIn(new ArrayList<>(ids));results.flush();results.saveAll(replacement);
        }
        task.setRunSummary(bounded.json());
        task.setBidContentHash(detached.getBidContentHash());task.setChapterHashes(detached.getChapterHashes());task.setStage(detached.getStage());
        task.setStatus(failure==null?"COMPLETED":"FAILED");task.setProgress(failure==null?100:task.getProgress());task.setErrorMessage(failure);task.setCompletedAt(LocalDateTime.now());
        return true;
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.READ_COMMITTED)
    public ScoreScoringResultsDTO read(Long projectId) {
        lock(projectId);var s=lockedSnapshot(projectId);return new ScoreScoringResultsAssembler().assemble(s.items(),s.visibleResults(),
                lockedTasks(projectId,List.of("PENDING","PROCESSING","COMPLETED","FAILED")).stream()
                        .max(Comparator.comparing(ScoreParseTask::getId)).orElse(null),s.sources());
    }
    /** Update only estimate columns, never merge entities captured before activation. */
    @Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.READ_COMMITTED)
    public boolean saveEstimates(Long projectId,String expected,List<ScoreItem> calculated) {
        lock(projectId);var current=lockedSnapshot(projectId);
        if(!current.fingerprint().equals(expected))return false;
        if(estimateFreshness!=null && calculated.stream().anyMatch(i ->
                (CertificateScoreEstimateSupport.applies(i) || EstimateEvidenceSupport.applies(i)) && !clearedFailure(i)
                        && !"FRESH".equals(estimateFreshness.status(i,java.time.LocalDate.now())))) return false;
        Map<Long,ScoreItem> byId=new HashMap<>();current.items().forEach(i->byId.put(i.getId(),i));
        for(var value:calculated) {
            var row=byId.get(value.getId());if(row==null)throw new IllegalArgumentException("ESTIMATE_ITEM_IDENTITY_MISMATCH");
            row.setEstScore(value.getEstScore());row.setEstBasis(value.getEstBasis());row.setStatusStage1(value.getStatusStage1());row.setKbHit(value.getKbHit());
            row.setEstimateInputFingerprint(value.getEstimateInputFingerprint());
            row.setEstimatedAt(value.getEstimatedAt());
        }
        return true;
    }
    /** A failed item may clear its own stale score without rejecting unrelated fresh rows. */
    private static boolean clearedFailure(ScoreItem i) {
        return i.getEstimateInputFingerprint()==null && i.getEstimatedAt()==null
                && i.getEstScore()==null && "PENDING".equals(i.getStatusStage1())
                && !Boolean.TRUE.equals(i.getKbHit());
    }
    @SuppressWarnings("unchecked")
    List<ScoreParseTask> lockedTasks(Long projectId,List<String> statuses) {
        List<ScoreParseTask> rows=em.createNativeQuery("SELECT * FROM score_parse_task WHERE project_id=:p AND task_type='SCORING' FOR UPDATE",ScoreParseTask.class).setParameter("p",projectId).getResultList();
        rows.forEach(t->em.refresh(t,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE));
        return rows.stream().filter(t->statuses.contains(t.getStatus())).toList();
    }
    private ScoreResult legacyDisplay(ScoreResult row) {
        return ScoreResult.builder().scoreItemId(row.getScoreItemId()).scoringTaskId(row.getScoringTaskId())
                .actualScore(row.getActualScore()).statusStage2("PENDING").evidence(row.getEvidence()).quote(row.getQuote())
                .missedReason("历史评分尚未绑定完整规则版本，请重新评分；"+Objects.toString(row.getMissedReason(),""))
                .suggestion(row.getSuggestion()).matchRatio(row.getMatchRatio()).reuseKind("LEGACY_UNVERIFIED").build();
    }
    private static boolean validValue(ScoreResult row,List<ScoreItem> items) {
        var item=items.stream().filter(i->i.getId().equals(row.getScoreItemId())).findFirst().orElseThrow();
        if("SUBJECTIVE".equals(item.getScoreType()))return row.getSuggestion()!=null&&!row.getSuggestion().isBlank();
        return row.getActualScore()!=null&&item.getWeight()!=null&&row.getActualScore().signum()>=0
                &&row.getActualScore().compareTo(item.getWeight())<=0&&row.getQuote()!=null&&!row.getQuote().isBlank();
    }
    private ScoreResult expiredDisplay(ScoreResult row) {
        return ScoreResult.builder().scoreItemId(row.getScoreItemId()).scoringTaskId(row.getScoringTaskId())
                .statusStage2("PENDING").reuseKind("SOURCE_EXPIRED")
                .missedReason("投标原件已变化，请选择全部重新评分").build();
    }
    private static boolean expired(ScoreParseTask task) {
        return task.getCreatedAt()!=null && (!task.getCreatedAt().plusSeconds(1800).isAfter(LocalDateTime.now())
                || "PENDING".equals(task.getStatus())&&!task.getCreatedAt().plusMinutes(10).isAfter(LocalDateTime.now()));
    }
    private static void timeout(ScoreParseTask task) {
        task.setStatus("FAILED");task.setTimeoutMarked(true);task.setErrorMessage("SCORING_DEADLINE_EXCEEDED");task.setCompletedAt(LocalDateTime.now());
    }
    private boolean writable(ScoreParseTask task,Snapshot snapshot) {
        return !Boolean.TRUE.equals(task.getTimeoutMarked()) && List.of("PENDING","PROCESSING").contains(task.getStatus())
                && currentVersion(task,snapshot);
    }
    private boolean currentVersion(ScoreParseTask task,Snapshot snapshot) {
        if(!"SCORING".equals(task.getTaskType()) || !snapshot.fingerprint().equals(task.getItemSetHash()))return false;
        var latest=em.createNativeQuery("SELECT id FROM score_parse_task WHERE project_id=:p AND task_type='SCORING' ORDER BY id DESC LIMIT 1 FOR UPDATE")
                .setParameter("p",task.getProjectId()).getSingleResult();
        return task.getId().equals(((Number)latest).longValue());
    }
    private ScoreParseTask currentTask(Long id) {
        if(id==null)return null;
        var t=em.find(ScoreParseTask.class,id,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if(t!=null)em.refresh(t,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);return t;
    }
    private void reject(ScoreParseTask task) {
        if(List.of("PENDING","PROCESSING").contains(task.getStatus())) {task.setStatus("FAILED");task.setErrorMessage("STALE_SCORE_VERSION_OR_TASK");task.setCompletedAt(LocalDateTime.now());}
    }
    private void lock(Long projectId) {if(tasks.lockProjectForTaskChange(projectId)==null)throw new IllegalArgumentException("PROJECT_NOT_FOUND");}
}
