// Input: TenderTextSource + Sidecar 表格客户端
// Output: 结构化表格候选，或结构缺失保留策略所需的失败标记
// Pos: scoreparse/application — docx 结构化表格通道与结构缺失任务收口
package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.RecallOutcome;
import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import com.xiyu.bid.scoreparse.domain.ScoreCompletenessPolicy;
import com.xiyu.bid.scoreparse.domain.ScoreFact;
import com.xiyu.bid.scoreparse.domain.ScoreParseDecision;
import com.xiyu.bid.scoreparse.domain.ScoreParsePipeline;
import com.xiyu.bid.scoreparse.domain.StructuredTableCandidateExtractor;
import com.xiyu.bid.scoreparse.domain.WeightSumCheck;
import com.xiyu.bid.scoreparse.entity.ScoreParseTask;
import com.xiyu.bid.scoreparse.repository.ScoreParseTaskRepository;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
final class StructuredTableExtractionHandler {

    private StructuredTableExtractionHandler() {
    }

    static Result extract(TenderTextSource source,
                          InitiationTenderTextResolver textResolver,
                          SidecarDocumentArtifactExtractor artifactExtractor,
                          SidecarTableExtractor sidecarTableExtractor) {
        String fileName = source.fileName();
        if (!supportsStructuredEvidence(fileName)) {
            return Result.ok(List.of());
        }
        try {
            byte[] content = textResolver.loadBytes(source.fileUrl());
            if (content == null || content.length == 0) {
                return Result.ok(List.of());
            }
            if (artifactExtractor != null) {
                return extractEvidencePipeline(content, fileName, source.text(), artifactExtractor);
            }
            var report = StructuredTableCandidateExtractor.extractWithSectionReport(
                    sidecarTableExtractor.extract(content, fileName));
            if (!report.sectionDimMissTables().isEmpty()) {
                log.warn("结构化表格章节推导缺失（疑似新措辞独立分表，需人工复核）: file={}, missTables={}",
                        fileName, report.sectionDimMissTables());
            }
            return Result.ok(report.candidates());
        } catch (RuntimeException | IOException exception) {
            log.warn("结构化表格提取失败，标记结构不完整，不走 markdown: file={}, msg={}",
                    fileName, exception.getMessage());
            return Result.structureIncomplete("STRUCTURE_DEFECT");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("结构化表格提取被中断: file={}", fileName);
            return Result.structureIncomplete("STRUCTURE_DEFECT");
        }
    }

    private static boolean supportsStructuredEvidence(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lower = fileName.toLowerCase();
        return lower.endsWith(".docx") || lower.endsWith(".pdf");
    }

    private static Result extractEvidencePipeline(byte[] content,
                                                  String fileName,
                                                  String tenderSectionText,
                                                  SidecarDocumentArtifactExtractor artifactExtractor)
            throws IOException, InterruptedException {
        var artifact = artifactExtractor.extract(content, fileName);
        if (shouldFallbackPdfToLegacy(fileName, artifact)) {
            log.warn("PDF 未抽取到结构化评分表，回落 legacy markdown 召回: file={}", fileName);
            return Result.ok(List.of());
        }
        var shadow = ScoreFactShadowPipeline.run(artifact, tenderSectionText);
        boolean structureBroken = shadow.tables().stream()
                .anyMatch(table -> !table.structurallyValid());
        if (structureBroken) {
            log.warn("DocumentArtifact v2 结构校验失败，保留现有评分项不覆盖: file={}, tables={}",
                    fileName, shadow.tables().size());
            return Result.structureIncomplete("STRUCTURE_DEFECT");
        }
        var completeness = shadow.completeness();
        List<ScoreFact> facts = shadow.tables().stream()
                .flatMap(table -> table.facts().stream())
                .toList();
        List<ScoreCandidate> leafCandidates = completeness.leafFacts().stream()
                .map(ScoreFact::toCandidate)
                .flatMap(Optional::stream)
                .toList();
        log.info("Evidence pipeline 结构化事实抽取完成: file={}, facts={}, leafCandidates={}, decision={}, fallback={}",
                fileName, facts.size(), leafCandidates.size(), completeness.decision(), completeness.fallbackReason());
        return Result.evidencePipeline(facts, leafCandidates, completeness);
    }

    private static boolean shouldFallbackPdfToLegacy(String fileName,
                                                    com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact artifact) {
        if (fileName == null || !fileName.toLowerCase().endsWith(".pdf")) {
            return false;
        }
        return artifact == null || artifact.tables().stream()
                .noneMatch(table -> table != null && Boolean.TRUE.equals(table.isScoreTable()));
    }

    static void markIncomplete(ScoreParseTask task,
                               String taskId,
                               String failureReason,
                               ScoreParseTaskRepository taskRepository,
                               ScoreParseTaskStateService stateService,
                               ScoreParseProgressService progressService) {
        String reason = (failureReason == null || failureReason.isBlank()) ? "STRUCTURE_DEFECT" : failureReason;
        task.setRunSummary(RunSummaryAssembler.assemble(
                new RecallOutcome(List.of(), 0),
                0,
                new RunSummaryAssembler.MergeView(false, false, 0, 0, 0),
                new WeightSumCheck.Result(BigDecimal.ZERO, true, true, Map.of(), Map.of()),
                0,
                "",
                ScoreParsePipeline.LEGACY_FALLBACK,
                ScoreParseDecision.STRUCTURE_INCOMPLETE.name(),
                reason).json());
        task.setPipelineVersion(ScoreParsePipeline.LEGACY_FALLBACK);
        task.setDecisionStatus(ScoreParseDecision.STRUCTURE_INCOMPLETE.name());
        task.setFallbackReason(reason);
        taskRepository.save(task);
        stateService.updateProgress(taskId, 85, "结构化异常，保留现有评分项不覆盖");
        progressService.updateProgress(taskId,
                new com.xiyu.bid.scoreparse.dto.ScoreParseProgressDTO(
                        taskId, "PROCESSING", 85, "结构化异常，保留现有评分项不覆盖", null, null, null));
        stateService.markCompleted(taskId);
        progressService.clearProgress(taskId);
    }

    static void markEvidencePipelineCompleted(ScoreParseTask task,
                                              String taskId,
                                              Result structured,
                                              ScoreParseTaskRepository taskRepository,
                                              ScoreParseTaskStateService stateService,
                                              ScoreParseProgressService progressService,
                                              ScoreItemPersistenceService itemPersistenceService,
                                              EstimatedScoreService estimatedScoreService) {
        List<ScoreCandidate> candidates = structured.candidates();
        var completeness = structured.completeness();
        ScoreParseDecision decision = completeness == null
                ? ScoreParseDecision.PARSE_FAILED
                : completeness.decision();
        String fallbackReason = completeness == null ? "COMPLETENESS_DEFECT" : completeness.fallbackReason();
        WeightSumCheck.Result weightResult = evidenceWeightResult(candidates, completeness, decision);
        task.setRunSummary(RunSummaryAssembler.assemble(
                new RecallOutcome(List.of(), 0),
                candidates.size(),
                new RunSummaryAssembler.MergeView(false, false, 0, 0, 0),
                weightResult,
                candidates.size(),
                "",
                ScoreParsePipeline.EVIDENCE_PIPELINE_V1,
                decision.name(),
                fallbackReason).json());
        task.setPipelineVersion(ScoreParsePipeline.EVIDENCE_PIPELINE_V1);
        task.setDecisionStatus(decision.name());
        task.setFallbackReason(fallbackReason);
        BigDecimal declaredTotal = completeness == null ? BigDecimal.valueOf(100) : completeness.declaredTotalScore();
        if (weightResult.totalWeight() != null && declaredTotal != null
                && weightResult.totalWeight().compareTo(declaredTotal) != 0) {
            task.setOriginalTotalWeight(weightResult.totalWeight());
            task.setNormalizationWarning(String.format(
                    "评分权重合计为 %s分，不符合%s分声明总分（Evidence Pipeline 不自动归一化，保留旧评分项待复核）",
                    weightResult.totalWeight().stripTrailingZeros().toPlainString(),
                    declaredTotal.stripTrailingZeros().toPlainString()));
        }
        taskRepository.save(task);
        if (!structured.facts().isEmpty()) {
            itemPersistenceService.persistFactsAndEvidence(task.getProjectId(), task.getId(), structured.facts());
        }
        if (decision == ScoreParseDecision.PARSE_FAILED) {
            stateService.failTask(taskId, "Evidence Pipeline 未提取到可自动落库的评分项");
            progressService.clearProgress(taskId);
            return;
        }
        if (decision == ScoreParseDecision.AUTO_APPROVED && !candidates.isEmpty()) {
            updateProgress(taskId, 90, "Evidence Pipeline 评分项落库", stateService, progressService);
            itemPersistenceService.persistItems(task.getProjectId(), task.getId(), candidates);
            updateProgress(taskId, 95, "阶段 1 预计得分计算", stateService, progressService);
            estimatedScoreService.estimateForProject(task.getProjectId());
        } else {
            log.warn("Evidence Pipeline 判定需复核，保留现有评分项不覆盖: taskId={}, decision={}, fallback={}",
                    taskId, decision, fallbackReason);
        }
        stateService.markCompleted(taskId);
        progressService.clearProgress(taskId);
    }

    private static WeightSumCheck.Result evidenceWeightResult(
            List<ScoreCandidate> candidates,
            ScoreCompletenessPolicy.ScoreCompletenessResult completeness,
            ScoreParseDecision decision) {
        WeightSumCheck.Result legacy = new WeightSumCheck().checkCandidates(candidates);
        if (completeness == null || completeness.declaredTotalScore() == null) {
            return legacy;
        }
        boolean weightWarning = completeness.leafWeightSum() == null
                || completeness.leafWeightSum().compareTo(completeness.declaredTotalScore()) != 0;
        boolean needRecheck = weightWarning || decision != ScoreParseDecision.AUTO_APPROVED;
        return new WeightSumCheck.Result(
                legacy.totalWeight(), weightWarning, needRecheck,
                legacy.dimensionWeights(), legacy.declaredWeights());
    }

    private static void updateProgress(String taskId,
                                       int progress,
                                       String stageText,
                                       ScoreParseTaskStateService stateService,
                                       ScoreParseProgressService progressService) {
        stateService.updateProgress(taskId, progress, stageText);
        progressService.updateProgress(taskId,
                new com.xiyu.bid.scoreparse.dto.ScoreParseProgressDTO(
                        taskId, "PROCESSING", progress, stageText, null, null, null));
    }

    record Result(List<ScoreCandidate> candidates,
                  boolean structureIncomplete,
                  String failureReason,
                  boolean evidencePipeline,
                  List<ScoreFact> facts,
                  ScoreCompletenessPolicy.ScoreCompletenessResult completeness) {

        static Result ok(List<ScoreCandidate> candidates) {
            return new Result(candidates, false, null, false, List.of(), null);
        }

        static Result structureIncomplete(String failureReason) {
            return new Result(List.of(), true, failureReason, false, List.of(), null);
        }

        static Result evidencePipeline(List<ScoreFact> facts,
                                       List<ScoreCandidate> candidates,
                                       ScoreCompletenessPolicy.ScoreCompletenessResult completeness) {
            return new Result(candidates, false, null, true,
                    facts == null ? List.of() : List.copyOf(facts), completeness);
        }
    }
}
