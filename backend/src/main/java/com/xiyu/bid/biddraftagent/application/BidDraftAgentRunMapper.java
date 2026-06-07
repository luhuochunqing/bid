package com.xiyu.bid.biddraftagent.application;

import com.xiyu.bid.biddraftagent.domain.BidDraftSnapshot;
import com.xiyu.bid.biddraftagent.domain.GapCheckResult;
import com.xiyu.bid.biddraftagent.domain.ManualConfirmationDecision;
import com.xiyu.bid.biddraftagent.domain.MaterialMatchScore;
import com.xiyu.bid.biddraftagent.domain.RequirementClassification;
import com.xiyu.bid.biddraftagent.domain.WriteCoverageDecision;
import com.xiyu.bid.biddraftagent.dto.BidDraftAgentArtifactDTO;
import com.xiyu.bid.biddraftagent.dto.BidDraftAgentReviewDTO;
import com.xiyu.bid.biddraftagent.dto.BidDraftAgentRunDTO;
import com.xiyu.bid.biddraftagent.entity.BidAgentArtifact;
import com.xiyu.bid.biddraftagent.entity.BidAgentRun;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BidDraftAgentRunMapper {

    private final BidDraftAgentJsonCodec jsonCodec;

    public BidDraftAgentRunDTO toRunDTO(BidAgentRun run, List<BidAgentArtifact> artifacts) {
        return BidDraftAgentRunDTO.builder()
                .id(run.getId())
                .projectId(run.getProjectId())
                .tenderId(run.getTenderId())
                .projectName(run.getProjectName())
                .tenderTitle(run.getTenderTitle())
                .status(run.getStatus().name())
                .snapshot(jsonCodec.fromJson(run.getSnapshotJson(), BidDraftSnapshot.class))
                .requirementClassification(jsonCodec.fromJson(run.getRequirementClassificationJson(), RequirementClassification.class))
                .materialMatchScore(jsonCodec.fromJson(run.getMaterialMatchScoreJson(), MaterialMatchScore.class))
                .gapCheck(jsonCodec.fromJson(run.getGapCheckJson(), GapCheckResult.class))
                .manualConfirmation(jsonCodec.fromJson(run.getManualConfirmationJson(), ManualConfirmationDecision.class))
                .writeCoverage(jsonCodec.fromJson(run.getWriteCoverageJson(), WriteCoverageDecision.class))
                .draftText(run.getDraftText())
                .reviewText(run.getReviewText())
                .artifacts(artifacts.stream().map(this::toArtifactDTO).toList())
                .reviewedAt(run.getReviewedAt())
                .appliedAt(run.getAppliedAt())
                .createdAt(run.getCreatedAt())
                .updatedAt(run.getUpdatedAt())
                .build();
    }

    public BidDraftAgentReviewDTO toReviewDTO(
            BidAgentRun run,
            BidDraftAgentEvaluation evaluation,
            BidDraftGenerationResult generation
    ) {
        List<String> nextActions = new ArrayList<>(evaluation.writeCoverage().recommendedSections());
        nextActions.addAll(evaluation.gapCheck().suggestions());
        return BidDraftAgentReviewDTO.builder()
                .runId(run.getId())
                .projectId(run.getProjectId())
                .status(run.getStatus().name())
                .reviewSummary(generation.reviewSummary())
                .draftText(run.getDraftText())
                .requirementClassification(evaluation.requirementClassification())
                .materialMatchScore(evaluation.materialMatchScore())
                .gapCheck(evaluation.gapCheck())
                .manualConfirmation(evaluation.manualConfirmation())
                .writeCoverage(evaluation.writeCoverage())
                .nextActions(nextActions)
                .reviewedAt(run.getReviewedAt())
                .build();
    }

    private BidDraftAgentArtifactDTO toArtifactDTO(BidAgentArtifact artifact) {
        return BidDraftAgentArtifactDTO.builder()
                .id(artifact.getId())
                .runId(artifact.getRunId())
                .artifactType(artifact.getArtifactType())
                .title(artifact.getTitle())
                .content(artifact.getContent())
                .handoffTarget(artifact.getHandoffTarget())
                .status(artifact.getStatus().name())
                .appliedAt(artifact.getAppliedAt())
                .createdAt(artifact.getCreatedAt())
                .updatedAt(artifact.getUpdatedAt())
                .build();
    }
}
