package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import com.xiyu.bid.scoreparse.domain.MultimodalFactParentCodeNormalizer;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFactValidator;
import com.xiyu.bid.scoreparse.domain.OriginalScoreEvidencePolicy;
import com.xiyu.bid.scoreparse.domain.OriginalScoringContext;
import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import com.xiyu.bid.scoreparse.domain.ScoreCompletenessPolicy;
import com.xiyu.bid.scoreparse.domain.ScoreFact;
import com.xiyu.bid.scoreparse.domain.ScoreParseDecision;
import com.xiyu.bid.scoreparse.domain.ScoreUnitPolicy;
import com.xiyu.bid.scoreparse.domain.ScoreTableSplitPolicy;
import com.xiyu.bid.scoreparse.domain.ScoreTableSummaryPolicy;
import com.xiyu.bid.scoreparse.domain.TableCompleteness;
import com.xiyu.bid.scoreparse.domain.TableSplitResult;
import com.xiyu.bid.scoreparse.domain.UnknownDeclaredTotalReviewPolicy;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Structured evidence orchestration kept separate from the legacy table handler. */
@Slf4j
final class MultimodalStructuredExtractionHandler {
    private MultimodalStructuredExtractionHandler() {}

    static StructuredTableResult extract(TenderTextSource source, LoadedBidFile loaded,
                                          SidecarDocumentArtifactExtractor artifactExtractor,
                                          MultimodalScoreFactExtractor extractor,
                                          SidecarImageExtractor imageExtractor) {
        return extractWithChunkHeartbeat(source, loaded, artifactExtractor, extractor, () -> {});
    }

    static StructuredTableResult extract(TenderTextSource source, LoadedBidFile loaded,
                                          SidecarDocumentArtifactExtractor artifactExtractor,
                                          MultimodalScoreFactExtractor extractor) {
        return extractWithChunkHeartbeat(source, loaded, artifactExtractor, extractor, () -> {});
    }

    static StructuredTableResult extractWithChunkHeartbeat(TenderTextSource source, LoadedBidFile loaded,
                                          SidecarDocumentArtifactExtractor artifactExtractor,
                                          MultimodalScoreFactExtractor extractor, Runnable chunkCompleted) {
        return extractWithTextModelHeartbeat(source,
                loaded == null ? List.of() : List.of(loaded), List.of(source),
                artifactExtractor, extractor, chunkCompleted);
    }

    static StructuredTableResult extractWithTextModelHeartbeat(TenderTextSource source,
                                          List<LoadedBidFile> loadedFiles,
                                          List<TenderTextSource> sources,
                                          SidecarDocumentArtifactExtractor artifactExtractor,
                                          MultimodalScoreFactExtractor extractor, Runnable chunkCompleted) {
        if (extractor == null) {
            return legacyArtifactOnly(source, loadedFiles.isEmpty() ? null : loadedFiles.get(0), artifactExtractor);
        }
        if (!extractor.supportsTextOnly()) {
            return legacyModelPath(source, loadedFiles.isEmpty() ? null : loadedFiles.get(0), artifactExtractor, extractor, chunkCompleted);
        }
        var acquired = SourceArtifactAcquisition.acquire(loadedFiles, sources, artifactExtractor);
        try {
            return StructuredSourceDiagnostics.attach(runTextFirst(source.fileName(), source.text(), acquired.artifact(),
                    extractor, chunkCompleted), acquired.diagnostics());
        } catch (RuntimeException exception) {
            log.warn("文字评分事实提取失败: file={}, cause={}", source.fileName(), exception.getClass().getSimpleName());
            return StructuredSourceDiagnostics.attach(StructuredTableResult.structureIncomplete("MULTIMODAL_EXTRACTION_FAILED"),
                    acquired.diagnostics());
        }
    }

    private static StructuredTableResult runTextFirst(String fileName, String text, DocumentArtifact artifact,
                                                       MultimodalScoreFactExtractor extractor, Runnable chunkCompleted) {
        MultimodalScoreFactExtractor.ExtractionResult extracted;
        boolean rechecked = false;
        List<String> initialRetryDiagnostics = List.of();
        try {
            extracted = artifact == null ? extractor.extractTextAndProgress(text, chunkCompleted)
                    : extractor.extractWithTextAndProgress(artifact, text, chunkCompleted);
        } catch (RuntimeException firstFailure) {
            log.warn("文字证据首次提取失败，尝试一次独立模型复核: file={}, cause={}", fileName,
                    firstFailure.getClass().getSimpleName());
            if (artifact == null) {
                extracted = extractor.recheckTextAndProgress(text, chunkCompleted);
            } else {
                var plan = com.xiyu.bid.scoreparse.domain.SourceRecheckPlan.build(artifact, List.of(), List.of());
                initialRetryDiagnostics = plan.diagnostics();
                extracted = plan.text().isBlank()
                        ? new MultimodalScoreFactExtractor.ExtractionResult(null, List.of(), "", 0)
                        : extractor.recheckWithTextAndProgress(plan.artifact(), plan.text(), chunkCompleted);
                extracted = new MultimodalScoreFactExtractor.ExtractionResult(extracted.declaredTotal(),
                        com.xiyu.bid.scoreparse.domain.SourceRecheckPlan.merge(extracted.facts(), List.of(), List.of(),
                                plan.diagnostics()), extracted.model(), extracted.imageCount());
            }
            rechecked = true;
        }
        OriginalScoringContext context = OriginalScoringContext.resolve(artifact, text);
        return StructuredSourceDiagnostics.attach(run(fileName, text, artifact, context, extractor, chunkCompleted,
                extracted, true, rechecked), initialRetryDiagnostics);
    }

    private static StructuredTableResult legacyArtifactOnly(TenderTextSource source, LoadedBidFile loaded,
                                                             SidecarDocumentArtifactExtractor artifactExtractor) {
        if (loaded == null || artifactExtractor == null) {
            return StructuredTableResult.structureIncomplete("MULTIMODAL_INPUT_UNAVAILABLE");
        }
        try {
            DocumentArtifact artifact = MultimodalExtractionReviewPolicy.artifact(loaded, source.fileName(), artifactExtractor);
            return MultimodalReviewFallback.reviewOnly(artifact, OriginalScoringContext.resolve(artifact, source.text()),
                    source.text(), "MULTIMODAL_MODEL_UNAVAILABLE");
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            return StructuredTableResult.structureIncomplete("MULTIMODAL_EXTRACTION_FAILED");
        }
    }

    static StructuredTableResult legacyModelPath(TenderTextSource source, LoadedBidFile loaded,
                                                          SidecarDocumentArtifactExtractor artifactExtractor,
                                                          MultimodalScoreFactExtractor extractor,
                                                          Runnable chunkCompleted) {
        if (loaded == null || artifactExtractor == null) {
            return StructuredTableResult.structureIncomplete("MULTIMODAL_INPUT_UNAVAILABLE");
        }
        try {
            DocumentArtifact artifact = MultimodalExtractionReviewPolicy.artifact(loaded, source.fileName(), artifactExtractor);
            OriginalScoringContext context = OriginalScoringContext.resolve(artifact, source.text());
            MultimodalScoreFactExtractor.ExtractionResult extracted;
            boolean rechecked = false;
            try {
                extracted = extractor.extractWithTextAndProgress(artifact, source.text(), chunkCompleted);
            } catch (RuntimeException firstFailure) {
                try {
                    extracted = extractor.recheckWithTextAndProgress(artifact, source.text(), chunkCompleted);
                    rechecked = true;
                } catch (RuntimeException recheckFailure) {
                    return MultimodalReviewFallback.reviewOnly(artifact, context, source.text(),
                            "MULTIMODAL_EXTRACTION_FAILED");
                }
            }
            return run(source.fileName(), source.text(), artifact, context, extractor, chunkCompleted,
                    extracted, false, rechecked);
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            return StructuredTableResult.structureIncomplete("MULTIMODAL_EXTRACTION_FAILED");
        }
    }

    private static StructuredTableResult run(String fileName, String tenderSectionText,
                                              DocumentArtifact artifact, OriginalScoringContext originalContext,
                                              MultimodalScoreFactExtractor extractor, Runnable chunkCompleted,
                                              MultimodalScoreFactExtractor.ExtractionResult extracted,
                                              boolean textOnly) {
        return run(fileName, tenderSectionText, artifact, originalContext, extractor, chunkCompleted,
                extracted, textOnly, false);
    }

    private static StructuredTableResult run(String fileName, String tenderSectionText,
                                              DocumentArtifact artifact, OriginalScoringContext originalContext,
                                              MultimodalScoreFactExtractor extractor, Runnable chunkCompleted,
                                              MultimodalScoreFactExtractor.ExtractionResult extracted,
                                              boolean textOnly, boolean alreadyRechecked) {
        return run(fileName,tenderSectionText,artifact,originalContext,extractor,chunkCompleted,extracted,textOnly,alreadyRechecked,true);
    }
    static StructuredTableResult run(String fileName,String tenderSectionText,DocumentArtifact artifact,
            OriginalScoringContext originalContext,MultimodalScoreFactExtractor extractor,Runnable chunkCompleted,
            MultimodalScoreFactExtractor.ExtractionResult extracted,boolean textOnly,boolean alreadyRechecked,boolean anchored) {
        extracted = new MultimodalScoreFactExtractor.ExtractionResult(extracted.declaredTotal(), TextPrimaryEvidenceBinder.bind(extracted.facts(), artifact, tenderSectionText), extracted.model(), extracted.imageCount());
        extracted = MultimodalExtractionReviewPolicy.normalized(extracted, originalContext, artifact, tenderSectionText);
        var validator = new MultimodalScoreFactValidator();
        var validation = validator.validate(extracted.facts(), artifact, originalContext, tenderSectionText,
                extracted.declaredTotal());
        List<MultimodalScoreFact> eligibleModelFacts = OriginalScoreEvidencePolicy.eligibleModelFacts(
                extracted.facts(), validation.sourceVerification());
        boolean multiTableModelScopeUnknown = MultimodalExtractionReviewPolicy.hasMultipleTables(eligibleModelFacts)
                && !MultimodalExtractionReviewPolicy.hasIndependentTableTotals(originalContext, eligibleModelFacts)
                && !MultimodalExtractionReviewPolicy.documentTotalCloses(originalContext, validation);
        var retryPlan = artifact == null || !textOnly || !anchored
                || (validation.autoApprovable() && !multiTableModelScopeUnknown)
                ? null : com.xiyu.bid.scoreparse.domain.SourceRecheckPlan.build(
                artifact, extracted.facts(), validation.violations());
        if (retryPlan != null && !retryPlan.diagnostics().isEmpty()) {
            extracted = new MultimodalScoreFactExtractor.ExtractionResult(extracted.declaredTotal(),
                    com.xiyu.bid.scoreparse.domain.SourceRecheckPlan.merge(extracted.facts(), List.of(), List.of(),
                            retryPlan.diagnostics()), extracted.model(), extracted.imageCount());
            validation = validator.validate(extracted.facts(), artifact, originalContext, tenderSectionText, extracted.declaredTotal());
        }
        if (!alreadyRechecked && (!validation.autoApprovable() || multiTableModelScopeUnknown)
                && (retryPlan == null || !retryPlan.text().isBlank())) {
            try {
                var recheckedExtraction = textOnly && artifact == null
                        ? extractor.recheckTextAndProgress(tenderSectionText, chunkCompleted)
                        : extractor.recheckWithTextAndProgress(retryPlan == null ? artifact : retryPlan.artifact(),
                                retryPlan == null ? tenderSectionText : retryPlan.text(), chunkCompleted);
                recheckedExtraction = new MultimodalScoreFactExtractor.ExtractionResult(recheckedExtraction.declaredTotal(), TextPrimaryEvidenceBinder.bind(recheckedExtraction.facts(), artifact, tenderSectionText), recheckedExtraction.model(), recheckedExtraction.imageCount());
                var rechecked = MultimodalExtractionReviewPolicy.normalized(recheckedExtraction, originalContext, artifact, tenderSectionText);
                var recheckValidation = validator.validate(rechecked.facts(), artifact, originalContext,
                        tenderSectionText, rechecked.declaredTotal());
                List<MultimodalScoreFact> recheckEligibleFacts = OriginalScoreEvidencePolicy.eligibleModelFacts(
                        rechecked.facts(), recheckValidation.sourceVerification());
                boolean recheckScopeUnknown = MultimodalExtractionReviewPolicy.hasMultipleTables(recheckEligibleFacts)
                        && !MultimodalExtractionReviewPolicy.hasIndependentTableTotals(originalContext, recheckEligibleFacts)
                        && !MultimodalExtractionReviewPolicy.documentTotalCloses(originalContext, recheckValidation);
                if (retryPlan != null) {
                    var retryReport = recheckValidation;
                    List<String> verifiedIds = recheckEligibleFacts.stream()
                            .map(OriginalScoreEvidencePolicy::modelFactId)
                            .filter(id -> retryReport.violations().stream().noneMatch(v -> id.equals(v.factId())))
                            .toList();
                    var merged = retryPlan.merge(extracted.facts(), rechecked.facts(), verifiedIds);
                    extracted = new MultimodalScoreFactExtractor.ExtractionResult(extracted.declaredTotal(), merged,
                            extracted.model(), extracted.imageCount());
                    validation = validator.validate(merged, artifact, originalContext, tenderSectionText, extracted.declaredTotal());
                    eligibleModelFacts = OriginalScoreEvidencePolicy.eligibleModelFacts(merged, validation.sourceVerification());
                    multiTableModelScopeUnknown = MultimodalExtractionReviewPolicy.hasMultipleTables(eligibleModelFacts)
                            && !MultimodalExtractionReviewPolicy.hasIndependentTableTotals(originalContext, eligibleModelFacts)
                            && !MultimodalExtractionReviewPolicy.documentTotalCloses(originalContext, validation);
                } else if (recheckValidation.autoApprovable() && !recheckScopeUnknown) {
                    extracted = rechecked;
                    validation = recheckValidation;
                    eligibleModelFacts = recheckEligibleFacts;
                    multiTableModelScopeUnknown = false;
                }
                if (recheckValidation.autoApprovable() && !multiTableModelScopeUnknown) {
                    log.info("紧凑模型复核通过: file={}, facts={}, declaredTotal={}", fileName,
                            rechecked.facts().size(), rechecked.declaredTotal());
                }
            } catch (RuntimeException recheckFailure) {
                log.warn("紧凑模型复核失败，保留首次提取结果: file={}", fileName, recheckFailure);
            }
        }
        if (!validation.autoApprovable() || multiTableModelScopeUnknown) {
            StructuredTableResult deterministicFallback = artifact == null ? null : MultimodalReviewFallback.completeDeterministic(
                    originalContext, artifact, tenderSectionText, validation);
            if (deterministicFallback != null) {
                return StructuredSourceDiagnostics.attach(anchored
                        ? FallbackCandidateRetention.retain(deterministicFallback, validation, artifact) : deterministicFallback,
                        retryPlan == null ? List.of() : retryPlan.diagnostics());
            }
        }
        var finalValidation = validation;
        List<ScoreFact> facts = validation.facts().stream().map(f -> TextPrimaryScoreFactMapper.map(f, artifact, finalValidation)).toList();
        Set<String> eligibleFactIds = OriginalScoreEvidencePolicy.eligibleModelFacts(validation.facts(),
                validation.sourceVerification()).stream().map(OriginalScoreEvidencePolicy::modelFactId)
                .collect(java.util.stream.Collectors.toSet());
        List<ScoreFact> eligibleScoreFacts = facts.stream().filter(fact -> eligibleFactIds.contains(fact.factId())).toList();
        var decision = validation.autoApprovable() && !multiTableModelScopeUnknown
                ? ScoreParseDecision.AUTO_APPROVED : ScoreParseDecision.REVIEW_REQUIRED;
        List<ScoreFact> leaves = ScoreUnitPolicy.effectiveFacts(eligibleScoreFacts);
        List<ScoreFact> parents = eligibleScoreFacts.stream().filter(f -> !leaves.contains(f)).toList();
        TableSplitResult modelSplit = MultimodalExtractionReviewPolicy.hasMultipleFactTables(eligibleScoreFacts)
                ? MultimodalExtractionReviewPolicy.splitModelFacts(eligibleScoreFacts, originalContext, tenderSectionText) : null;
        var completeness = new ScoreCompletenessPolicy.ScoreCompletenessResult(validation.autoApprovable(), decision,
                validation.autoApprovable() ? null : validation.violations().get(0).code(), validation.originalTotal(),
                "TENDER_TEXT".equals(validation.sourceVerification().total().source())
                        ? ScoreCompletenessPolicy.DeclaredTotalSource.TENDER_TEXT
                        : validation.originalTotal() == null
                        ? ScoreCompletenessPolicy.DeclaredTotalSource.UNKNOWN
                        : ScoreCompletenessPolicy.DeclaredTotalSource.STRUCTURED_SUMMARY_ROW,
                validation.leafTotal(),
                leaves.size(), parents.size(), 0, validation.violations().stream()
                .map(v -> v.code() + "[fact=" + v.factId() + ",field=" + v.field()
                        + ",source=" + v.evidence() + "] " + v.message()
                        + " expected=" + v.expected() + " actual=" + v.actual()).toList(),
                leaves, parents);
        if (modelSplit != null && multiTableModelScopeUnknown) {
            completeness = UnknownDeclaredTotalReviewPolicy.evaluateUnknownDeclaredTotal(eligibleScoreFacts,
                    "MULTI_TABLE_DECLARED_TOTAL_UNKNOWN", validation.violations().stream()
                            .map(v -> v.code() + "[fact=" + v.factId() + ",field=" + v.field()
                                    + ",source=" + v.evidence() + "] " + v.message()).toList());
        }
        List<ScoreCandidate> candidates = validation.autoApprovable() && !multiTableModelScopeUnknown
                && !ScoreUnitPolicy.hasUnknownFactRelation(eligibleScoreFacts)
                ? leaves.stream().map(ScoreFact::toCandidate).flatMap(Optional::stream).toList() : List.of();
        List<TableCompleteness> tables = modelSplit == null
                ? List.of(new TableCompleteness("MULTIMODAL", true, completeness,
                facts.stream().map(ScoreFact::sourceTableId).distinct().toList()))
                : modelSplit.tables();
        log.info("文字评分事实完成: facts={}, sourceTextChars={}, model={}, decision={}, modelTotal={}, originalTotal={}, "
                        + "originalSource={}, leafTotal={}, violations={}",
                facts.size(), tenderSectionText == null ? 0 : tenderSectionText.length(), extracted.model(), decision, validation.declaredTotal(),
                validation.originalTotal(), validation.sourceVerification().total().locator(),
                validation.leafTotal(), validation.violations().size());
        log.info("文字评分事实校验明细: file={}, fallback={}, violations={}",
                fileName, completeness.fallbackReason(), validation.violations().stream()
                        .map(v -> v.code() + "[fact=" + v.factId() + ",field=" + v.field()
                                + ",source=" + v.evidence() + "] " + v.message()
                                + " expected=" + v.expected() + " actual=" + v.actual()).toList());
        var proof = validation.autoApprovable() ? OriginalScoreEvidencePolicy.publicationProof(
                validation.sourceVerification(), validation.leafTotal(), facts, candidates) : null;
        return StructuredSourceDiagnostics.attach(StructuredTableResult.evidencePipeline(facts, candidates, completeness, tables,
                proof == null ? List.of() : List.of(proof), validation.sourceVerification().qualifications(), null),
                retryPlan == null ? List.of() : retryPlan.diagnostics());
    }
}
