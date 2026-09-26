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
        String factId = com.xiyu.bid.scoreparse.domain.OriginalScoreEvidencePolicy.modelFactId(fact);
        var parent = com.xiyu.bid.scoreparse.domain.MultimodalFactParentCodeNormalizer
                .resolveParent(fact, validation.facts());
        String parentId = parent == null ? ""
                : com.xiyu.bid.scoreparse.domain.OriginalScoreEvidencePolicy.modelFactId(parent);
        // 逐条问题的 factId 是 modelFactId（来源侧）或空串（文档级）；按 modelFactId 匹配，否则
        // 「来源侧问题挂不到该事实」——本类缺陷（引文不逐字）的诊断信息会整体丢失。
        // 本类自有校验的 of() 问题用 sectionTitle+"/"+code，比事实身份粗（空 code 兄弟共用同一
        // 键），故意不匹配：沿用粗键会把问题挂到错误的那条兄弟行上，正是本次修复要消灭的歧义。
        String violations = validation.violations().stream()
                .filter(v -> v.factId().isBlank() || v.factId().equals(factId))
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
}
