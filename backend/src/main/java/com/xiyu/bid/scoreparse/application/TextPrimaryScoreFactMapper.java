package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFactValidator;
import com.xiyu.bid.scoreparse.domain.ScoreEvidence;
import com.xiyu.bid.scoreparse.domain.ScoreFact;
import com.xiyu.bid.scoreparse.domain.ScoreRule;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;

import java.util.stream.Collectors;

final class TextPrimaryScoreFactMapper {
    private TextPrimaryScoreFactMapper() {
    }

    static ScoreFact map(MultimodalScoreFact fact, DocumentArtifact artifact,
                         MultimodalScoreFactValidator.ValidationReport validation) {
        String cellId = fact.sourceCellIds().isEmpty() ? "" : fact.sourceCellIds().get(0);
        String raw = artifact == null ? "" : artifact.tables().stream().flatMap(t -> t.cells().stream())
                .filter(cell -> cell.cellId().equals(cellId)).map(cell -> cell.rawText() == null ? "" : cell.rawText())
                .findFirst().orElse("");
        int start = raw.indexOf(fact.quotedText());
        String factKey = logicalKey(fact, fact.code());
        String factId = com.xiyu.bid.scoreparse.domain.OriginalScoreEvidencePolicy.modelFactId(fact);
        var parent = com.xiyu.bid.scoreparse.domain.MultimodalFactParentCodeNormalizer
                .resolveParent(fact, validation.facts());
        String parentId = parent == null ? ""
                : com.xiyu.bid.scoreparse.domain.OriginalScoreEvidencePolicy.modelFactId(parent);
        String violations = validation.violations().stream()
                .filter(v -> v.factId().isBlank() || v.factId().equals(factKey))
                .map(v -> v.code() + "[field=" + v.field() + ", expected=" + v.expected() + ", actual=" + v.actual()
                        + ", evidence=" + v.evidence() + "]").collect(Collectors.joining("; "));
        boolean candidateEligible = validation.sourceVerification().qualifications().stream()
                .anyMatch(qualification -> factId.equals(qualification.factId())
                        && qualification.candidateEligible());
        var status = candidateEligible && violations.isBlank() && validation.autoApprovable()
                ? ScoreFact.ValidationStatus.AUTO_APPROVED : ScoreFact.ValidationStatus.REVIEW_REQUIRED;
        return new ScoreFact(factId, fact.tableId(), fact.sourceCellIds(), fact.ruleText(), fact.code(), fact.dimension(),
                fact.weight(), new ScoreEvidence(ScoreEvidence.Kind.EXPLICIT_WEIGHT_COLUMN, fact.quotedText(), cellId,
                start, start < 0 ? -1 : start + fact.quotedText().length(), "page=" + fact.pageNumber() + "; scoreType="
                + fact.scoreType() + (violations.isBlank() ? "" : "; validationDiff=" + violations)), ScoreRule.MAX_SCORE,
                parentId, "MULTIMODAL", fact.confidence(), status, fact.childAggregation());
    }

    private static String logicalKey(MultimodalScoreFact fact, String code) {
        return fact.schemeId() + "\u0000" + fact.scopeStatus() + "\u0000" + fact.sectionTitle() + "\u0000" + code;
    }
}
