package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.LogicalRuleSourceUnit;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import com.xiyu.bid.scoreparse.domain.OriginalScoringContext;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Restores an abbreviated model quotation only from one uniquely matched, closed PDF rule cell. */
final class PdfEllipsisEvidenceBinder {
    private PdfEllipsisEvidenceBinder() { }

    static Optional<MultimodalScoreFact> bind(MultimodalScoreFact fact, DocumentArtifact artifact,
                                               List<OriginalScoringContext.SchemeEvidence> schemes) {
        String[] parts = fact.quotedText().split("(?:\\.{3}|…)", -1);
        if (parts.length != 2) return Optional.empty();
        String first = compact(parts[0]);
        String last = compact(parts[1]);
        if (first.length() < 20 || last.length() < 20) return Optional.empty();
        var matches = new ArrayList<Object[]>();
        for (var table : artifact.tables()) {
            String type = table.sourceFileType().isBlank() && artifact.artifact() != null
                    ? artifact.artifact().fileType() : table.sourceFileType();
            if (!"PDF".equalsIgnoreCase(type) || !Boolean.TRUE.equals(table.isScoreTable())
                    || !TextPrimaryEvidenceBinder.tableInFactScope(fact, table, schemes)) continue;
            for (var cell : table.cells()) {
                String raw = compact(cell.rawText());
                int begin = raw.indexOf(first);
                int end = begin < 0 ? -1 : raw.indexOf(last, begin + first.length());
                if (begin < 0 || end < 0 || raw.indexOf(first, begin + 1) >= 0
                        || raw.indexOf(last, end + 1) >= 0) continue;
                if (cell.row() == null || cell.column() == null || cell.row() == 0) continue;
                boolean ruleColumn = table.cells().stream().anyMatch(header -> Integer.valueOf(0).equals(header.row())
                        && cell.column().equals(header.column()) && header.rawText() != null
                        && compact(header.rawText()).matches("评分标准|评审标准|评分细则|评审细则"));
                if (ruleColumn && LogicalRuleSourceUnit.closed(artifact, List.of(cell.cellId()))) {
                    matches.add(new Object[]{table, cell});
                }
            }
        }
        if (matches.size() != 1) return Optional.empty();
        var table = (DocumentArtifact.CanonicalTable) matches.getFirst()[0];
        var cell = (DocumentArtifact.CanonicalTable.CanonicalCellDto) matches.getFirst()[1];
        if ((!fact.tableId().isBlank() && !fact.tableId().equals(table.tableId()))
                || (!fact.sourceCellIds().isEmpty() && !fact.sourceCellIds().contains(cell.cellId()))) return Optional.empty();
        Integer page = cell.sourcePage() == null ? table.sourcePage() : cell.sourcePage();
        return Optional.of(new MultimodalScoreFact(fact.sectionTitle(), fact.code(), fact.parentCode(),
                fact.dimension(), fact.weight(), fact.ruleText(), fact.scoreType(), page, table.tableId(),
                List.of(cell.cellId()), cell.rawText(), fact.confidence(), fact.childAggregation(),
                fact.schemeId(), fact.scopeStatus(), fact.sourceMetadata()));
    }

    private static String compact(String value) {
        return TextPrimaryEvidenceBinder.normalizeForEvidence(value).replaceAll("\\s+", "");
    }
}
