package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.biddraftagent.repository.BidTenderDocumentSnapshotRepository;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.scoreparse.domain.SummaryAggregator;
import com.xiyu.bid.scoreparse.dto.WarehouseMatchedItem;
import com.xiyu.bid.scoreparse.dto.ScoreParseReviewCommand;
import com.xiyu.bid.scoreparse.dto.KnowledgeMatchResult;
import com.xiyu.bid.scoreparse.dto.WarehouseMatchRequest;
import com.xiyu.bid.scoreparse.dto.ProjectMatchedItem;
import com.xiyu.bid.scoreparse.dto.ScoreParseProgressDTO;
import com.xiyu.bid.scoreparse.dto.CertMatchedItem;
import com.xiyu.bid.scoreparse.dto.ScoreScoringCommand;
import com.xiyu.bid.scoreparse.dto.BidDocumentUploadDTO;
import com.xiyu.bid.scoreparse.dto.PersonMatchedItem;
import com.xiyu.bid.scoreparse.dto.ScoreParseCommand;
import com.xiyu.bid.scoreparse.dto.ActivateScoreExtractionCommand;
import com.xiyu.bid.scoreparse.dto.ScoreParseItemsDTO;
import com.xiyu.bid.scoreparse.dto.ProjectMatchRequest;
import com.xiyu.bid.scoreparse.dto.PersonMatchRequest;
import com.xiyu.bid.scoreparse.dto.ScoreParseTriggerDTO;
import com.xiyu.bid.scoreparse.dto.ScoreItemDTO;
import com.xiyu.bid.scoreparse.dto.ScoreScoringResultsDTO;
import com.xiyu.bid.scoreparse.dto.BrandMatchedItem;
import com.xiyu.bid.scoreparse.dto.BrandMatchRequest;
import com.xiyu.bid.scoreparse.dto.ScoreParseReviewDTO;
import com.xiyu.bid.scoreparse.dto.CertMatchRequest;
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

/** Legacy items endpoint continues to describe the active source, even when latest extraction failed. */
@RequiredArgsConstructor
final class ScoreParseItemsReadSupport {
    private final ScoreItemRepository items;
    private final ScoreParseTaskRepository tasks;
    private final ScoreResultRepository results;
    private final InitiationTenderTextResolver resolver;
    private final BidTenderDocumentSnapshotRepository snapshots;
    private final ProjectDocumentRepository documents;
    ScoreParseItemsDTO read(Long projectId,ScoreParseTask latest,ScoreParseTask scoring) {
        var projection=items.findByProjectIdOrderByItemIndexAsc(projectId);
        var source=projection.stream().filter(i->i.getActiveVersionId()!=null).findFirst()
                .flatMap(i->tasks.findById(i.getParseTaskId())).orElse(latest);
        var summary=new SummaryAggregator().aggregate(projection.stream().map(i->new SummaryAggregator.Item(i.getWeight(),i.getScoreType(),i.getEstScore(),i.getStatusStage1())).toList());
        return new ScoreParseItemsDTO(projection.stream().map(ScoreParseItemsReadSupport::item).toList(),new ScoreParseItemsDTO.Summary(
                summary.totalWeight(),summary.totalEstScore(),summary.okCount(),summary.dangerCount(),summary.pendingCount(),summary.objectiveWeight(),summary.subjectiveWeight(),summary.weightWarning(),
                source==null?null:source.getOriginalTotalWeight(),source==null?null:source.getNormalizationWarning()),
                new ScoreParseItemsMetaBuilder(resolver,snapshots,tasks,items,results,new ScoreBidDocumentLookup(documents,null,null,null,null)).build(projectId,latest,scoring));
    }
    private static ScoreItemDTO item(ScoreItem i) {
        return new ScoreItemDTO(i.getId(),i.getCode(),i.getDim(),i.getDetail(),i.getWeight(),i.getScoreType(),i.getStatusStage1(),i.getEstScore(),i.getEstBasis(),i.getKbHit(),i.getLocation(),i.getSourceCellIds(),i.getWeightEvidence(),i.getExtractionMethod(),i.getConfidence(),i.getValidationStatus(),i.getParentScoreItemId());
    }
}
