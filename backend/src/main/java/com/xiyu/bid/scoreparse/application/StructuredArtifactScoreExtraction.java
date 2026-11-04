package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.OriginalScoringContext;
import com.xiyu.bid.scoreparse.domain.ScoreFact;
import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import com.xiyu.bid.scoreparse.domain.ScoreParseDecision;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/** Deterministic artifact facts and original-source publication guards, shared by single/multiple files. */
@Slf4j
final class StructuredArtifactScoreExtraction {
    private StructuredArtifactScoreExtraction() {}
    static StructuredTableResult extract(DocumentArtifact artifact, String fileName, String tenderSectionText) {
        return StructuredSourceDiagnostics.attach(extractFacts(artifact, fileName, tenderSectionText),
                artifact.warnings().stream().filter(note -> note.startsWith("BAIDU_OCR_")).toList());
    }

    private static StructuredTableResult extractFacts(DocumentArtifact artifact, String fileName, String tenderSectionText) {
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
                shadow.tableCompletenesses(), originalCheck.proofs(), originalCheck.qualifications(), null);
    }

}
