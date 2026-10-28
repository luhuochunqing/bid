package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Finds original, numbered scoring rows absent from source-verified facts. */
final class SourceScoreRowCoveragePolicy {
    static final String UNCOVERED = "SOURCE_SCORE_ROW_UNCOVERED";
    private SourceScoreRowCoveragePolicy() { }

    static List<OriginalScoreEvidencePolicy.Problem> uncovered(DocumentArtifact artifact, OriginalScoringContext context,
            List<ScoreEvidenceClaim> claims, Set<String> eligibleClaimIds) {
        if (artifact == null || context == null) return List.of();
        var problems = new ArrayList<OriginalScoreEvidencePolicy.Problem>();
        for (var table : artifact.tables()) {
            if (!Boolean.TRUE.equals(table.isScoreTable()) || !context.owns(table)) continue;
            Map<Integer, String> headers = new LinkedHashMap<>();
            table.cells().stream().filter(c -> Integer.valueOf(0).equals(c.row()) && c.column() != null)
                    .forEach(c -> headers.putIfAbsent(c.column(), compact(c.rawText())));
            Integer codeColumn = column(headers,"序号|编号|条款号|条目");
            Integer nameColumn = column(headers,"评分因素|评分项目|评分项|评审因素|评审项目");
            Integer ruleColumn = column(headers,"评分标准|评审标准|评分细则|评审细则");
            Integer scoreColumn = column(headers,"分值|分数|得分");
            if (codeColumn == null || nameColumn == null || ruleColumn == null || scoreColumn == null) continue;
            Map<Integer, List<DocumentArtifact.CanonicalTable.CanonicalCellDto>> rows = new LinkedHashMap<>();
            table.cells().stream().filter(c -> c.row() != null && c.row() > 0 && !Boolean.TRUE.equals(c.isMergedContinue()))
                    .forEach(c -> rows.computeIfAbsent(c.row(), ignored -> new ArrayList<>()).add(c));
            for (var row : rows.entrySet()) {
                String code = cell(row.getValue(),codeColumn), name = cell(row.getValue(),nameColumn);
                String rule = cell(row.getValue(),ruleColumn), score = cell(row.getValue(),scoreColumn);
                if (!code.matches("[0-9]+(?:[.][0-9]+)*") || name.isBlank() || rule.isBlank()
                        || !score.matches("[0-9]+(?:[.][0-9]+)?")) continue;
                BigDecimal amount = new BigDecimal(score);
                if (amount.signum() == 0 && !name.contains("扣分")) continue;
                Set<String> rowIds = row.getValue().stream().filter(c -> ruleColumn.equals(c.column()))
                        .map(DocumentArtifact.CanonicalTable.CanonicalCellDto::cellId)
                        .collect(java.util.stream.Collectors.toSet());
                boolean covered = claims.stream().filter(c -> eligibleClaimIds.contains(c.id()))
                        .filter(c -> table.tableId().equals(c.tableId()))
                        .anyMatch(c -> c.sourceCellIds() != null && c.sourceCellIds().stream().anyMatch(rowIds::contains));
                if (!covered) problems.add(new OriginalScoreEvidencePolicy.Problem("", "sourceRow", UNCOVERED,
                        "source-verified scoring row", code, table.tableId()+":"+table.tableId()+"-r"+row.getKey()));
            }
        }
        return List.copyOf(problems);
    }
    private static Integer column(Map<Integer,String> headers, String pattern) {
        List<Integer> matching = headers.entrySet().stream().filter(e -> e.getValue().matches(pattern))
                .map(Map.Entry::getKey).toList();
        return matching.size() == 1 ? matching.getFirst() : null;
    }
    private static String cell(List<DocumentArtifact.CanonicalTable.CanonicalCellDto> cells, int column) {
        return cells.stream().filter(c -> Integer.valueOf(column).equals(c.column())).map(c -> compact(c.rawText()))
                .filter(s -> !s.isBlank()).findFirst().orElse("");
    }
    private static String compact(String value) { return EvidenceText.normalize(value).text().replaceAll("\\s+",""); }
}
