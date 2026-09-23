// Input: TenderTextSource + Sidecar 表格客户端
// Output: 结构化表格候选，或结构缺失保留策略所需的失败标记
// Pos: scoreparse/application — docx 结构化表格通道与结构缺失任务收口
package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.RecallOutcome;
import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import com.xiyu.bid.scoreparse.domain.ScoreCompletenessPolicy;
import com.xiyu.bid.scoreparse.domain.ScoreFact;
import com.xiyu.bid.scoreparse.domain.OriginalScoringContext;
import com.xiyu.bid.scoreparse.domain.ScoreParseDecision;
import com.xiyu.bid.scoreparse.domain.ScoreParsePipeline;
import com.xiyu.bid.scoreparse.domain.StructuredTableCandidateExtractor;
import com.xiyu.bid.scoreparse.domain.TableCompleteness;
import com.xiyu.bid.scoreparse.domain.WeightSumCheck;
import com.xiyu.bid.scoreparse.entity.ScoreParseTask;
import com.xiyu.bid.scoreparse.repository.ScoreParseTaskRepository;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
final class StructuredTableExtractionHandler {
    private StructuredTableExtractionHandler() {
    }
    static StructuredTableResult extract(TenderTextSource source,
                                          LoadedBidFile loaded,
                                          SidecarDocumentArtifactExtractor artifactExtractor,
                                          SidecarTableExtractor sidecarTableExtractor) {
        String fileName = source.fileName();
        if (!StructuredTableSourceSupport.supportsStructuredEvidence(fileName) || loaded == null) {
            return StructuredTableResult.ok(List.of());
        }
        try {
            if (artifactExtractor != null) {
                return extractEvidencePipeline(loaded, fileName, source.text(), artifactExtractor);
            }
            var sidecarExtraction = switch (loaded) {
                case LoadedBidFile.InMemory inMem -> sidecarTableExtractor.extract(inMem.content(), fileName);
                case LoadedBidFile.OnDisk onDisk -> sidecarTableExtractor.extract(onDisk.tempFile(), fileName);
            };
            var report = StructuredTableCandidateExtractor.extractWithSectionReport(sidecarExtraction);
            if (!report.sectionDimMissTables().isEmpty()) {
                log.warn("结构化表格章节推导缺失（疑似新措辞独立分表，需人工复核）: file={}, missTables={}",
                        fileName, report.sectionDimMissTables());
            }
            return StructuredTableResult.ok(report.candidates());
        } catch (RuntimeException | IOException exception) {
            log.warn("结构化表格提取失败，标记结构不完整，不走 markdown: file={}, msg={}",
                    fileName, exception.getMessage());
            return StructuredTableResult.structureIncomplete("STRUCTURE_DEFECT");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("结构化表格提取被中断: file={}", fileName);
            return StructuredTableResult.structureIncomplete("STRUCTURE_DEFECT");
        }
    }
    static StructuredTableResult extractWithTextModelHeartbeat(TenderTextSource source, LoadedBidFile loaded,
            SidecarDocumentArtifactExtractor artifactExtractor, MultimodalScoreFactExtractor multimodalExtractor,
            Runnable chunkCompleted) {
        return MultimodalStructuredExtractionHandler.extractWithChunkHeartbeat(source, loaded, artifactExtractor,
                multimodalExtractor, chunkCompleted);
    }
    static StructuredTableResult extractWithTextModelHeartbeat(TenderTextSource source, List<LoadedBidFile> loadedFiles,
            List<TenderTextSource> sources, SidecarDocumentArtifactExtractor artifactExtractor,
            MultimodalScoreFactExtractor multimodalExtractor, Runnable chunkCompleted) {
        return MultimodalStructuredExtractionHandler.extractWithTextModelHeartbeat(source, loadedFiles, sources,
                artifactExtractor, multimodalExtractor, chunkCompleted);
    }
    static StructuredTableResult extract(TenderTextSource source,
                                          InitiationTenderTextResolver textResolver,
                                          SidecarDocumentArtifactExtractor artifactExtractor,
                                          SidecarTableExtractor sidecarTableExtractor) {
        LoadedBidFile loaded = source.fileUrl() != null ? textResolver.loadTenderFile(source.fileUrl(), source.fileName(), null) : null;
        try {
            return extract(source, loaded, artifactExtractor, sidecarTableExtractor);
        } finally {
            if (loaded != null) {
                textResolver.releaseTenderFile(loaded);
            }
        }
    }
    private static StructuredTableResult extractEvidencePipeline(LoadedBidFile loaded,
                                                   String fileName,
                                                   String tenderSectionText,
                                                   SidecarDocumentArtifactExtractor artifactExtractor)
            throws IOException, InterruptedException {
        var artifact = switch (loaded) {
            case LoadedBidFile.InMemory inMem -> artifactExtractor.extract(inMem.content(), fileName);
            case LoadedBidFile.OnDisk onDisk -> artifactExtractor.extract(onDisk.tempFile(), fileName);
        };
        var originalContext = OriginalScoringContext.resolve(artifact, tenderSectionText);
        if (StructuredTableSourceSupport.shouldFallbackPdfToLegacy(fileName, artifact)) {
            log.warn("PDF 未抽取到结构化评分表，回落 legacy markdown 召回: file={}", fileName);
            return StructuredTableResult.ok(List.of());
        }
        var shadow = ScoreFactShadowPipeline.run(artifact, tenderSectionText);
        boolean allStructureBroken = !shadow.tables().isEmpty()
                && shadow.tables().stream().allMatch(table -> !table.structurallyValid());
        if (allStructureBroken) {
            log.warn("DocumentArtifact v2 全部评分表结构校验失败，保留现有评分项不覆盖: file={}, tables={}",
                    fileName, shadow.tables().size());
            return StructuredTableResult.structureIncomplete("STRUCTURE_DEFECT");
        }
        var completeness = shadow.completeness();
        List<ScoreFact> facts = shadow.tables().stream()
                .flatMap(table -> table.facts().stream())
                .toList();
        var originalCheck = OriginalSourcePublicationGuard.verify(originalContext, artifact, facts);
        List<String> sourceDefects = originalCheck.defects();
        boolean originalVerified = sourceDefects.isEmpty();
        if (!originalVerified) {
            completeness = OriginalSourcePublicationGuard.review(completeness, sourceDefects, originalCheck);
            facts = facts.stream().map(f -> f.withValidationStatus(ScoreFact.ValidationStatus.REVIEW_REQUIRED)).toList();
        } else if (completeness != null && completeness.decision() == ScoreParseDecision.AUTO_APPROVED) {
            completeness = OriginalSourcePublicationGuard.verified(completeness, originalCheck);
        }
        // spec 062：聚合所有 AUTO_APPROVED 表的 AUTO_APPROVED 叶子候选（不再要求整份文档 AUTO_APPROVED）
        List<ScoreCandidate> leafCandidates = shadow.tableCompletenesses().stream()
                .filter(table -> table.completeness() != null
                        && table.completeness().decision() == ScoreParseDecision.AUTO_APPROVED)
                .flatMap(table -> table.completeness().leafFacts().stream())
                .map(ScoreFact::toCandidate)
                .flatMap(Optional::stream)
                .toList();
        log.info("Evidence pipeline 结构化事实抽取完成: file={}, facts={}, leafCandidates={}, "
                        + "declaredTotal={}, declaredTotalSource={}, decision={}, fallback={}, splitTables={}",
                fileName, facts.size(), leafCandidates.size(), completeness.declaredTotalScore(),
                completeness.declaredTotalSource(), completeness.decision(), completeness.fallbackReason(),
                shadow.tableCompletenesses().size());
        return StructuredTableResult.evidencePipeline(facts, leafCandidates, completeness,
                shadow.tableCompletenesses(), originalCheck.proofs());
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
                                               StructuredTableResult structured,
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
        if (decision == ScoreParseDecision.AUTO_APPROVED && !structured.originalVerified()) {
            decision = ScoreParseDecision.REVIEW_REQUIRED;
        }
        String fallbackReason = completeness == null ? "COMPLETENESS_DEFECT" : completeness.fallbackReason();
        if (decision == ScoreParseDecision.REVIEW_REQUIRED && !structured.originalVerified()
                && completeness != null && completeness.decision() == ScoreParseDecision.AUTO_APPROVED) {
            fallbackReason = "ORIGINAL_SOURCE_UNVERIFIED";
        }
        // spec 062：权重合计信号基于全部表叶子候选（不以落库过滤集为准），
        // 保证文档级 REVIEW_REQUIRED 时 originalTotalWeight 仍反映全部事实权重。
        List<ScoreCandidate> allLeafCandidates = structured.tableCompletenesses().stream()
                .filter(table -> table.completeness() != null)
                .flatMap(table -> table.completeness().leafFacts().stream())
                .map(ScoreFact::toCandidate)
                .flatMap(Optional::stream)
                .toList();
        WeightSumCheck.Result weightResult = evidenceWeightResult(allLeafCandidates, completeness, decision);
        List<Map<String, Object>> tableSplits = structured.tableCompletenesses().stream()
                .map(StructuredTableExtractionHandler::tableSplitView).toList();
        List<String> subtotalDefects = completeness == null ? List.of() : completeness.defects().stream()
                .filter(defect -> defect.startsWith("TABLE_SUBTOTAL_MISMATCH")).toList();
        task.setRunSummary(RunSummaryAssembler.assemble(
                new RecallOutcome(List.of(), 0), candidates.size(),
                new RunSummaryAssembler.MergeView(false, false, 0, 0, 0), weightResult, candidates.size(),
                structured.aiProviderDiagnostics() != null ? structured.aiProviderDiagnostics().model() : "",
                ScoreParsePipeline.EVIDENCE_PIPELINE_V1, decision.name(), fallbackReason,
                tableSplits, subtotalDefects, structured.aiProviderDiagnostics()).json());
        task.setPipelineVersion(ScoreParsePipeline.EVIDENCE_PIPELINE_V1);
        task.setDecisionStatus(decision.name());
        task.setFallbackReason(fallbackReason);
        BigDecimal declaredTotal = completeness == null ? null : completeness.declaredTotalScore();
        // spec 062：多表场景文档级 declaredTotal 与全表 totalWeight 无直接可比性，
        // 归一化告警以 weightWarning（leafWeightSum vs 声明总分）为唯一判据，避免多表自洽必现误报。
        if (weightResult.weightWarning() && weightResult.totalWeight() != null && declaredTotal != null) {
            task.setOriginalTotalWeight(weightResult.totalWeight());
            task.setNormalizationWarning(String.format(
                    "评分权重合计为 %s分，不符合%s分声明总分（Evidence Pipeline 不自动归一化，保留旧评分项待复核）",
                    weightResult.totalWeight().stripTrailingZeros().toPlainString(),
                    declaredTotal.stripTrailingZeros().toPlainString()));
        }
        taskRepository.save(task);
        structured.tableCompletenesses().forEach(table -> {
            var tableCompleteness = table.completeness();
            log.info("Evidence pipeline 分表拆分: taskId={}, tableId={}, declaredTotal={}, decision={}, fallback={}",
                    taskId, table.tableId(),
                    tableCompleteness == null ? null : tableCompleteness.declaredTotalScore(),
                    tableCompleteness == null ? null : tableCompleteness.decision(),
                    tableCompleteness == null ? null : tableCompleteness.fallbackReason());
        });
        if (!structured.facts().isEmpty()) {
            itemPersistenceService.persistFactsAndEvidence(task.getProjectId(), task.getId(), structured.facts());
        }
        if (decision == ScoreParseDecision.PARSE_FAILED) {
            stateService.failTask(taskId, "Evidence Pipeline 未提取到可自动落库的评分项");
            progressService.clearProgress(taskId);
            return;
        }
        // 正式发布门禁：只有"文档级决策 AUTO_APPROVED 且正式候选非空"才允许项目级全量替换
        // score_item 及后续预计得分计算；REVIEW_REQUIRED（部分表通过）的候选只保留在
        // score_parse_fact/score_parse_evidence 供复核按任务读取，绝不 deleteByProjectId
        // 洗掉已有正式评分项与实际打分。STRUCTURE_INCOMPLETE / PARSE_FAILED 在上游
        // markIncomplete / failTask 收口，同样不进入本发布路径。
        if (decision == ScoreParseDecision.AUTO_APPROVED && structured.originalVerified() && !candidates.isEmpty()) {
            updateProgress(taskId, 90, "Evidence Pipeline 评分项落库", stateService, progressService);
            itemPersistenceService.persistItems(task.getProjectId(), task.getId(), candidates);
            updateProgress(taskId, 95, "阶段 1 预计得分计算", stateService, progressService);
            estimatedScoreService.estimateForProject(task.getProjectId());
        } else if (!candidates.isEmpty()) {
            // 部分表通过：本次提取事实/证据/复核候选已由 persistFactsAndEvidence 按任务保存，
            // 复核 UI 按 parseTaskId 读取；正式评分项与预计得分保持原状。
            log.warn("Evidence Pipeline 部分表判定需复核，已保存复核候选事实且保留现有评分项不覆盖: "
                            + "taskId={}, 通过表候选={}, decision={}, fallback={}",
                    taskId, candidates.size(), decision, fallbackReason);
        } else {
            log.warn("Evidence Pipeline 判定需复核，保留现有评分项不覆盖: taskId={}, decision={}, fallback={}",
                    taskId, decision, fallbackReason);
        }
        stateService.markCompleted(taskId);
        progressService.clearProgress(taskId);
    }

    private static Map<String, Object> tableSplitView(TableCompleteness table) {
        var completeness = table.completeness();
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("tableId", table.tableId());
        view.put("sourceTableIds", table.sourceTableIds());
        view.put("declaredTotal", completeness == null ? null : completeness.declaredTotalScore());
        view.put("decision", completeness == null ? "" : completeness.decision().name());
        view.put("fallbackReason", completeness == null ? "" : completeness.fallbackReason());
        return view;
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
}
