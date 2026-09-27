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

    private static final java.util.regex.Pattern CATEGORY_DIMENSION =
            java.util.regex.Pattern.compile("^(?:商务|技术|报价|价格|商务分|技术分|报价分|价格分|商务部分|技术部分|报价部分|价格部分|商务标|技术标|综合|综合部分)$");

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
        List<Span> candidateSpans = uniqueShortest;
        if (candidateSpans.size() > 1 && !fact.sourceCellIds().isEmpty()) {
            List<Span> fullyMatching = candidateSpans.stream()
                    .filter(s -> s.cells().stream().allMatch(c -> fact.sourceCellIds().contains(c.cellId())))
                    .toList();
            if (fullyMatching.size() == 1) {
                candidateSpans = fullyMatching;
            } else {
                List<Span> matchingCited = candidateSpans.stream()
                        .filter(s -> s.cells().stream().anyMatch(c -> fact.sourceCellIds().contains(c.cellId())))
                        .toList();
                if (matchingCited.size() == 1) {
                    candidateSpans = matchingCited;
                }
            }
        }
        if (candidateSpans.size() != 1) return Optional.empty();

        Span span = candidateSpans.get(0);
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
        if (cells.isEmpty()) return List.of();

        for (int k = 1; k <= cells.size(); k++) {
            List<Span> matches = new java.util.ArrayList<>();
            collectCombinations(table, cells, 0, k, new java.util.ArrayList<>(), quote, matches);
            if (!matches.isEmpty()) {
                return matches.stream().distinct().toList();
            }
        }
        return List.of();
    }

    private static void collectCombinations(DocumentArtifact.CanonicalTable table,
                                            List<DocumentArtifact.CanonicalTable.CanonicalCellDto> cells,
                                            int start, int k,
                                            List<DocumentArtifact.CanonicalTable.CanonicalCellDto> current,
                                            String quote,
                                            List<Span> matches) {
        if (current.size() == k) {
            String combined = current.stream()
                    .map(DocumentArtifact.CanonicalTable.CanonicalCellDto::rawText)
                    .collect(java.util.stream.Collectors.joining(" "));
            if (TextPrimaryEvidenceBinder.normalizeForEvidence(combined).contains(quote)) {
                matches.add(new Span(table, List.copyOf(current)));
            }
            return;
        }
        for (int i = start; i <= cells.size() - (k - current.size()); i++) {
            current.add(cells.get(i));
            collectCombinations(table, cells, i + 1, k, current, quote, matches);
            current.remove(current.size() - 1);
        }
    }

    private static String itemName(MultimodalScoreFact fact, Span span) {
        String dimension = TextPrimaryEvidenceBinder.normalizeForEvidence(fact.dimension());
        if (!dimension.isBlank() && !CATEGORY_DIMENSION.matcher(dimension).matches()
                && span.cells().stream().map(DocumentArtifact.CanonicalTable.CanonicalCellDto::rawText)
                .filter(java.util.Objects::nonNull)
                .anyMatch(raw -> TextPrimaryEvidenceBinder.normalizeForEvidence(raw).contains(dimension))) {
            return fact.dimension();
        }
        String code = TextPrimaryEvidenceBinder.normalizeForEvidence(fact.code());
        if (!code.isBlank() && !code.matches("[\\d.()_-]+")) {
            List<String> matches = span.cells().stream().map(DocumentArtifact.CanonicalTable.CanonicalCellDto::rawText)
                    .filter(java.util.Objects::nonNull)
                    .filter(raw -> TextPrimaryEvidenceBinder.normalizeForEvidence(raw).contains(code)).toList();
            if (matches.size() == 1 && TextPrimaryEvidenceBinder.normalizeForEvidence(fact.quotedText()).contains(code)) {
                return fact.code();
            }
        }
        if (CATEGORY_DIMENSION.matcher(dimension).matches() && !span.cells().isEmpty()) {
            for (var cell : span.cells()) {
                String candidate = cleanedName(cell.rawText());
                if (!candidate.isBlank() && candidate.length() <= 30 && !candidate.matches("[\\d.()_-]+")) {
                    return candidate;
                }
            }
        }
        return fact.dimension();
    }

    private static String cleanedName(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("[（(]\\s*\\d+(?:\\.\\d+)?\\s*分\\s*[)）]", "")
                .replaceAll("^[（(]?\\s*\\d+\\s*[)）]?\\s*[.、]?\\s*", "")
                .trim();
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
