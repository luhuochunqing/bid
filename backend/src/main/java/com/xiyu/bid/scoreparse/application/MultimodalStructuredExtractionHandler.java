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
        try {
            return runTextFirst(source.fileName(), source.text(), loadedFiles, sources,
                    artifactExtractor, extractor, chunkCompleted);
        } catch (RuntimeException exception) {
            log.warn("文字评分事实提取失败，进入异常复核且禁止正则自动发布: file={}, msg={}",
                    source.fileName(), exception.getMessage());
            return StructuredTableResult.structureIncomplete("MULTIMODAL_EXTRACTION_FAILED");
        }
    }

    private static StructuredTableResult runTextFirst(String fileName, String text,
                                                       List<LoadedBidFile> loadedFiles,
                                                       List<TenderTextSource> sources,
                                                       SidecarDocumentArtifactExtractor artifactExtractor,
                                                       MultimodalScoreFactExtractor extractor,
                                                       Runnable chunkCompleted) {
        MultimodalScoreFactExtractor.ExtractionResult extracted;
        boolean rechecked = false;
        try {
            extracted = extractor.extractTextAndProgress(text, chunkCompleted);
        } catch (RuntimeException firstFailure) {
            log.warn("文字证据首次提取失败，尝试独立模型复核: file={}, cause={}", fileName,
                    firstFailure.getClass().getSimpleName());
            extracted = extractor.recheckTextAndProgress(text, chunkCompleted);
            rechecked = true;
        }
        DocumentArtifact artifact = null;
        if (!loadedFiles.isEmpty() && artifactExtractor != null) {
            try {
                artifact = TextPrimaryEvidenceBinder.bundle(loadedFiles, sources, artifactExtractor);
            } catch (IOException | InterruptedException exception) {
                if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
                log.warn("结构化证据后校验不可用，保留文字候选复核: file={}, msg={}", fileName, exception.getMessage());
            }
        }
        OriginalScoringContext context = OriginalScoringContext.resolve(artifact, text);
        return run(fileName, text, artifact, context, extractor, chunkCompleted, extracted, true, rechecked);
    }

    private static StructuredTableResult legacyArtifactOnly(TenderTextSource source, LoadedBidFile loaded,
                                                             SidecarDocumentArtifactExtractor artifactExtractor) {
        if (loaded == null || artifactExtractor == null) {
            return StructuredTableResult.structureIncomplete("MULTIMODAL_INPUT_UNAVAILABLE");
        }
        try {
            DocumentArtifact artifact = artifact(loaded, source.fileName(), artifactExtractor);
            return MultimodalReviewFallback.reviewOnly(artifact, OriginalScoringContext.resolve(artifact, source.text()),
                    source.text(), "MULTIMODAL_MODEL_UNAVAILABLE");
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            return StructuredTableResult.structureIncomplete("MULTIMODAL_EXTRACTION_FAILED");
        }
    }

    private static StructuredTableResult legacyModelPath(TenderTextSource source, LoadedBidFile loaded,
                                                          SidecarDocumentArtifactExtractor artifactExtractor,
                                                          MultimodalScoreFactExtractor extractor,
                                                          Runnable chunkCompleted) {
        if (loaded == null || artifactExtractor == null) {
            return StructuredTableResult.structureIncomplete("MULTIMODAL_INPUT_UNAVAILABLE");
        }
        try {
            DocumentArtifact artifact = artifact(loaded, source.fileName(), artifactExtractor);
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
        extracted = normalized(extracted);
        extracted = new MultimodalScoreFactExtractor.ExtractionResult(extracted.declaredTotal(), TextPrimaryEvidenceBinder.bind(extracted.facts(), artifact, tenderSectionText), extracted.model(), extracted.imageCount());
        var validator = new MultimodalScoreFactValidator();
        var validation = validator.validate(extracted.facts(), artifact, originalContext, tenderSectionText,
                extracted.declaredTotal());
        boolean multiTableModelScopeUnknown = hasMultipleTables(extracted.facts())
                && !hasIndependentTableTotals(originalContext, extracted.facts())
                && !documentTotalCloses(originalContext, validation);
        if (!alreadyRechecked && (!validation.autoApprovable() || multiTableModelScopeUnknown)) {
            try {
                var rechecked = normalized(textOnly
                        ? extractor.recheckTextAndProgress(tenderSectionText, chunkCompleted)
                        : extractor.recheckWithTextAndProgress(artifact, tenderSectionText, chunkCompleted));
                rechecked = new MultimodalScoreFactExtractor.ExtractionResult(rechecked.declaredTotal(), TextPrimaryEvidenceBinder.bind(rechecked.facts(), artifact, tenderSectionText), rechecked.model(), rechecked.imageCount());
                var recheckValidation = validator.validate(rechecked.facts(), artifact, originalContext,
                        tenderSectionText, rechecked.declaredTotal());
                boolean recheckScopeUnknown = hasMultipleTables(rechecked.facts())
                        && !hasIndependentTableTotals(originalContext, rechecked.facts())
                        && !documentTotalCloses(originalContext, recheckValidation);
                if (recheckValidation.autoApprovable() && !recheckScopeUnknown) {
                    extracted = rechecked;
                    validation = recheckValidation;
                    multiTableModelScopeUnknown = false;
                }
                if (recheckValidation.autoApprovable() && !multiTableModelScopeUnknown) {
                    log.info("紧凑模型复核通过: file={}, facts={}, declaredTotal={}", fileName,
                            rechecked.facts().size(), rechecked.declaredTotal());
                }
            } catch (RuntimeException recheckFailure) {
                log.warn("紧凑模型复核失败，保留首次提取结果: file={}, cause={}", fileName,
                        recheckFailure.getClass().getSimpleName());
            }
        }
        if (!validation.autoApprovable() || multiTableModelScopeUnknown) {
            StructuredTableResult deterministicFallback = artifact == null ? null : MultimodalReviewFallback.completeDeterministic(
                    originalContext, artifact, tenderSectionText, validation);
            if (deterministicFallback != null) {
                return deterministicFallback;
            }
        }
        var finalValidation = validation;
        List<ScoreFact> facts = validation.facts().stream().map(f -> TextPrimaryScoreFactMapper.map(f, artifact, finalValidation)).toList();
        var decision = validation.autoApprovable() && !multiTableModelScopeUnknown
                ? ScoreParseDecision.AUTO_APPROVED : ScoreParseDecision.REVIEW_REQUIRED;
        List<ScoreFact> leaves = ScoreUnitPolicy.effectiveFacts(facts);
        List<ScoreFact> parents = facts.stream().filter(f -> !leaves.contains(f)).toList();
        TableSplitResult modelSplit = hasMultipleFactTables(facts)
                ? splitModelFacts(facts, originalContext, tenderSectionText) : null;
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
            completeness = UnknownDeclaredTotalReviewPolicy.evaluateUnknownDeclaredTotal(facts,
                    "MULTI_TABLE_DECLARED_TOTAL_UNKNOWN", validation.violations().stream()
                            .map(v -> v.code() + "[fact=" + v.factId() + ",field=" + v.field()
                                    + ",source=" + v.evidence() + "] " + v.message()).toList());
        }
        List<ScoreCandidate> candidates = validation.autoApprovable() && !multiTableModelScopeUnknown
                && !ScoreUnitPolicy.hasUnknownFactRelation(facts)
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
        var proof = validation.autoApprovable() ? OriginalScoreEvidencePolicy.publicationProof(
                validation.sourceVerification(), validation.leafTotal(), facts, candidates) : null;
        return StructuredTableResult.evidencePipeline(facts, candidates, completeness, tables,
                proof == null ? List.of() : List.of(proof));
    }

    private static DocumentArtifact artifact(LoadedBidFile loaded, String fileName,
                                             SidecarDocumentArtifactExtractor extractor)
            throws IOException, InterruptedException {
        return switch (loaded) {
            case LoadedBidFile.InMemory inMem -> extractor.extract(inMem.content(), fileName);
            case LoadedBidFile.OnDisk onDisk -> extractor.extract(onDisk.tempFile(), fileName);
        };
    }

    private static MultimodalScoreFactExtractor.ExtractionResult normalized(
            MultimodalScoreFactExtractor.ExtractionResult extraction) {
        return new MultimodalScoreFactExtractor.ExtractionResult(extraction.declaredTotal(),
                MultimodalFactParentCodeNormalizer.normalize(extraction.facts()), extraction.model(),
                extraction.imageCount());
    }

    private static boolean hasMultipleTables(List<MultimodalScoreFact> facts) {
        return facts != null && facts.stream().map(MultimodalScoreFact::tableId)
                .filter(tableId -> tableId != null && !tableId.isBlank()).distinct().count() > 1;
    }

    private static boolean hasMultipleFactTables(List<ScoreFact> facts) {
        return facts != null && facts.stream().map(ScoreFact::sourceTableId)
                .filter(tableId -> tableId != null && !tableId.isBlank()).distinct().count() > 1;
    }

    private static boolean hasIndependentTableTotals(OriginalScoringContext context,
                                                      List<MultimodalScoreFact> facts) {
        if (context == null || context.subtotals().isEmpty()) {
            return false;
        }
        return facts.stream().map(MultimodalScoreFact::tableId).filter(tableId -> tableId != null && !tableId.isBlank())
                .distinct().allMatch(context.subtotals()::containsKey);
    }

    private static boolean documentTotalCloses(OriginalScoringContext context, MultimodalScoreFactValidator.ValidationReport validation) {
        return context != null && context.total().value() != null && validation != null && validation.leafTotal() != null
                && context.total().value().compareTo(validation.leafTotal()) == 0;
    }

    private static TableSplitResult splitModelFacts(List<ScoreFact> facts, OriginalScoringContext context,
                                                     String tenderSectionText) {
        List<ScoreTableSplitPolicy.ScoreTableInput> inputs = facts.stream()
                .collect(java.util.stream.Collectors.groupingBy(ScoreFact::sourceTableId,
                        java.util.LinkedHashMap::new, java.util.stream.Collectors.toList()))
                .entrySet().stream()
                .map(entry -> new ScoreTableSplitPolicy.ScoreTableInput(entry.getKey(), true, entry.getValue()))
                .toList();
        List<ScoreTableSummaryPolicy.DeclaredTotalEvidence> totals = context == null ? List.of()
                : context.subtotals().entrySet().stream()
                .map(entry -> new ScoreTableSummaryPolicy.DeclaredTotalEvidence(entry.getValue().value(),
                        entry.getKey(), -1, entry.getValue().locator(), entry.getValue().locator()))
                .toList();
        return ScoreTableSplitPolicy.split(inputs, totals, tenderSectionText);
    }
}
