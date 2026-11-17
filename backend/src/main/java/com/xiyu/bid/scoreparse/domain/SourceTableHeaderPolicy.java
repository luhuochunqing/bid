// Input: immutable PDF tables from one or more source documents.
// Output: explicit column roles, inherited only within the same scoring scheme and layout.
// Pos: scoreparse/domain — pure source table context.
package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class SourceTableHeaderPolicy {
    private SourceTableHeaderPolicy() {}

    public static Map<Integer, String> headers(DocumentArtifact artifact, CanonicalTable table) {
        var own = explicit(table);
        if (!own.isEmpty() || artifact == null || table.sourcePage() == null) return own;
        var document = artifact.documentFor(table.tableId());
        String type = table.sourceFileType().isBlank() && document != null ? document.fileType() : table.sourceFileType();
        if (!"PDF".equalsIgnoreCase(type)) return Map.of();
        var previous = artifact.tables().stream().filter(t -> t.sourcePage() != null && t.sourcePage() < table.sourcePage()
                        && t.columns() == table.columns() && Boolean.TRUE.equals(t.isScoreTable())
                        && Objects.equals(document, artifact.documentFor(t.tableId()))
                        && !OriginalScoringContext.schemeTitleForTable(table).isBlank()
                        && OriginalScoringContext.schemeTitleForTable(table).equals(OriginalScoringContext.schemeTitleForTable(t))
                        && !explicit(t).isEmpty())
                .max(java.util.Comparator.comparing(CanonicalTable::sourcePage));
        var inherited = previous.map(SourceTableHeaderPolicy::explicit).orElse(Map.of());
        // Headerless continuations are often not flagged isScoreTable by the converter.
        // Require actual numeric data in the inherited score column and a nonempty rule.
        boolean data = table.cells().stream().anyMatch(c -> c.rawText() != null && c.column() != null
                && inherited.getOrDefault(c.column(), "").matches("分值|分数|得分|权重|满分|最高分|评分分值")
                && c.rawText().strip().matches("\\d+(?:\\.\\d+)?") && table.cells().stream().anyMatch(rule ->
                Objects.equals(rule.row(), c.row()) && rule.rawText() != null && !rule.rawText().isBlank()
                        && inherited.getOrDefault(rule.column(), "").matches("(?:评分|评审)(?:标准|细则)|规则|评分要求")));
        return data ? inherited : Map.of();
    }

    public static boolean scoreColumn(DocumentArtifact artifact, CanonicalTable table, Integer column) {
        return column != null && headers(artifact, table).getOrDefault(column, "")
                .matches("分值|分数|得分|权重|满分|最高分|评分分值");
    }

    private static Map<Integer, String> explicit(CanonicalTable table) {
        var roles = new LinkedHashMap<Integer, String>();
        table.cells().stream().filter(c -> Integer.valueOf(0).equals(c.row()) && c.column() != null
                && c.rawText() != null).forEach(c -> roles.put(c.column(), c.rawText().replaceAll("\\s+", "")));
        boolean score = roles.values().stream().anyMatch(h -> h.matches("分值|分数|得分|权重|满分|最高分|评分分值"));
        boolean rule = roles.values().stream().anyMatch(h -> h.matches("(?:评分|评审)(?:标准|细则)|规则|评分要求"));
        return score && rule ? Map.copyOf(roles) : Map.of();
    }
}
