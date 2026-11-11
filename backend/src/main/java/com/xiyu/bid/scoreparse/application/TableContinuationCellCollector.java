package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import com.xiyu.bid.scoreparse.domain.ScoreTableSummaryPolicy;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Collects cross-page or cross-row continuation cells where subsequent rows
 * have empty leading columns and extend the current rule text column.
 */
final class TableContinuationCellCollector {

    private static final Pattern NEW_ITEM_HEADER = Pattern.compile(
            "^\\s*(?:[1-9]\\d*[、\\.．]|[(（][1-9]\\d*[)）]|[\\[【][1-9]\\d*[\\]】]|[一二三四五六七八九十]+[、\\.．]|[(（][一二三四五六七八九十]+[)）]|第[一二三四五六七八九十0-9]+[部分章节条款])");

    private TableContinuationCellCollector() {
    }

    static List<DocumentArtifact.CanonicalTable.CanonicalCellDto> collect(
            DocumentArtifact.CanonicalTable table,
            DocumentArtifact.CanonicalTable.CanonicalCellDto initialCell) {
        return collect(table, initialCell, null);
    }

    static List<DocumentArtifact.CanonicalTable.CanonicalCellDto> collect(
            DocumentArtifact.CanonicalTable table,
            DocumentArtifact.CanonicalTable.CanonicalCellDto initialCell,
            MultimodalScoreFact fact) {
        if (table == null || initialCell == null || initialCell.row() == null || initialCell.column() == null) {
            return initialCell == null ? List.of() : List.of(initialCell);
        }
        List<DocumentArtifact.CanonicalTable.CanonicalCellDto> result = new ArrayList<>();
        result.add(initialCell);
        int targetCol = initialCell.column();
        int currentRow = initialCell.row();
        var rowMap = table.cells().stream().filter(c -> c.row() != null)
                .collect(Collectors.groupingBy(DocumentArtifact.CanonicalTable.CanonicalCellDto::row));
        String combinedFactText = fact == null ? "" :
                ((fact.ruleText() == null ? "" : fact.ruleText()) + " " + (fact.quotedText() == null ? "" : fact.quotedText()));
        String cleanFactText = TextPrimaryEvidenceBinder.normalizeForEvidence(combinedFactText).replaceAll("\\s+", "");

        while (true) {
            int nextRow = currentRow + 1;
            var nextRowCells = rowMap.get(nextRow);
            if (nextRowCells == null || nextRowCells.isEmpty()
                    || nextRowCells.stream().anyMatch(c -> ScoreTableSummaryPolicy.isReviewerInstruction(c.rawText()))) {
                break;
            }
            var contCell = nextRowCells.stream().filter(c -> c.column() != null && c.column() == targetCol).findFirst().orElse(null);
            if (contCell == null || contCell.rawText() == null || contCell.rawText().isBlank()) break;
            boolean otherBlank = nextRowCells.stream().filter(c -> c.column() != null && c.column() != targetCol)
                    .allMatch(c -> c.rawText() == null || c.rawText().isBlank() || Boolean.TRUE.equals(c.isMergedContinue()));
            if (!otherBlank) break;
            if (!isContinuationForFact(contCell, cleanFactText)) {
                break;
            }
            result.add(contCell);
            currentRow = nextRow;
        }
        return List.copyOf(result);
    }

    private static boolean isContinuationForFact(DocumentArtifact.CanonicalTable.CanonicalCellDto contCell,
                                                 String cleanFactText) {
        String raw = contCell.rawText() == null ? "" : contCell.rawText();
        String cleanCont = TextPrimaryEvidenceBinder.normalizeForEvidence(raw).replaceAll("\\s+", "");
        if (cleanCont.isBlank()) return false;

        boolean matchesNewHeader = NEW_ITEM_HEADER.matcher(raw.stripLeading()).find();
        if (matchesNewHeader) {
            int sampleLen = Math.min(cleanCont.length(), 8);
            return sampleLen >= 4 && cleanFactText.contains(cleanCont.substring(0, sampleLen));
        }

        return true;
    }
}
