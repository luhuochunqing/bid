package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.biddraftagent.repository.BidTenderDocumentSnapshotRepository;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.scoreparse.domain.SummaryAggregator;
import com.xiyu.bid.scoreparse.dto.ScoreItemDTO;
import com.xiyu.bid.scoreparse.dto.ScoreParseItemsDTO;
import com.xiyu.bid.scoreparse.entity.ScoreItem;
import com.xiyu.bid.scoreparse.entity.ScoreParseTask;
import com.xiyu.bid.scoreparse.repository.ScoreItemRepository;
import com.xiyu.bid.scoreparse.repository.ScoreParseTaskRepository;
import com.xiyu.bid.scoreparse.repository.ScoreResultRepository;
import java.time.LocalDate;

/** Read projection hides unverifiable knowledge estimates without altering stored history. */
final class ScoreParseItemsReadSupport {
    private final ScoreItemRepository items;
    private final ScoreParseTaskRepository tasks;
    private final ScoreResultRepository results;
    private final InitiationTenderTextResolver resolver;
    private final BidTenderDocumentSnapshotRepository snapshots;
    private final ProjectDocumentRepository documents;
    private final ScoreEstimateFreshness freshness;

    ScoreParseItemsReadSupport(ScoreItemRepository items, ScoreParseTaskRepository tasks,
            ScoreResultRepository results, InitiationTenderTextResolver resolver,
            BidTenderDocumentSnapshotRepository snapshots, ProjectDocumentRepository documents) {
        this(items,tasks,results,resolver,snapshots,documents,null);
    }
    ScoreParseItemsReadSupport(ScoreItemRepository items, ScoreParseTaskRepository tasks,
            ScoreResultRepository results, InitiationTenderTextResolver resolver,
            BidTenderDocumentSnapshotRepository snapshots, ProjectDocumentRepository documents,
            ScoreEstimateFreshness freshness) {
        this.items=items; this.tasks=tasks; this.results=results; this.resolver=resolver;
        this.snapshots=snapshots; this.documents=documents; this.freshness=freshness;
    }
    ScoreParseItemsDTO read(Long projectId, ScoreParseTask latest, ScoreParseTask scoring) {
        var stored=items.findByProjectIdOrderByItemIndexAsc(projectId);
        var source=stored.stream().filter(i->i.getParseTaskId()!=null).findFirst()
                .flatMap(i->tasks.findById(i.getParseTaskId())).orElse(latest);
        var projected=stored.stream().map(this::project).toList();
        var summary=new SummaryAggregator().aggregate(projected.stream().map(i->
                new SummaryAggregator.Item(i.weight(),i.scoreType(),i.estScore(),i.status())).toList());
        return new ScoreParseItemsDTO(projected,new ScoreParseItemsDTO.Summary(
                summary.totalWeight(),summary.totalEstScore(),summary.okCount(),summary.dangerCount(),
                summary.pendingCount(),summary.objectiveWeight(),summary.subjectiveWeight(),summary.weightWarning(),
                source==null?null:source.getOriginalTotalWeight(),source==null?null:source.getNormalizationWarning()),
                new ScoreParseItemsMetaBuilder(resolver,snapshots,tasks,items,results,
                        new ScoreBidDocumentLookup(documents,null,null,null,null)).build(projectId,latest,scoring));
    }
    private ScoreItemDTO project(ScoreItem i) {
        String state=!(CertificateScoreEstimateSupport.applies(i)||EstimateEvidenceSupport.applies(i))?"NOT_APPLICABLE":
                freshness==null?"STALE":freshness.status(i,LocalDate.now());
        boolean stale="STALE".equals(state);
        return new ScoreItemDTO(i.getId(),i.getCode(),i.getDim(),i.getDetail(),i.getWeight(),i.getScoreType(),
                stale?"PENDING":i.getStatusStage1(),stale?null:i.getEstScore(),
                stale && i.getEstScore()!=null?"知识库预判已过期或缺少计算记录，请更新知识库预判":i.getEstBasis(),
                stale?Boolean.FALSE:i.getKbHit(),i.getLocation(),i.getSourceCellIds(),i.getWeightEvidence(),
                i.getExtractionMethod(),i.getConfidence(),i.getValidationStatus(),i.getParentScoreItemId(),
                state,i.getEstimatedAt(),i.getDeliverableType(),i.getScoringMode());
    }
}
