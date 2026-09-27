package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.ScoreExtractionSnapshot;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

/** Internal parser boundary, called only after project authorization at trigger. */
@Service @RequiredArgsConstructor
public class ScoreExtractionSnapshotWriter {
    private final ScoreExtractionRecordRepository snapshots;
    private final ScoreParseFactRepository facts;
    private final ScoreParseEvidenceRepository evidence;
    private final ScoreParseTaskRepository tasks;
    private final ScoreExtractionCodec codec;
    private final ScoreVersionActivationService activation;
    private final ScoreExtractionVersionFeature feature;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper;

    @Transactional
    public void begin(ScoreParseTask task) {
        if(!feature.enabled() || !"PARSE".equals(task.getTaskType())) return;
        tasks.lockProjectForTaskChange(task.getProjectId());
        if(!snapshots.existsById(task.getId())) snapshots.save(ScoreExtractionRecord.builder()
                .parseTaskId(task.getId()).projectId(task.getProjectId())
                .baseFingerprint(activation.currentToken(task.getProjectId()).fingerprint()).build());
    }
    public boolean usesVersioning(Long taskId) { return snapshots.existsById(taskId); }

    @Transactional
    public void stage(Long projectId,Long taskId,ScoreExtractionSnapshot snapshot) {
        tasks.lockProjectForTaskChange(projectId);
        var record=snapshots.findById(taskId).orElseThrow();
        if(!projectId.equals(record.getProjectId()) || record.getFrozenAt()!=null) throw new ScoreExtractionException(409,"EXTRACTION_ALREADY_FROZEN");
        record.setPayload(codec.encode(snapshot));snapshots.save(record);
    }
    @Transactional
    public void freeze(Long projectId,Long taskId,ScoreExtractionSnapshot snapshot) {
        if(tasks.lockProjectForTaskChange(projectId)==null) throw new ScoreExtractionException(404,"PROJECT_NOT_FOUND");
        var task=tasks.findById(taskId).filter(t -> projectId.equals(t.getProjectId()) && "PARSE".equals(t.getTaskType()))
                .orElseThrow(() -> new ScoreExtractionException(404,"EXTRACTION_TASK_NOT_FOUND"));
        var record=snapshots.findById(taskId).orElseThrow(() -> new ScoreExtractionException(409,"EXTRACTION_MODE_NOT_CAPTURED"));
        String payload=codec.encode(snapshot);
        if(record.getFrozenAt()!=null) {
            if(!codec.canonicalStoredJson(record.getPayload()).equals(codec.canonicalStoredJson(payload))) throw new ScoreExtractionException(409,"EXTRACTION_ALREADY_FROZEN");
            return;
        }
        new ScoreParseFactEvidenceWriter(facts,evidence,mapper).persist(projectId,taskId,
                snapshot.items().stream().map(i->i.fact()).toList(),
                snapshot.items().stream().map(i->i.qualification()).filter(java.util.Objects::nonNull).toList(),true);
        var stored=facts.findByParseTaskId(taskId).stream().sorted(java.util.Comparator.comparing(ScoreParseFact::getId)).toList();
        for(int n=0;n<stored.size();n++) {
            var row=stored.get(n);
            var item=snapshot.items().get(n);
            row.setWeight(ScoreParseFactEvidenceWriter.storableWeight(item.rawMaximum()));row.setPayloadVersion(1);row.setItemPayload(codec.encode(item));
        }
        facts.saveAll(stored);
        record.setPayload(payload);record.setFrozenAt(LocalDateTime.now());snapshots.save(record);
    }
    @Transactional
    public void freezeFailure(String taskId,String diagnostic) {
        var task=tasks.findByTaskId(taskId).orElse(null);
        if(task==null) return;
        var record=snapshots.findById(task.getId()).orElse(null);
        if(record==null || record.getFrozenAt()!=null) return;
        var draft=record.getPayload()==null?ScoreExtractionSnapshot.from(taskId,java.util.List.of(),java.util.List.of(),java.util.List.of()):codec.decode(record.getPayload());
        var diagnostics=new java.util.ArrayList<>(draft.sourceDiagnostics());diagnostics.add(diagnostic);
        freeze(task.getProjectId(),task.getId(),new ScoreExtractionSnapshot(draft.payloadVersion(),draft.items(),draft.schemes(),diagnostics,draft.runDiagnostics()));
    }
}
