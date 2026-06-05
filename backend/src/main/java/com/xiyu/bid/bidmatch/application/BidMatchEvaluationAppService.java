package com.xiyu.bid.bidmatch.application;

import com.xiyu.bid.bidmatch.domain.BidMatchModelVersionSnapshot;
import com.xiyu.bid.bidmatch.domain.BidMatchScoreEvaluation;
import com.xiyu.bid.bidmatch.domain.BidMatchScoringPolicy;
import com.xiyu.bid.bidmatch.dto.BidMatchEvaluationResponse;
import com.xiyu.bid.bidmatch.infrastructure.persistence.entity.BidMatchScoreEvaluationEntity;
import com.xiyu.bid.bidmatch.infrastructure.persistence.repository.BidMatchScoreEvaluationJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class BidMatchEvaluationAppService {

    private static final String SYSTEM_OPERATOR = "system";

    private final BidMatchModelAppService modelAppService;
    private final BidMatchEvidenceAssembler evidenceAssembler;
    private final BidMatchScoreEvaluationJpaRepository evaluationRepository;
    private final BidMatchJsonCodec jsonCodec;
    private final BidMatchEvaluationMapper mapper;
    private final BidMatchScoringPolicy policy = new BidMatchScoringPolicy();

    @Transactional
    public BidMatchEvaluationResponse evaluate(Long tenderId) {
        BidMatchActiveModelVersion activeVersion = modelAppService.activeVersion();
        BidMatchModelVersionSnapshot snapshot = activeVersion.snapshot();
        BidMatchEvidenceBundle evidenceBundle = evidenceAssembler.assemble(tenderId);
        BidMatchScoreEvaluation evaluation = policy.evaluate(snapshot, evidenceBundle.evidence());
        BidMatchScoreEvaluationEntity entity = new BidMatchScoreEvaluationEntity();
        entity.setTenderId(tenderId);
        entity.setModelId(snapshot.modelId());
        entity.setModelVersionId(activeVersion.versionEntityId());
        entity.setModelVersionNo(snapshot.versionNo());
        entity.setTotalScore(evaluation.totalScore());
        entity.setDimensionScoresJson(jsonCodec.toJson(evaluation.dimensionScores()));
        entity.setEvidenceJson(jsonCodec.toJson(evidenceBundle.snapshot()));
        entity.setEvidenceFingerprint(evidenceBundle.fingerprint());
        entity.setModelSnapshotJson(jsonCodec.toJson(snapshot));
        entity.setEvaluatedBy(SYSTEM_OPERATOR);
        entity.setEvaluatedAt(LocalDateTime.now());
        return mapper.toResponse(evaluationRepository.save(entity), false);
    }

    @Transactional
    public BidMatchEvaluationResponse latest(Long tenderId) {
        return evaluationRepository.findFirstByTenderIdOrderByEvaluatedAtDescIdDesc(tenderId)
                .map(entity -> mapper.toResponse(entity, stale(entity)))
                .orElse(null);
    }

    @Transactional
    public List<BidMatchEvaluationResponse> history(Long tenderId) {
        StaleContext staleContext = staleContext(tenderId);
        return evaluationRepository.findByTenderIdOrderByEvaluatedAtDescIdDesc(tenderId).stream()
                .map(entity -> mapper.toResponse(entity, stale(entity, staleContext)))
                .toList();
    }

    @Transactional
    public BidMatchEvaluationResponse get(Long evaluationId) {
        BidMatchScoreEvaluationEntity entity = evaluationRepository.findById(evaluationId)
                .orElseThrow(() -> new IllegalArgumentException("投标匹配评分结果不存在"));
        return mapper.toResponse(entity, stale(entity));
    }

    private boolean stale(BidMatchScoreEvaluationEntity entity) {
        return stale(entity, staleContext(entity.getTenderId()));
    }

    private StaleContext staleContext(Long tenderId) {
        try {
            BidMatchActiveModelVersion activeVersion = modelAppService.activeVersion();
            String currentFingerprint = evidenceAssembler.assemble(tenderId).fingerprint();
            return new StaleContext(activeVersion.versionEntityId(), currentFingerprint);
        } catch (IllegalStateException exception) {
            return null;
        }
    }

    private boolean stale(BidMatchScoreEvaluationEntity entity, StaleContext context) {
        if (context == null) {
            return true;
        }
        return !Objects.equals(context.activeVersionId(), entity.getModelVersionId())
                || !Objects.equals(context.currentFingerprint(), entity.getEvidenceFingerprint());
    }

    private record StaleContext(Long activeVersionId, String currentFingerprint) {
    }
}
