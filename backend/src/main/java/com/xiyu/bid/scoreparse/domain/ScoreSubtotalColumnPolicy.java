package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable.CanonicalCellDto;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Selects the source maximum column; never treats the largest number as column evidence. */
public final class ScoreSubtotalColumnPolicy {
    static final Pattern NUMBER = Pattern.compile("^\\s*(\\d+(?:\\.\\d+)?)\\s*分?\\s*$");
    static final Pattern EXPLICIT_TOTAL = Pattern.compile("(?:总分|满分)\\s*[：:（(为]?\\s*(\\d+(?:\\.\\d+)?)\\s*分");
    private static final Pattern MAXIMUM = Pattern.compile("^(?:最高分(?:值|数)?|最高得分|满分)(?:[（(]分[）)])?$");
    private static final Pattern SCORE = Pattern.compile("^(?:分值|分数|得分|评分分值)(?:[（(]分[）)])?$");
    private ScoreSubtotalColumnPolicy() {}

    public record Selection(int column, boolean unresolved) {
        public OriginalScoreEvidencePolicy.Problem problem(String tableId) {
            return new OriginalScoreEvidencePolicy.Problem("", "subtotal", "TABLE_SUBTOTAL_COLUMN_UNRESOLVED",
                    "one source maximum column", null, tableId);
        }
    }

    public static Selection select(CanonicalTable table, int headerRows, List<CanonicalCellDto> summary) {
        var headers = table.cells().stream().filter(cell -> cell.row() != null && cell.row() == headerRows - 1
                && !Boolean.TRUE.equals(cell.isMergedContinue())).toList();
        int column = select(headers, summary);
        boolean verified = column >= 0 && summary.stream().anyMatch(cell -> java.util.Objects.equals(cell.column(), column)
                && hasValue(cell.rawText()));
        return new Selection(column, !verified);
    }

    private static int select(List<CanonicalCellDto> headers, List<CanonicalCellDto> summary) {
        Set<Integer> maximum = columns(headers, MAXIMUM);
        if (!maximum.isEmpty()) return only(maximum);
        Set<Integer> score = columns(headers, SCORE);
        if (!score.isEmpty()) return only(score);
        // Legacy tables without a detected header may carry one unambiguous summary value.
        // A detected but unknown header is not score-column evidence.
        if (!headers.isEmpty()) return -1;
        Set<Integer> numeric = summary.stream().filter(c -> c.column() != null && c.column() > 0
                        && hasValue(c.rawText()))
                .map(CanonicalCellDto::column).collect(Collectors.toSet());
        return only(numeric);
    }

    private static Set<Integer> columns(List<CanonicalCellDto> headers, Pattern pattern) {
        return headers.stream().filter(c -> c.column() != null && pattern.matcher(normalize(c.rawText())).matches())
                .map(CanonicalCellDto::column).collect(Collectors.toSet());
    }

    private static boolean hasValue(String text) {
        return text != null && (NUMBER.matcher(text).matches() || EXPLICIT_TOTAL.matcher(text).find());
    }

    private static int only(Set<Integer> columns) { return columns.size() == 1 ? columns.iterator().next() : -1; }
    private static String normalize(String text) { return text == null ? "" : text.replaceAll("[\\s*]+", ""); }
}
