// Input: Canonical score table grid and semantic column mapping
// Output: immutable, cell-anchored declared-total evidence
// Pos: scoreparse/domain — pure summary-row policy shared by extraction and completeness
package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.CanonicalCell;
import com.xiyu.bid.scoreparse.domain.grid.CanonicalTableGrid;
import com.xiyu.bid.scoreparse.domain.grid.TableColumnMapping;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves declared score totals from canonical score-table summary rows.
 *
 * <p>The canonical grid is authoritative because Markdown conversion may insert
 * {@code <br>} and pipe separators between a summary label and its value. This
 * policy deliberately returns every supported value it sees; conflict handling
 * belongs to {@link ScoreCompletenessPolicy} and must never silently choose a
 * maximum.
 */
public final class ScoreTableSummaryPolicy {

    private static final BigDecimal MAX_REASONABLE_TOTAL = BigDecimal.valueOf(1000);
    private static final Pattern SCORE_VALUE_PATTERN = Pattern.compile(
            "(?<!\\d)(\\d{1,4}(?:\\.\\d+)?)\\s*分");

    private ScoreTableSummaryPolicy() {
    }

    public static List<DeclaredTotalEvidence> extractDeclaredTotals(
            CanonicalTableGrid grid, TableColumnMapping mapping) {
        if (grid == null || mapping == null || !grid.isStructurallyValid()) {
            return List.of();
        }
        List<DeclaredTotalEvidence> evidence = new ArrayList<>();
        int startRow = Math.min(Math.max(mapping.headerRows(), 0), grid.rows());
        for (int row = startRow; row < grid.rows(); row++) {
            List<CanonicalCell> rowCells = grid.cellsInRow(row).stream()
                    .sorted(Comparator.comparingInt(CanonicalCell::column))
                    .toList();
            String code = textAt(rowCells, mapping.codeCol());
            String dimension = textAt(rowCells, mapping.dimCol());
            if (!isSummaryRow(code, dimension)) {
                continue;
            }
            for (CanonicalCell cell : rowCells) {
                if (!cell.isUsableOrigin()) {
                    continue;
                }
                Matcher matcher = SCORE_VALUE_PATTERN.matcher(cell.rawText());
                while (matcher.find()) {
                    BigDecimal value = parseScore(matcher.group(1));
                    if (value != null) {
                        evidence.add(new DeclaredTotalEvidence(
                                value, grid.tableId(), row, cell.cellId(), matcher.group(0)));
                    }
                }
            }
        }
        return List.copyOf(evidence);
    }

    public static boolean isSummaryRow(String code, String dimension) {
        String codeText = normalizeCompact(code);
        String dimensionText = normalizeCompact(dimension);
        return isSummaryLabel(codeText) && hasTotalMarker(dimensionText);
    }

    private static String textAt(List<CanonicalCell> cells, int column) {
        if (column < 0) {
            return "";
        }
        return cells.stream()
                .filter(cell -> cell.column() == column && !cell.isMergedContinue())
                .map(CanonicalCell::rawText)
                .findFirst()
                .orElse("");
    }

    private static BigDecimal parseScore(String rawValue) {
        try {
            BigDecimal value = new BigDecimal(rawValue).setScale(2, RoundingMode.HALF_UP);
            return value.compareTo(BigDecimal.ZERO) > 0 && value.compareTo(MAX_REASONABLE_TOTAL) <= 0
                    ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String normalizeCompact(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "");
    }

    private static boolean isSummaryLabel(String text) {
        return text.equals("合计") || text.equals("总计")
                || text.equals("合计得分") || text.equals("总计得分");
    }

    private static boolean hasTotalMarker(String text) {
        return text.contains("满分") || text.contains("总分") || text.contains("得分");
    }

    public record DeclaredTotalEvidence(
            BigDecimal value,
            String tableId,
            int row,
            String sourceCellId,
            String quotedText
    ) {
        public DeclaredTotalEvidence {
            tableId = tableId == null ? "" : tableId;
            sourceCellId = sourceCellId == null ? "" : sourceCellId;
            quotedText = quotedText == null ? "" : quotedText;
        }
    }
}
