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
        var restored = com.xiyu.bid.scoreparse.domain.OriginalRuleRestoration.restore(fact, artifact, validation.facts());
        var status = candidateEligible && !restored.text().isBlank() && violations.isBlank() && validation.autoApprovable()
                ? ScoreFact.ValidationStatus.AUTO_APPROVED : ScoreFact.ValidationStatus.REVIEW_REQUIRED;
        String dimension = com.xiyu.bid.scoreparse.domain.OriginalScoreEvidencePolicy.publishedDimension(
                fact.dimension(), restored.text(), fact.weight());
        // 评审结论：模型改写项名时，正式项名用原文锚点名称（与验证侧同一唯一性判定），
        // 改写名保留在 ScoreSourceMetadata.modelCategory 作诊断。
        var canonicalName = com.xiyu.bid.scoreparse.domain.SourceItemName.recoverFromArtifact(
                artifact, fact.tableId(), fact.sourceCellIds(), fact.quotedText(), fact.weight());
        if (canonicalName.isPresent()) dimension = canonicalName.get();
        return new ScoreFact(factId, fact.tableId(), fact.sourceCellIds(), restored.text(), fact.code(), dimension,
                fact.weight(), new ScoreEvidence(ScoreEvidence.Kind.EXPLICIT_WEIGHT_COLUMN, fact.quotedText(), cellId,
                start, start < 0 ? -1 : start + fact.quotedText().length(), "page=" + fact.pageNumber() + "; scoreType="
                + fact.scoreType() + (violations.isBlank() ? "" : "; validationDiff=" + violations)), ScoreRule.MAX_SCORE,
                parentId, "MULTIMODAL", fact.confidence(), status, fact.childAggregation(), sourceMetadata(fact, artifact, validation, restored));
    }
    private static com.xiyu.bid.scoreparse.domain.ScoreSourceMetadata sourceMetadata(
            MultimodalScoreFact fact, DocumentArtifact artifact, MultimodalScoreFactValidator.ValidationReport validation, com.xiyu.bid.scoreparse.domain.OriginalRuleRestoration.Result restored) {
        var existing = fact.sourceMetadata();
        var diagnostics = new java.util.ArrayList<>(existing.diagnostics());
        if (restored.text().isBlank()) diagnostics.add("FULL_RULE_UNIT_UNRESOLVED");
        var sourceSpans = new java.util.LinkedHashMap<String, com.xiyu.bid.scoreparse.domain.SourceSpan>();
        existing.sourceSpans().forEach(span -> sourceSpans.put(span.sourceId(), span));
        com.xiyu.bid.scoreparse.domain.SourceSpanResolver.resolve(artifact, fact.sourceCellIds())
                .forEach(span -> sourceSpans.putIfAbsent(span.sourceId(), span));
        restored.spans().forEach(span -> sourceSpans.putIfAbsent(span.sourceId(), span));
        String identity=com.xiyu.bid.scoreparse.domain.OriginalScoreEvidencePolicy.modelFactId(fact);
        String locator=fact.tableId()+":"+String.join(",",fact.sourceCellIds());
        String coarse=fact.sectionTitle()+"/"+fact.code();
        boolean unique=validation.facts().stream().filter(f -> (f.sectionTitle()+"/"+f.code()).equals(coarse)
                && (f.tableId()+":"+String.join(",",f.sourceCellIds())).equals(locator)).count()==1;
        validation.violations().stream().filter(v -> v.factId().isBlank() || v.factId().equals(identity)
                || unique && v.factId().equals(coarse) && v.evidence().equals(locator))
                .map(MultimodalScoreFactValidator.Violation::code).forEach(diagnostics::add);
        return new com.xiyu.bid.scoreparse.domain.ScoreSourceMetadata(
                existing.modelRuleText().isBlank() ? fact.ruleText() : existing.modelRuleText(),
                existing.modelQuotedText().isBlank() ? fact.quotedText() : existing.modelQuotedText(),
                java.util.List.copyOf(sourceSpans.values()), diagnostics.stream().distinct().toList(),
                existing.modelCategory().isBlank() ? fact.dimension() : existing.modelCategory(),
                existing.modelScoreType().isBlank() ? fact.scoreType() : existing.modelScoreType(),
                existing.modelSourceClaim().sectionTitle().isBlank()
                        ? com.xiyu.bid.scoreparse.domain.ModelSourceClaim.from(fact) : existing.modelSourceClaim());
    }

}
