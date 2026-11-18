// Input: immutable PDF tables from one or more source documents.
// Output: explicit column roles, inherited only within the same scoring scheme and layout.
// Pos: scoreparse/domain — pure source table context.
package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable;
import com.xiyu.bid.scoreparse.domain.grid.TableColumnMapping;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

public final class SourceTableHeaderPolicy {
    private static final Pattern SCORE_HEADER = Pattern.compile("分值|分数|得分|权重|满分|最高分|评分分值");
    private static final Pattern RULE_HEADER = Pattern.compile("(?:评分|评审)(?:标准|细则)|规则|评分要求");
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
                && SCORE_HEADER.matcher(inherited.getOrDefault(c.column(), "")).matches()
                && c.rawText().strip().matches("\\d+(?:\\.\\d+)?") && table.cells().stream().anyMatch(rule ->
                Objects.equals(rule.row(), c.row()) && rule.rawText() != null && !rule.rawText().isBlank()
                        && RULE_HEADER.matcher(inherited.getOrDefault(rule.column(), "")).matches()));
        return data ? inherited : Map.of();
    }

    public static boolean scoreColumn(DocumentArtifact artifact, CanonicalTable table, Integer column) {
        if (column == null) return false;
        // Explicit maxima remain valid even when the rule header uses a different label.
        return table.cells().stream().anyMatch(c -> Integer.valueOf(0).equals(c.row())
                && column.equals(c.column()) && ScoreWeightSourceEvidence.scoreColumn(c, table))
                || SCORE_HEADER.matcher(headers(artifact, table).getOrDefault(column, "")).matches();
    }

    static boolean isEvaluationCriteriaTable(CanonicalTable table) {
        return isEvaluationCriteriaHeader(table.columns(), firstRow(table));
    }

    private static boolean isEvaluationCriteriaHeader(int columns, Map<Integer, String> roles) {
        return TableColumnMapping.isEvaluationCriteriaHeader(
                IntStream.range(0, columns).mapToObj(col -> roles.getOrDefault(col, "")).toList());
    }

    private static Map<Integer, String> firstRow(CanonicalTable table) {
        var roles = new LinkedHashMap<Integer, String>();
        table.cells().stream().filter(c -> Integer.valueOf(0).equals(c.row()) && c.column() != null
                && c.rawText() != null && !Boolean.TRUE.equals(c.isMergedContinue()))
                .forEach(c -> roles.put(c.column(), c.rawText().replaceAll("\\s+", "")));
        return roles;
    }

    private static Map<Integer, String> explicit(CanonicalTable table) {
        var roles = firstRow(table);
        if (isEvaluationCriteriaHeader(table.columns(), roles)) {
            roles.put(3, "分值");
            roles.put(4, "评审细则");
        }
        boolean score = roles.values().stream().anyMatch(h -> SCORE_HEADER.matcher(h).matches());
        boolean rule = roles.values().stream().anyMatch(h -> RULE_HEADER.matcher(h).matches());
        return score && rule ? Map.copyOf(roles) : Map.of();
    }
}
