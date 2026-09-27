package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import com.xiyu.bid.scoreparse.domain.OriginalScoringContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Binds a fact to original cells when one DOCX row spans adjacent physical tables. */
final class LogicalRowEvidenceBinder {
    private static final Pattern SCORE_COLUMN = Pattern.compile("(?:分值|分数|得分)");
    private static final Pattern NAME_COLUMN = Pattern.compile("(?:评分因素|评分项目|评分项|评审因素|评审项目)");
    private static final Pattern ID_COLUMN = Pattern.compile("(?:序号|编号)");

    private LogicalRowEvidenceBinder() { }

    private static String normalize(String value) {
        return com.xiyu.bid.scoreparse.domain.EvidenceText.normalize(value).text();
    }

    static java.util.Optional<MultimodalScoreFact> bind(
            MultimodalScoreFact fact, DocumentArtifact artifact,
            List<OriginalScoringContext.SchemeEvidence> schemes) {
        Map<String, Map<Integer, String>> headers = indexHeaders(artifact);
        String quote = normalize(fact.quotedText());
        if (quote.isBlank()) return java.util.Optional.empty();

        Map<String, List<Object[]>> rows = new LinkedHashMap<>();
        for (var table : artifact.tables()) {
            for (var cell : table.cells()) {
                if (cell.logicalRowId().isBlank()) continue;
                rows.computeIfAbsent(cell.logicalRowId(), ignored -> new java.util.ArrayList<>())
                        .add(new Object[]{table, cell});
            }
        }

        List<List<Object[]>> eligibleRows = rows.values().stream()
                .filter(row -> sameSectionAndScheme(row, fact, schemes)).toList();
        List<MultimodalScoreFact> exactMatches = eligibleRows.stream()
                .filter(row -> normalize(row.stream()
                        .map(pair -> ((DocumentArtifact.CanonicalTable.CanonicalCellDto) pair[1]).rawText())
                        .filter(text -> text != null && !text.isBlank()).reduce("", String::concat)).contains(quote))
                .filter(row -> sourceUnitClosed(row, artifact))
                .map(row -> boundLogicalRow(fact, row, quote))
                .toList();
        if (exactMatches.size() == 1) return exactMatches.stream().findFirst();
        if (!exactMatches.isEmpty()) return java.util.Optional.empty();

        List<MultimodalScoreFact> rebuilt = eligibleRows.stream()
                .filter(row -> hasExactDimensionAndScore(row, fact, headers))
                .filter(row -> sourceUnitClosed(row, artifact))
                .map(row -> rebuildFromOriginalRuleCells(fact, row, headers))
                .filter(java.util.Objects::nonNull).toList();
        return rebuilt.size() == 1 ? rebuilt.stream().findFirst() : java.util.Optional.empty();
    }

    private static boolean sourceUnitClosed(List<Object[]> row, DocumentArtifact artifact) {
        return com.xiyu.bid.scoreparse.domain.LogicalRuleSourceUnit.closed(artifact, row.stream()
                .map(pair -> ((DocumentArtifact.CanonicalTable.CanonicalCellDto) pair[1]).cellId()).toList());
    }

    private static boolean hasExactDimensionAndScore(List<Object[]> row, MultimodalScoreFact fact,
                                                     Map<String, Map<Integer, String>> headers) {
        String dimension = normalize(fact.dimension());
        if (dimension.isBlank() || row.stream().map(pair -> (DocumentArtifact.CanonicalTable.CanonicalCellDto) pair[1])
                .noneMatch(cell -> normalize(cell.rawText()).equals(dimension))) return false;
        if (fact.weight() == null) return false;
        return row.stream().anyMatch(pair -> {
            var table = (DocumentArtifact.CanonicalTable) pair[0];
            var cell = (DocumentArtifact.CanonicalTable.CanonicalCellDto) pair[1];
            return SCORE_COLUMN.matcher(headerFor(table, cell, headers)).find()
                    && exactScore(cell.rawText(), fact.weight());
        });
    }

    private static boolean exactScore(String source, java.math.BigDecimal expected) {
        if (source == null) return false;
        var matcher = java.util.regex.Pattern.compile("^\\s*[（(]?\\s*(\\d+(?:\\.\\d+)?)\\s*(?:分)?\\s*[）)]?\\s*$")
                .matcher(source.replace(',', '，'));
        if (!matcher.matches()) return false;
        try {
            return new java.math.BigDecimal(matcher.group(1)).compareTo(expected) == 0;
        } catch (NumberFormatException invalid) {
            return false;
        }
    }

    private static MultimodalScoreFact rebuildFromOriginalRuleCells(MultimodalScoreFact fact, List<Object[]> row,
                                                                     Map<String, Map<Integer, String>> headers) {
        List<Object[]> ruleCells = row.stream().filter(pair -> {
            var table = (DocumentArtifact.CanonicalTable) pair[0];
            var cell = (DocumentArtifact.CanonicalTable.CanonicalCellDto) pair[1];
            String raw = cell.rawText() == null ? "" : cell.rawText();
            if (raw.isBlank() || normalize(raw).equals(normalize(fact.dimension()))) return false;
            String header = headerFor(table, cell, headers);
            return !ID_COLUMN.matcher(header).find() && !SCORE_COLUMN.matcher(header).find();
        }).toList();
        if (ruleCells.isEmpty()) return null;
        String quote = ruleCells.stream().map(pair -> {
            var cell = (DocumentArtifact.CanonicalTable.CanonicalCellDto) pair[1];
            return cell.rawText() == null ? "" : cell.rawText();
        }).reduce("", String::concat);
        if (normalize(quote).isBlank()) return null;
        var firstTable = (DocumentArtifact.CanonicalTable) ruleCells.get(0)[0];
        var firstCell = (DocumentArtifact.CanonicalTable.CanonicalCellDto) ruleCells.get(0)[1];
        Integer page = firstCell.sourcePage() == null ? firstTable.sourcePage() : firstCell.sourcePage();
        List<String> ids = ruleCells.stream()
                .map(pair -> ((DocumentArtifact.CanonicalTable.CanonicalCellDto) pair[1]).cellId()).toList();
        return new MultimodalScoreFact(fact.sectionTitle(), fact.code(), fact.parentCode(), fact.dimension(),
                fact.weight(), fact.ruleText(), fact.scoreType(), page, firstTable.tableId(), ids, quote,
                fact.confidence(), fact.childAggregation(), fact.schemeId(), fact.scopeStatus());
    }

    private static boolean sameSectionAndScheme(List<Object[]> row, MultimodalScoreFact fact,
                                                 List<OriginalScoringContext.SchemeEvidence> schemes) {
        var firstTable = (DocumentArtifact.CanonicalTable) row.get(0)[0];
        String section = firstTable.sourceSection();
        if (row.stream().map(pair -> (DocumentArtifact.CanonicalTable) pair[0])
                .anyMatch(table -> !section.equals(table.sourceSection()))) return false;

        List<String> schemeIds = row.stream().map(pair -> (DocumentArtifact.CanonicalTable) pair[0])
                .map(table -> schemes.stream().filter(scheme -> scheme.tableIds().contains(table.tableId()))
                        .map(OriginalScoringContext.SchemeEvidence::schemeId).findFirst()
                        .orElseGet(() -> table.sourceSection().isBlank() ? ""
                                : OriginalScoringContext.schemeId(OriginalScoringContext.schemeTitleForTable(table))))
                .distinct().toList();
        if (schemeIds.size() != 1) return false;
        String actualScheme = schemeIds.get(0);
        return actualScheme != null && !actualScheme.isBlank()
                && (fact.schemeId().isBlank() || fact.schemeId().equals(actualScheme));
    }

    private static Map<String, Map<Integer, String>> indexHeaders(DocumentArtifact artifact) {
        if (artifact == null) return Map.of();
        Map<String, Map<Integer, String>> headers = new LinkedHashMap<>();
        for (var table : artifact.tables()) {
            Map<Integer, String> columns = new LinkedHashMap<>();
            table.cells().stream().filter(cell -> Integer.valueOf(0).equals(cell.row()) && cell.column() != null
                    && cell.rawText() != null && !cell.rawText().isBlank())
                    .forEach(cell -> columns.putIfAbsent(cell.column(), cell.rawText()));
            headers.put(table.tableId(), Map.copyOf(columns));
        }
        return Map.copyOf(headers);
    }

    private static String headerFor(DocumentArtifact.CanonicalTable table,
                                    DocumentArtifact.CanonicalTable.CanonicalCellDto cell,
                                    Map<String, Map<Integer, String>> headers) {
        return headers.getOrDefault(table.tableId(), Map.of()).getOrDefault(cell.column(), "");
    }

    private static MultimodalScoreFact boundLogicalRow(MultimodalScoreFact fact, List<Object[]> row, String quote) {
        List<Object[]> nonempty = row.stream()
                .filter(pair -> {
                    var cell = (DocumentArtifact.CanonicalTable.CanonicalCellDto) pair[1];
                    return cell.rawText() != null && !cell.rawText().isBlank();
                }).toList();
        List<Object[]> contributing = nonempty.stream().filter(pair -> {
            String text = normalize(((DocumentArtifact.CanonicalTable.CanonicalCellDto) pair[1]).rawText());
            return text.length() > 1 && quote.contains(text);
        }).toList();
        List<Object[]> citations = contributing.size() > 1 ? contributing : nonempty;
        var firstTable = (DocumentArtifact.CanonicalTable) citations.get(0)[0];
        var firstCell = (DocumentArtifact.CanonicalTable.CanonicalCellDto) citations.get(0)[1];
        Integer page = firstCell.sourcePage() == null ? firstTable.sourcePage() : firstCell.sourcePage();
        List<String> sourceCellIds = citations.stream()
                .map(pair -> ((DocumentArtifact.CanonicalTable.CanonicalCellDto) pair[1]).cellId()).toList();
        return new MultimodalScoreFact(fact.sectionTitle(), fact.code(), fact.parentCode(), fact.dimension(),
                fact.weight(), fact.ruleText(), fact.scoreType(), page, firstTable.tableId(), sourceCellIds,
                TextPrimaryEvidenceBinder.restoredQuote(citations.stream().map(pair ->
                        ((DocumentArtifact.CanonicalTable.CanonicalCellDto) pair[1]).rawText()).reduce("", String::concat),
                        fact.quotedText()), fact.confidence(), fact.childAggregation(), fact.schemeId(), fact.scopeStatus(), fact.sourceMetadata());
    }

}
