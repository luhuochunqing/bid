// Input: a complete model rule quote and canonical PDF logical rows.
// Output: one original scoring unit only when all text and score agree, allowing punctuation glyph drift.
// Pos: scoreparse/application — source binding helper; never edits source text.
package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.ChildAggregation;
import com.xiyu.bid.scoreparse.domain.EvidenceText;
import com.xiyu.bid.scoreparse.domain.LogicalRuleSourceUnit;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import com.xiyu.bid.scoreparse.domain.OriginalScoringContext;
import com.xiyu.bid.scoreparse.domain.SourceTableHeaderPolicy;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

final class PdfFullRuleQuoteBinder {
    private PdfFullRuleQuoteBinder() {}
    static Optional<MultimodalScoreFact> bind(MultimodalScoreFact fact, DocumentArtifact artifact,
                                             List<OriginalScoringContext.SchemeEvidence> schemes) {
        if (fact.weight() == null || fact.quotedText().isBlank() || fact.quotedText().contains("|")) return Optional.empty();
        var matches = new ArrayList<MultimodalScoreFact>();
        for (var table : artifact.tables()) {
            var meta = artifact.documentFor(table.tableId());
            String type = table.sourceFileType().isBlank() && meta != null ? meta.fileType() : table.sourceFileType();
            if (!"PDF".equalsIgnoreCase(type)) continue;
            var headers = SourceTableHeaderPolicy.headers(artifact, table);
            if (headers.isEmpty() || !TextPrimaryEvidenceBinder.tableInFactScope(fact, table, schemes)
                    || !fact.tableId().isBlank() && !fact.tableId().equals(table.tableId())) continue;
            var rows = new LinkedHashMap<String, List<DocumentArtifact.CanonicalTable.CanonicalCellDto>>();
            for (var cell : table.cells()) if (!cell.logicalRowId().isBlank())
                rows.computeIfAbsent(cell.logicalRowId(), ignored -> new ArrayList<>()).add(cell);
            for (var row : rows.values()) {
                var rules = row.stream().filter(c -> headers.getOrDefault(c.column(), "")
                        .matches("(?:评分|评审)(?:标准|细则)|规则|评分要求") && c.rawText() != null && !c.rawText().isBlank()).toList();
                String raw = rules.stream().map(DocumentArtifact.CanonicalTable.CanonicalCellDto::rawText)
                        .collect(java.util.stream.Collectors.joining("\n"));
                var weights = row.stream().filter(c -> headers.getOrDefault(c.column(), "").matches("分值|分数|得分|权重|满分|最高分|评分分值")
                        && c.rawText() != null && c.rawText().strip().matches("\\d+(?:\\.\\d+)?"))
                        .map(c -> new BigDecimal(c.rawText().strip())).toList();
                var ids = rules.stream().map(DocumentArtifact.CanonicalTable.CanonicalCellDto::cellId).toList();
                if (raw.isBlank() || !compact(raw).equals(compact(fact.quotedText())) || weights.size() != 1
                        || weights.getFirst().compareTo(fact.weight()) != 0
                        || !fact.sourceCellIds().isEmpty() && !ids.containsAll(fact.sourceCellIds())
                        || !LogicalRuleSourceUnit.closed(artifact, ids)) continue;
                matches.add(new MultimodalScoreFact(fact.sectionTitle(), fact.code(), fact.parentCode(), fact.dimension(),
                        fact.weight(), raw, fact.scoreType(), rules.getFirst().sourcePage() == null ? table.sourcePage()
                                : rules.getFirst().sourcePage(), table.tableId(), ids, raw,
                        fact.confidence(), fact.childAggregation(), fact.schemeId(), fact.scopeStatus(), fact.sourceMetadata()));
            }
        }
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }
    static Optional<MultimodalScoreFact> bindLabel(MultimodalScoreFact fact, DocumentArtifact artifact,
                                                  List<OriginalScoringContext.SchemeEvidence> schemes) {
        String quote = normalizeLabel(fact.quotedText());
        var label = java.util.regex.Pattern.compile(".+\\((\\d+(?:\\.\\d+)?)分?\\)").matcher(quote);
        if (fact.weight() == null || !label.matches()
                || new BigDecimal(label.group(1)).compareTo(fact.weight()) != 0) return Optional.empty();
        var matches = new ArrayList<MultimodalScoreFact>();
        for (var table : artifact.tables()) {
            if (!TextPrimaryEvidenceBinder.tableInFactScope(fact, table, schemes)) continue;
            var headers = SourceTableHeaderPolicy.headers(artifact, table);
            for (var cell : table.cells()) {
                if (!headers.getOrDefault(cell.column(), "").matches("评审项目|评分项目|评分项|评分因素|评审因素")
                        || cell.rawText() == null || Boolean.TRUE.equals(cell.isMergedContinue())) continue;
                String raw = normalizeLabel(cell.rawText());
                if (!raw.equals(quote) || (!LogicalRuleSourceUnit.closed(artifact, List.of(cell.cellId()))
                        && fact.childAggregation() != ChildAggregation.SUM)) continue;
                matches.add(new MultimodalScoreFact(fact.sectionTitle(), fact.code(), fact.parentCode(), fact.dimension(),
                        fact.weight(), cell.rawText(), fact.scoreType(), cell.sourcePage() == null ? table.sourcePage()
                                : cell.sourcePage(), table.tableId(), List.of(cell.cellId()),
                        cell.rawText(), fact.confidence(), fact.childAggregation(), fact.schemeId(), fact.scopeStatus(), fact.sourceMetadata()));
            }
        }
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }
    private static String normalizeLabel(String value) {
        return java.text.Normalizer.normalize(EvidenceText.normalize(value).text(), java.text.Normalizer.Form.NFKC)
                .replaceAll("\\s+", "");
    }
    private static String compact(String value) {
        return EvidenceText.normalize(value).text().replaceAll("[:：;；,，]", "");
    }
}
