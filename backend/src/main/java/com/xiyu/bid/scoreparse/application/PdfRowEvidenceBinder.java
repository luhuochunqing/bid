package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import com.xiyu.bid.scoreparse.domain.OriginalScoringContext;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Binds PDF quotes and item names to one unique physical table row. */
final class PdfRowEvidenceBinder {
    private PdfRowEvidenceBinder() {
    }

    static Optional<MultimodalScoreFact> bindQuote(MultimodalScoreFact fact, DocumentArtifact artifact,
                                                    List<OriginalScoringContext.SchemeEvidence> schemes) {
        if (fact.quotedText().isBlank()) return Optional.empty();
        String quote = TextPrimaryEvidenceBinder.normalizeForEvidence(fact.quotedText());
        List<Span> matches = artifact.tables().stream()
                .filter(table -> "PDF".equalsIgnoreCase(sourceType(table, artifact)))
                .filter(table -> (Boolean.TRUE.equals(table.isScoreTable()) || !OriginalScoringContext.schemeTitleForTable(table).isBlank()))
                .filter(table -> TextPrimaryEvidenceBinder.tableInFactScope(fact, table, schemes))
                .flatMap(table -> rows(table).stream().flatMap(row -> matchingSpans(table, row, quote).stream()))
                .toList();
        if (matches.isEmpty()) return Optional.empty();
        int shortest = matches.stream().mapToInt(span -> span.cells().size()).min().orElse(Integer.MAX_VALUE);
        List<Span> uniqueShortest = matches.stream().filter(span -> span.cells().size() == shortest).toList();
        if (uniqueShortest.size() != 1) return Optional.empty();

        Span span = uniqueShortest.get(0);
        var firstCell = span.cells().get(0);
        Integer page = firstCell.sourcePage() == null ? span.table().sourcePage() : firstCell.sourcePage();
        List<String> cellIds = span.cells().stream()
                .map(DocumentArtifact.CanonicalTable.CanonicalCellDto::cellId).toList();
        return Optional.of(copy(fact, itemName(fact, span), page, span.table().tableId(), cellIds));
    }

    static MultimodalScoreFact addExactQuoteItemName(MultimodalScoreFact fact, MultimodalScoreFact exact,
                                                      DocumentArtifact.CanonicalTable table,
                                                      DocumentArtifact.CanonicalTable.CanonicalCellDto ruleCell,
                                                      DocumentArtifact artifact) {
        String code = TextPrimaryEvidenceBinder.normalizeForEvidence(fact.code());
        if (!"PDF".equalsIgnoreCase(sourceType(table, artifact)) || !(Boolean.TRUE.equals(table.isScoreTable()) || !OriginalScoringContext.schemeTitleForTable(table).isBlank())
                || ruleCell.row() == null || code.isBlank() || code.matches("[\\d.()_-]+")) return exact;
        List<DocumentArtifact.CanonicalTable.CanonicalCellDto> names = table.cells().stream()
                .filter(cell -> ruleCell.row().equals(cell.row()) && !ruleCell.cellId().equals(cell.cellId()))
                .filter(cell -> cell.rawText() != null && TextPrimaryEvidenceBinder.normalizeForEvidence(cell.rawText())
                        .contains(code))
                .toList();
        if (names.size() != 1) return exact;
        var name = names.get(0);
        var selected = new java.util.LinkedHashSet<>(exact.sourceCellIds());
        selected.add(name.cellId());
        List<String> ids = artifact.tables().stream().flatMap(candidate -> candidate.cells().stream())
                .filter(cell -> selected.contains(cell.cellId()))
                .map(DocumentArtifact.CanonicalTable.CanonicalCellDto::cellId).toList();
        return copy(exact, fact.code(), exact.pageNumber(), table.tableId(), ids);
    }

    private static List<List<DocumentArtifact.CanonicalTable.CanonicalCellDto>> rows(
            DocumentArtifact.CanonicalTable table) {
        Map<Integer, List<DocumentArtifact.CanonicalTable.CanonicalCellDto>> grouped = table.cells().stream()
                .filter(cell -> cell.row() != null && !Boolean.TRUE.equals(cell.isMergedContinue()))
                .collect(java.util.stream.Collectors.groupingBy(
                        DocumentArtifact.CanonicalTable.CanonicalCellDto::row, LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));
        return List.copyOf(grouped.values());
    }

    private static List<Span> matchingSpans(DocumentArtifact.CanonicalTable table,
                                            List<DocumentArtifact.CanonicalTable.CanonicalCellDto> row,
                                            String quote) {
        List<DocumentArtifact.CanonicalTable.CanonicalCellDto> cells = row.stream()
                .filter(cell -> cell.rawText() != null && !cell.rawText().isBlank())
                .sorted(java.util.Comparator.comparing(cell -> cell.column() == null ? Integer.MAX_VALUE : cell.column()))
                .toList();
        java.util.ArrayList<Span> matches = new java.util.ArrayList<>();
        for (int start = 0; start < cells.size(); start++) {
            StringBuilder text = new StringBuilder();
            java.util.ArrayList<DocumentArtifact.CanonicalTable.CanonicalCellDto> span = new java.util.ArrayList<>();
            for (int end = start; end < cells.size(); end++) {
                if (end > start && !adjacent(cells.get(end - 1), cells.get(end))) break;
                if (!text.isEmpty()) text.append(' ');
                text.append(cells.get(end).rawText());
                span.add(cells.get(end));
                if (span.size() >= 2
                        && TextPrimaryEvidenceBinder.normalizeForEvidence(text.toString()).contains(quote)) {
                    matches.add(new Span(table, List.copyOf(span)));
                }
            }
        }
        if (matches.isEmpty()) return List.of();
        int shortest = matches.stream().mapToInt(span -> span.cells().size()).min().orElse(Integer.MAX_VALUE);
        return matches.stream().filter(span -> span.cells().size() == shortest).toList();
    }

    private static String itemName(MultimodalScoreFact fact, Span span) {
        String dimension = TextPrimaryEvidenceBinder.normalizeForEvidence(fact.dimension());
        if (!dimension.isBlank() && span.cells().stream().map(DocumentArtifact.CanonicalTable.CanonicalCellDto::rawText)
                .filter(java.util.Objects::nonNull)
                .anyMatch(raw -> TextPrimaryEvidenceBinder.normalizeForEvidence(raw).equals(dimension))) {
            return fact.dimension();
        }
        String code = TextPrimaryEvidenceBinder.normalizeForEvidence(fact.code());
        if (code.isBlank() || code.matches("[\\d.()_-]+")) return fact.dimension();
        List<String> matches = span.cells().stream().map(DocumentArtifact.CanonicalTable.CanonicalCellDto::rawText)
                .filter(java.util.Objects::nonNull)
                .filter(raw -> TextPrimaryEvidenceBinder.normalizeForEvidence(raw).contains(code)).toList();
        return matches.size() == 1 && TextPrimaryEvidenceBinder.normalizeForEvidence(fact.quotedText()).contains(code)
                ? fact.code() : fact.dimension();
    }

    private static boolean adjacent(DocumentArtifact.CanonicalTable.CanonicalCellDto left,
                                    DocumentArtifact.CanonicalTable.CanonicalCellDto right) {
        if (left.column() == null || right.column() == null) return false;
        int leftSpan = Math.max(1, left.colspan() == null ? 1 : left.colspan());
        return right.column() == left.column() + leftSpan;
    }

    private static String sourceType(DocumentArtifact.CanonicalTable table, DocumentArtifact artifact) {
        if (!table.sourceFileType().isBlank()) return table.sourceFileType();
        return artifact.artifact() == null ? "" : artifact.artifact().fileType();
    }

    private static MultimodalScoreFact copy(MultimodalScoreFact fact, String dimension, Integer page,
                                            String tableId, List<String> cellIds) {
        return new MultimodalScoreFact(fact.sectionTitle(), fact.code(), fact.parentCode(), dimension, fact.weight(),
                fact.ruleText(), fact.scoreType(), page, tableId, cellIds, fact.quotedText(), fact.confidence(),
                fact.childAggregation(), fact.schemeId(), fact.scopeStatus(), fact.sourceMetadata());
    }

    private record Span(DocumentArtifact.CanonicalTable table,
                        List<DocumentArtifact.CanonicalTable.CanonicalCellDto> cells) {
    }
}
