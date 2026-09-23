package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import com.xiyu.bid.scoreparse.domain.MultimodalFactParentCodeNormalizer;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFactValidator;
import com.xiyu.bid.scoreparse.domain.OriginalScoreEvidencePolicy;
import com.xiyu.bid.scoreparse.domain.OriginalScoringContext;
import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import com.xiyu.bid.scoreparse.domain.ScoreCompletenessPolicy;
import com.xiyu.bid.scoreparse.domain.ScoreEvidence;
import com.xiyu.bid.scoreparse.domain.ScoreFact;
import com.xiyu.bid.scoreparse.domain.ScoreParseDecision;
import com.xiyu.bid.scoreparse.domain.ScoreRule;
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
        if (loaded == null || artifactExtractor == null) {
            return StructuredTableResult.structureIncomplete("MULTIMODAL_INPUT_UNAVAILABLE");
        }
        DocumentArtifact artifact = null;
        OriginalScoringContext originalContext = null;
        try {
            artifact = artifact(loaded, source.fileName(), artifactExtractor);
            originalContext = OriginalScoringContext.resolve(artifact, source.text());
            if (extractor == null) return MultimodalReviewFallback.reviewOnly(artifact, originalContext, source.text(),
                    "MULTIMODAL_MODEL_UNAVAILABLE");
            return run(source.fileName(), source.text(), artifact, originalContext, extractor, chunkCompleted);
        } catch (RuntimeException | IOException exception) {
            log.warn("文字评分事实提取失败，进入异常复核且禁止正则自动发布: file={}, msg={}",
                    source.fileName(), exception.getMessage());
            return artifact == null ? StructuredTableResult.structureIncomplete("MULTIMODAL_EXTRACTION_FAILED")
                    : MultimodalReviewFallback.reviewOnly(artifact, originalContext, source.text(), "MULTIMODAL_EXTRACTION_FAILED");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return artifact == null ? StructuredTableResult.structureIncomplete("MULTIMODAL_EXTRACTION_INTERRUPTED")
                    : MultimodalReviewFallback.reviewOnly(artifact, originalContext, source.text(), "MULTIMODAL_EXTRACTION_INTERRUPTED");
        }
    }

    private static StructuredTableResult run(String fileName, String tenderSectionText,
                                              DocumentArtifact artifact, OriginalScoringContext originalContext,
                                              MultimodalScoreFactExtractor extractor, Runnable chunkCompleted) {
        MultimodalScoreFactExtractor.ExtractionResult extracted;
        boolean alreadyRechecked = false;
        try {
            extracted = extractor.extractWithTextAndProgress(artifact, tenderSectionText, chunkCompleted);
        } catch (RuntimeException firstFailure) {
            log.warn("文字证据首次提取失败，尝试独立模型复核: file={}, cause={}", fileName,
                    firstFailure.getClass().getSimpleName());
            extracted = extractor.recheckWithTextAndProgress(artifact, tenderSectionText, chunkCompleted);
            alreadyRechecked = true;
        }
        extracted = normalized(extracted);
        var validator = new MultimodalScoreFactValidator();
        var validation = validator.validate(extracted.facts(), artifact, originalContext, extracted.declaredTotal());
        boolean multiTableModelScopeUnknown = hasMultipleTables(extracted.facts())
                && !hasIndependentTableTotals(originalContext, extracted.facts());
        if (!alreadyRechecked && (!validation.autoApprovable() || multiTableModelScopeUnknown)) {
            try {
                var rechecked = normalized(extractor.recheckWithTextAndProgress(
                        artifact, tenderSectionText, chunkCompleted));
                var recheckValidation = validator.validate(rechecked.facts(), artifact, originalContext,
                        rechecked.declaredTotal());
                boolean recheckScopeUnknown = hasMultipleTables(rechecked.facts())
                        && !hasIndependentTableTotals(originalContext, rechecked.facts());
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
        boolean scoringRelationUncertain = validation.violations().stream().anyMatch(violation ->
                violation.code().startsWith("CHILD_AGGREGATION_")
                        || "PARENT_WEIGHT_MISMATCH".equals(violation.code()));
        if ((!validation.autoApprovable() || multiTableModelScopeUnknown)
                && !scoringRelationUncertain) {
            StructuredTableResult deterministicFallback = MultimodalReviewFallback.completeDeterministic(
                    originalContext, artifact, tenderSectionText, validation);
            if (deterministicFallback != null) {
                return deterministicFallback;
            }
        }
        var finalValidation = validation;
        List<ScoreFact> facts = validation.facts().stream().map(f -> toScoreFact(f, artifact, finalValidation)).toList();
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

    private static ScoreFact toScoreFact(MultimodalScoreFact fact, DocumentArtifact artifact,
                                         MultimodalScoreFactValidator.ValidationReport validation) {
        String cellId = fact.sourceCellIds().isEmpty() ? "" : fact.sourceCellIds().get(0);
        String raw = artifact.tables().stream().flatMap(table -> table.cells().stream())
                .filter(cell -> cell.cellId().equals(cellId)).map(cell -> cell.rawText() == null ? "" : cell.rawText())
                .findFirst().orElse("");
        int start = raw.indexOf(fact.quotedText());
        String factId = fact.sectionTitle() + "/" + fact.code();
        String parentId = fact.parentCode().isBlank() ? "" : fact.sectionTitle() + "/" + fact.parentCode();

        String violations = validation.violations().stream()
                .filter(v -> v.factId().isBlank() || v.factId().equals(factId))
                .map(v -> v.code() + "[field=" + v.field() + ", expected=" + v.expected()
                        + ", actual=" + v.actual() + ", evidence=" + v.evidence() + "]")
                .collect(java.util.stream.Collectors.joining("; "));
        var status = violations.isBlank() && validation.autoApprovable()
                ? ScoreFact.ValidationStatus.AUTO_APPROVED : ScoreFact.ValidationStatus.REVIEW_REQUIRED;
        return new ScoreFact(factId, fact.tableId(), fact.sourceCellIds(), fact.ruleText(), fact.code(),
                fact.dimension(), fact.weight(), new ScoreEvidence(ScoreEvidence.Kind.EXPLICIT_WEIGHT_COLUMN,
                fact.quotedText(), cellId, start, start < 0 ? -1 : start + fact.quotedText().length(),
                "page=" + fact.pageNumber() + "; scoreType=" + fact.scoreType()
                        + (violations.isBlank() ? "" : "; validationDiff=" + violations)),
                ScoreRule.MAX_SCORE, parentId, "MULTIMODAL", fact.confidence(), status, fact.childAggregation());
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
