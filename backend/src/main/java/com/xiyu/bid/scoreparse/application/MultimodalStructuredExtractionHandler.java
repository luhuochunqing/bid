package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFactValidator;
import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import com.xiyu.bid.scoreparse.domain.ScoreCompletenessPolicy;
import com.xiyu.bid.scoreparse.domain.ScoreEvidence;
import com.xiyu.bid.scoreparse.domain.ScoreFact;
import com.xiyu.bid.scoreparse.domain.ScoreParseDecision;
import com.xiyu.bid.scoreparse.domain.ScoreRule;
import com.xiyu.bid.scoreparse.domain.TableCompleteness;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** Multimodal orchestration kept separate from the legacy structured-table handler. */
@Slf4j
final class MultimodalStructuredExtractionHandler {
    private MultimodalStructuredExtractionHandler() {}

    static StructuredTableResult extract(TenderTextSource source, LoadedBidFile loaded,
                                          SidecarDocumentArtifactExtractor artifactExtractor,
                                          MultimodalScoreFactExtractor extractor,
                                          SidecarImageExtractor imageExtractor) {
        if (loaded == null || artifactExtractor == null) {
            return StructuredTableResult.structureIncomplete("MULTIMODAL_INPUT_UNAVAILABLE");
        }
        if (extractor == null) {
            return reviewOnly(loaded, source.fileName(), source.text(), artifactExtractor,
                    "MULTIMODAL_MODEL_UNAVAILABLE");
        }
        try {
            return run(loaded, source.fileName(), source.text(), artifactExtractor, extractor, imageExtractor);
        } catch (RuntimeException | IOException exception) {
            log.warn("多模态评分事实提取失败，进入异常复核且禁止正则自动发布: file={}, msg={}",
                    source.fileName(), exception.getMessage());
            return reviewOnly(loaded, source.fileName(), source.text(), artifactExtractor,
                    "MULTIMODAL_EXTRACTION_FAILED");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return reviewOnly(loaded, source.fileName(), source.text(), artifactExtractor,
                    "MULTIMODAL_EXTRACTION_INTERRUPTED");
        }
    }

    private static StructuredTableResult run(LoadedBidFile loaded, String fileName, String tenderSectionText,
                                              SidecarDocumentArtifactExtractor artifactExtractor,
                                              MultimodalScoreFactExtractor extractor,
                                              SidecarImageExtractor imageExtractor)
            throws IOException, InterruptedException {
        DocumentArtifact artifact = artifact(loaded, fileName, artifactExtractor);
        var scorePages = artifact.tables().stream()
                .filter(table -> table.sourcePage() != null)
                .map(DocumentArtifact.CanonicalTable::sourcePage).collect(java.util.stream.Collectors.toSet());
        var images = imageExtractor == null ? List.<com.xiyu.bid.scoreparse.domain.DocumentImage>of()
                : switch (loaded) {
                    case LoadedBidFile.InMemory inMem -> imageExtractor.renderPages(inMem.content(), fileName, scorePages).images();
                    case LoadedBidFile.OnDisk onDisk -> imageExtractor.renderPages(onDisk.tempFile(), fileName, scorePages).images();
                };
        var extracted = extractor.extract(artifact, images);
        var validation = new MultimodalScoreFactValidator().validate(
                extracted.facts(), artifact, extracted.declaredTotal());
        if (!validation.autoApprovable()) {
            StructuredTableResult deterministicFallback = completeDeterministicFallback(
                    artifact, tenderSectionText, validation);
            if (deterministicFallback != null) {
                return deterministicFallback;
            }
        }
        List<ScoreFact> facts = validation.facts().stream().map(f -> toScoreFact(f, artifact, validation)).toList();
        var decision = validation.autoApprovable() ? ScoreParseDecision.AUTO_APPROVED : ScoreParseDecision.REVIEW_REQUIRED;
        List<ScoreFact> leaves = facts.stream().filter(f -> ScoreFact.isLeaf(f, facts)).toList();
        List<ScoreFact> parents = facts.stream().filter(f -> !ScoreFact.isLeaf(f, facts)).toList();
        var completeness = new ScoreCompletenessPolicy.ScoreCompletenessResult(validation.autoApprovable(), decision,
                validation.autoApprovable() ? null : validation.violations().get(0).code(), validation.declaredTotal(),
                ScoreCompletenessPolicy.DeclaredTotalSource.STRUCTURED_SUMMARY_ROW, validation.leafTotal(),
                leaves.size(), parents.size(), 0, validation.violations().stream()
                .map(v -> v.code() + ": " + v.message() + " expected=" + v.expected() + " actual=" + v.actual()).toList(),
                leaves, parents);
        List<ScoreCandidate> candidates = validation.autoApprovable()
                ? leaves.stream().map(ScoreFact::toCandidate).flatMap(Optional::stream).toList() : List.of();
        var tables = List.of(new TableCompleteness("MULTIMODAL", true, completeness,
                facts.stream().map(ScoreFact::sourceTableId).distinct().toList()));
        log.info("多模态评分事实完成: facts={}, images={}, model={}, decision={}, violations={}",
                facts.size(), extracted.imageCount(), extracted.model(), decision, validation.violations().size());
        return StructuredTableResult.evidencePipeline(facts, candidates, completeness, tables);
    }

    private static StructuredTableResult completeDeterministicFallback(
            DocumentArtifact artifact, String tenderSectionText,
            MultimodalScoreFactValidator.ValidationReport modelValidation) {
        var shadow = ScoreFactShadowPipeline.run(artifact, tenderSectionText);
        var completeness = shadow.completeness();
        if (completeness == null || completeness.decision() != ScoreParseDecision.AUTO_APPROVED) {
            return null;
        }
        List<ScoreFact> facts = shadow.tables().stream()
                .flatMap(table -> table.facts().stream()).toList();
        List<ScoreCandidate> candidates = facts.stream()
                .filter(fact -> ScoreFact.isLeaf(fact, facts))
                .map(ScoreFact::toCandidate)
                .flatMap(Optional::stream)
                .toList();
        if (candidates.isEmpty()) {
            return null;
        }
        log.warn("多模态召回未闭合，确定性表格管道已完整回补: modelFacts={}, fallbackFacts={}, declaredTotal={}, leafTotal={}",
                modelValidation.facts().size(), facts.size(), completeness.declaredTotalScore(),
                completeness.leafWeightSum());
        return StructuredTableResult.evidencePipeline(facts, candidates, completeness,
                shadow.tableCompletenesses());
    }

    private static StructuredTableResult reviewOnly(LoadedBidFile loaded, String fileName, String text,
                                                     SidecarDocumentArtifactExtractor extractor, String reason) {
        try {
            var shadow = ScoreFactShadowPipeline.run(artifact(loaded, fileName, extractor), text);
            List<ScoreFact> facts = shadow.tables().stream().flatMap(table -> table.facts().stream())
                    .map(fact -> fact.withValidationStatus(ScoreFact.ValidationStatus.REVIEW_REQUIRED)).toList();
            List<ScoreFact> leaves = facts.stream().filter(f -> ScoreFact.isLeaf(f, facts)).toList();
            List<ScoreFact> parents = facts.stream().filter(f -> !ScoreFact.isLeaf(f, facts)).toList();
            BigDecimal sum = leaves.stream().map(ScoreFact::weight).filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            var completeness = new ScoreCompletenessPolicy.ScoreCompletenessResult(false,
                    ScoreParseDecision.REVIEW_REQUIRED, reason, BigDecimal.valueOf(100),
                    ScoreCompletenessPolicy.DeclaredTotalSource.DEFAULT_100, sum, leaves.size(), parents.size(),
                    0, List.of(reason), leaves, parents);
            return StructuredTableResult.evidencePipeline(facts, List.of(), completeness,
                    List.of(new TableCompleteness("MULTIMODAL_FALLBACK", false, completeness)));
        } catch (RuntimeException | IOException | InterruptedException failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            return StructuredTableResult.structureIncomplete(reason);
        }
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
                ScoreRule.MAX_SCORE, parentId, "MULTIMODAL", fact.confidence(), status);
    }
}
