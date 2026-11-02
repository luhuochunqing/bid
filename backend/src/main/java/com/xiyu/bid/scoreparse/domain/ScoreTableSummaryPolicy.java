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
    private static final Pattern SUMMARY_NUMERIC_CELL_PATTERN = Pattern.compile(
            "^[~≈]?\\s*(\\d{1,4}(?:\\.\\d+)?)\\s*$");

    private ScoreTableSummaryPolicy() {
    }

    /** Table-level reviewer procedure, conservatively retaining any explicit scoring condition. */
    public static boolean isReviewerInstruction(String text) {
        return text != null && text.strip().matches("(?s)^(?:注|说明)[：:].*(?:评委|评标委员会).*(?:平均|独立评分|汇总).*")
                && !text.matches("(?s).*(?:[0-9０-９一二三四五六七八九十]+(?:[.．][0-9０-９]+)?\\s*分|不得分|不计分|扣分|否决|无效|废标).*");
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
            if (!isSummaryRow(code, dimension) && !isSummaryRow(rowCells)) {
                continue;
            }
            BigDecimal largestNumericCell = null;
            for (CanonicalCell cell : rowCells) {
                if (!cell.isUsableOrigin()) {
                    continue;
                }
                Matcher matcher = SCORE_VALUE_PATTERN.matcher(cell.rawText());
                while (matcher.find()) {
                    if (ScoreSubtotalColumnPolicy.isSingleItemMention(cell.rawText(), matcher.start())) {
                        continue;
                    }
                    BigDecimal value = parseScore(matcher.group(1));
                    if (value != null) {
                        evidence.add(new DeclaredTotalEvidence(
                                value, grid.tableId(), row, cell.cellId(), matcher.group(0)));
                    }
                }
                Matcher numericCell = SUMMARY_NUMERIC_CELL_PATTERN.matcher(cell.rawText().trim());
                if (numericCell.matches()) {
                    BigDecimal value = parseScore(numericCell.group(1));
                    if (value != null && (largestNumericCell == null || value.compareTo(largestNumericCell) > 0)) {
                        largestNumericCell = value;
                    }
                }
            }
            if (evidence.isEmpty() && largestNumericCell != null) {
                BigDecimal summaryTotal = largestNumericCell;
                CanonicalCell totalCell = rowCells.stream()
                        .filter(CanonicalCell::isUsableOrigin)
                        .filter(cell -> SUMMARY_NUMERIC_CELL_PATTERN.matcher(cell.rawText().trim()).matches())
                        .filter(cell -> {
                            Matcher matcher = SUMMARY_NUMERIC_CELL_PATTERN.matcher(cell.rawText().trim());
                            if (!matcher.matches()) {
                                return false;
                            }
                            BigDecimal value = parseScore(matcher.group(1));
                            return value != null && value.compareTo(summaryTotal) == 0;
                        })
                        .findFirst()
                        .orElse(null);
                if (totalCell != null) {
                    evidence.add(new DeclaredTotalEvidence(
                            summaryTotal, grid.tableId(), row, totalCell.cellId(), totalCell.rawText().trim()));
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

    /** Recognize summary rows even when merged/shifted cells defeat column mapping. */
    public static boolean isSummaryRow(List<CanonicalCell> rowCells) {
        if (rowCells == null || rowCells.isEmpty()) {
            return false;
        }
        boolean hasSummaryLabel = rowCells.stream()
                .filter(CanonicalCell::isUsableOrigin)
                .map(CanonicalCell::rawText)
                .map(ScoreTableSummaryPolicy::normalizeCompact)
                .anyMatch(ScoreTableSummaryPolicy::isSummaryLabel);
        boolean hasTotalMarker = rowCells.stream()
                .filter(CanonicalCell::isUsableOrigin)
                .map(CanonicalCell::rawText)
                .map(ScoreTableSummaryPolicy::normalizeCompact)
                .anyMatch(ScoreTableSummaryPolicy::hasTotalMarker);
        boolean hasNumericTotal = rowCells.stream()
                .filter(CanonicalCell::isUsableOrigin)
                .map(CanonicalCell::rawText)
                .map(String::trim)
                .anyMatch(text -> SUMMARY_NUMERIC_CELL_PATTERN.matcher(text).matches());
        return hasSummaryLabel && (hasTotalMarker || hasNumericTotal);
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
