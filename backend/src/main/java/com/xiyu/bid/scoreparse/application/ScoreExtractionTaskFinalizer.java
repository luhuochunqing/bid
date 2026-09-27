package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.entity.ScoreParseTask;
import com.xiyu.bid.scoreparse.repository.ScoreParseTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

/** Completes only a still-running task. Frozen extraction commits independently of publication. */
@Component @RequiredArgsConstructor
public class ScoreExtractionTaskFinalizer {
    private final ScoreParseTaskRepository tasks;
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public boolean source(ScoreParseTask draft,TenderTextSource source) {
        tasks.lockProjectForTaskChange(draft.getProjectId());
        var current=tasks.findById(draft.getId()).orElseThrow();
        if(Boolean.TRUE.equals(current.getTimeoutMarked()) || "FAILED".equals(current.getStatus()) || "COMPLETED".equals(current.getStatus())) return false;
        current.setFileName(source.fileName());current.setFileUrl(source.fileUrl());tasks.save(current);return true;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public boolean finish(ScoreParseTask draft,String failure) {
        tasks.lockProjectForTaskChange(draft.getProjectId());
        var current=tasks.findById(draft.getId()).orElseThrow();
        if(Boolean.TRUE.equals(current.getTimeoutMarked()) || "FAILED".equals(current.getStatus()) || "COMPLETED".equals(current.getStatus())) return false;
        current.setDecisionStatus(draft.getDecisionStatus());current.setPipelineVersion(draft.getPipelineVersion());
        current.setOriginalTotalWeight(draft.getOriginalTotalWeight());current.setRunSummary(draft.getRunSummary());
        current.setNormalizationWarning(draft.getNormalizationWarning());current.setFallbackReason(draft.getFallbackReason());
        current.setSkippedChunks(draft.getSkippedChunks());current.setStatus(failure==null?"COMPLETED":"FAILED");
        current.setErrorMessage(failure);current.setProgress(100);current.setCompletedAt(LocalDateTime.now());tasks.save(current);
        return failure==null;
    }
}
