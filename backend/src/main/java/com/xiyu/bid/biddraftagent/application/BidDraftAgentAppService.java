package com.xiyu.bid.biddraftagent.application;

import com.xiyu.bid.biddraftagent.domain.BidDraftSnapshot;
import com.xiyu.bid.biddraftagent.domain.GapCheckResult;
import com.xiyu.bid.biddraftagent.domain.ManualConfirmationDecision;
import com.xiyu.bid.biddraftagent.domain.MaterialMatchScore;
import com.xiyu.bid.biddraftagent.domain.RequirementClassification;
import com.xiyu.bid.biddraftagent.domain.WriteCoverageDecision;
import com.xiyu.bid.biddraftagent.dto.BidDraftAgentApplyResponseDTO;
import com.xiyu.bid.biddraftagent.dto.BidDraftAgentReviewDTO;
import com.xiyu.bid.biddraftagent.dto.BidDraftAgentRunDTO;
import com.xiyu.bid.biddraftagent.dto.BidDraftAgentSkippedSectionDTO;
import com.xiyu.bid.biddraftagent.entity.BidAgentArtifact;
import com.xiyu.bid.biddraftagent.entity.BidAgentRun;
import com.xiyu.bid.biddraftagent.repository.BidAgentArtifactRepository;
import com.xiyu.bid.biddraftagent.repository.BidAgentRunRepository;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.service.ProjectAccessScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class BidDraftAgentAppService {

    private final BidDraftSnapshotAssembler snapshotAssembler;
    private final BidDraftAgentEvaluator evaluator;
    private final BidDraftTextGenerator textGenerator;
    private final BidDraftAgentEntityFactory entityFactory;
    private final BidDraftAgentRunMapper runMapper;
    private final BidDraftAgentJsonCodec jsonCodec;
    private final BidDraftAgentDocumentWritePlanner documentWritePlanner;
    private final BidDraftAgentDocumentWriter documentWriter;
    private final BidAgentRunRepository runRepository;
    private final BidAgentArtifactRepository artifactRepository;
    private final ProjectAccessScopeService projectAccessScopeService;

    public BidDraftAgentRunDTO createRun(Long projectId) {
        return createRun(projectId, null);
    }

    public BidDraftAgentRunDTO createRun(Long projectId, Long snapshotId) {
        assertProjectAccess(projectId);
        BidDraftSnapshot snapshot = snapshotAssembler.assemble(projectId, snapshotId);
        BidDraftAgentEvaluation evaluation = evaluator.evaluate(snapshot);
        BidDraftGenerationResult generation = generate(snapshot, evaluation);

        BidAgentRun savedRun = runRepository.save(entityFactory.buildRun(snapshot, evaluation, generation));
        List<BidAgentArtifact> savedArtifacts = artifactRepository.saveAll(entityFactory.buildArtifacts(savedRun, generation));
        return runMapper.toRunDTO(savedRun, savedArtifacts);
    }

    @Transactional(readOnly = true)
    public BidDraftAgentRunDTO getRun(Long projectId, Long runId) {
        assertProjectAccess(projectId);
        BidAgentRun run = requireRun(projectId, runId);
        List<BidAgentArtifact> artifacts = artifactRepository.findByRunIdOrderByCreatedAtAsc(runId);
        return runMapper.toRunDTO(run, artifacts);
    }

    public BidDraftAgentReviewDTO reviewCurrentDraft(Long projectId) {
        assertProjectAccess(projectId);
        BidAgentRun run = runRepository.findTopByProjectIdOrderByCreatedAtDesc(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("BidAgentRun", String.valueOf(projectId)));
        return reviewRun(run);
    }

    public BidDraftAgentReviewDTO reviewRun(Long projectId, Long runId) {
        assertProjectAccess(projectId);
        BidAgentRun run = requireRun(projectId, runId);
        return reviewRun(run);
    }

    private BidDraftAgentReviewDTO reviewRun(BidAgentRun run) {
        BidDraftSnapshot snapshot = jsonCodec.fromJson(run.getSnapshotJson(), BidDraftSnapshot.class);
        BidDraftAgentEvaluation evaluation = evaluator.evaluate(snapshot);
        BidDraftGenerationResult generation = generate(snapshot, evaluation);

        run.setReviewText(generation.reviewSummary());
        run.setReviewedAt(LocalDateTime.now());
        run.setStatus(BidAgentRun.Status.REVIEWED);
        entityFactory.updateEvaluationJson(run, evaluation);
        BidAgentRun savedRun = runRepository.save(run);

        updateReviewArtifact(savedRun.getId(), generation.reviewSummary());
        return runMapper.toReviewDTO(savedRun, evaluation, generation);
    }

    public BidDraftAgentApplyResponseDTO applyRun(Long projectId, Long runId) {
        assertProjectAccess(projectId);
        BidAgentRun run = requireRun(projectId, runId);
        List<BidAgentArtifact> artifacts = artifactRepository.findByRunIdOrderByCreatedAtAsc(runId);
        if (artifacts.isEmpty()) {
            throw new IllegalStateException("当前运行没有可应用的产物");
        }

        BidAgentArtifact primaryArtifact = artifacts.stream()
                .filter(artifact -> "DRAFT_TEXT".equalsIgnoreCase(artifact.getArtifactType()))
                .findFirst()
                .orElse(artifacts.get(0));
        BidDraftAgentEvaluation evaluation = storedEvaluation(run);
        BidDraftAgentDocumentWritePlan writePlan = documentWritePlanner.plan(
                run,
                artifacts,
                evaluation.gapCheck(),
                evaluation.manualConfirmation(),
                evaluation.writeCoverage()
        );
        BidDraftAgentDocumentWriteResult writeResult = documentWriter.write(projectId, writePlan);

        primaryArtifact.setStatus(BidAgentArtifact.Status.READY_FOR_WRITER);
        primaryArtifact.setAppliedAt(LocalDateTime.now());
        artifactRepository.save(primaryArtifact);

        run.setStatus(BidAgentRun.Status.READY_FOR_WRITER);
        run.setAppliedAt(LocalDateTime.now());
        runRepository.save(run);

        return BidDraftAgentApplyResponseDTO.builder()
                .runId(run.getId())
                .projectId(run.getProjectId())
                .artifactId(primaryArtifact.getId())
                .artifactType(primaryArtifact.getArtifactType())
                .status(primaryArtifact.getStatus().name())
                .readyForWriter(true)
                .handoffTarget(primaryArtifact.getHandoffTarget())
                .structureId(writeResult.structureId())
                .structureCreated(writeResult.structureCreated())
                .totalSections(writeResult.totalSections())
                .createdSections(writeResult.createdSections())
                .updatedSections(writeResult.updatedSections())
                .skippedSectionsCount(writeResult.skippedSectionsCount())
                .skippedSections(writeResult.skippedSections().stream()
                        .map(this::toSkippedSectionDTO)
                        .toList())
                .message("AI 标书初稿已写入文档编辑器")
                .build();
    }

    private void assertProjectAccess(Long projectId) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
    }

    private BidDraftGenerationResult generate(
            BidDraftSnapshot snapshot,
            BidDraftAgentEvaluation evaluation
    ) {
        return textGenerator.generate(
                snapshot,
                evaluation.requirementClassification(),
                evaluation.materialMatchScore(),
                evaluation.gapCheck(),
                evaluation.manualConfirmation(),
                evaluation.writeCoverage()
        );
    }

    private BidAgentRun requireRun(Long projectId, Long runId) {
        return runRepository.findByIdAndProjectId(runId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("BidAgentRun", String.valueOf(runId)));
    }

    private BidDraftAgentEvaluation storedEvaluation(BidAgentRun run) {
        return new BidDraftAgentEvaluation(
                jsonCodec.fromJson(run.getRequirementClassificationJson(), RequirementClassification.class),
                jsonCodec.fromJson(run.getMaterialMatchScoreJson(), MaterialMatchScore.class),
                jsonCodec.fromJson(run.getGapCheckJson(), GapCheckResult.class),
                jsonCodec.fromJson(run.getManualConfirmationJson(), ManualConfirmationDecision.class),
                jsonCodec.fromJson(run.getWriteCoverageJson(), WriteCoverageDecision.class)
        );
    }

    private BidDraftAgentSkippedSectionDTO toSkippedSectionDTO(BidDraftAgentSkippedSection skipped) {
        return BidDraftAgentSkippedSectionDTO.builder()
                .sectionId(skipped.sectionId())
                .sectionKey(skipped.sectionKey())
                .title(skipped.title())
                .locked(skipped.locked())
                .reason(skipped.reason())
                .build();
    }

    private void updateReviewArtifact(Long runId, String reviewSummary) {
        artifactRepository.findByRunIdAndArtifactType(runId, "REVIEW_SUMMARY")
                .ifPresent(artifact -> {
                    artifact.setContent(reviewSummary);
                    artifactRepository.save(artifact);
                });
    }
}
