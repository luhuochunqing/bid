package com.xiyu.bid.biddraftagent.application;

import com.xiyu.bid.biddraftagent.domain.BidDraftSnapshot;
import com.xiyu.bid.biddraftagent.entity.BidAgentArtifact;
import com.xiyu.bid.biddraftagent.entity.BidAgentRun;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BidDraftAgentEntityFactory {

    private static final String GENERATOR_KEY = "openai-responses-v1";

    private final BidDraftAgentJsonCodec jsonCodec;

    public BidAgentRun buildRun(
            BidDraftSnapshot snapshot,
            BidDraftAgentEvaluation evaluation,
            BidDraftGenerationResult generation
    ) {
        return BidAgentRun.builder()
                .projectId(snapshot.projectId())
                .tenderId(snapshot.tenderId())
                .projectName(snapshot.projectName())
                .tenderTitle(snapshot.tenderTitle())
                .status(BidAgentRun.Status.DRAFTED)
                .snapshotJson(jsonCodec.toJson(snapshot))
                .requirementClassificationJson(jsonCodec.toJson(evaluation.requirementClassification()))
                .materialMatchScoreJson(jsonCodec.toJson(evaluation.materialMatchScore()))
                .gapCheckJson(jsonCodec.toJson(evaluation.gapCheck()))
                .manualConfirmationJson(jsonCodec.toJson(evaluation.manualConfirmation()))
                .writeCoverageJson(jsonCodec.toJson(evaluation.writeCoverage()))
                .draftText(generation.draftText())
                .reviewText(generation.reviewSummary())
                .generatorKey(GENERATOR_KEY)
                .build();
    }

    public List<BidAgentArtifact> buildArtifacts(BidAgentRun run, BidDraftGenerationResult generation) {
        return generation.artifactSpecs().stream()
                .map(spec -> BidAgentArtifact.builder()
                        .runId(run.getId())
                        .projectId(run.getProjectId())
                        .artifactType(spec.artifactType())
                        .title(spec.title())
                        .content(spec.content())
                        .handoffTarget(spec.handoffTarget())
                        .status(BidAgentArtifact.Status.DRAFTED)
                        .build())
                .toList();
    }

    public void updateEvaluationJson(BidAgentRun run, BidDraftAgentEvaluation evaluation) {
        run.setRequirementClassificationJson(jsonCodec.toJson(evaluation.requirementClassification()));
        run.setMaterialMatchScoreJson(jsonCodec.toJson(evaluation.materialMatchScore()));
        run.setGapCheckJson(jsonCodec.toJson(evaluation.gapCheck()));
        run.setManualConfirmationJson(jsonCodec.toJson(evaluation.manualConfirmation()));
        run.setWriteCoverageJson(jsonCodec.toJson(evaluation.writeCoverage()));
    }
}
