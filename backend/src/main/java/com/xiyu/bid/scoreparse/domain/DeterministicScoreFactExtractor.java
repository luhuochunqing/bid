// Input: CanonicalTableGrid and verified column mapping
// Output: deterministic, evidence-bound ScoreFact values
// Pos: scoreparse/domain - pure core, no LLM/framework dependencies (spec 059 Phase 2)
package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.CanonicalCell;
import com.xiyu.bid.scoreparse.domain.grid.CanonicalTableGrid;
import com.xiyu.bid.scoreparse.domain.grid.MergedRegion;
import com.xiyu.bid.scoreparse.domain.grid.TableColumnMapping;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts score facts deterministically from the canonical table structure.
 *
 * <p>The extractor never invents a weight. A fact is marked
 * {@code REVIEW_REQUIRED} when its weight is absent or ambiguous. Numbers in a
 * rule cell such as "优得 4 分" are classified as rule text only; they are not
 * treated as the item's maximum score.
 */
public final class DeterministicScoreFactExtractor {
    private static final Pattern NUMERIC_WEIGHT = Pattern.compile(
            "^[~≈]?\\s*(\\d{1,3}(?:\\.\\d+)?)\\s*(?:分|分值|%)?\\s*$");
    private static final Pattern TITLE_WEIGHT = Pattern.compile(
            "[（(]\\s*(\\d{1,3}(?:\\.\\d+)?)\\s*分\\s*[）)]");
    private static final Pattern AWARD_RULE = Pattern.compile("(?:得|奖励|加)\\s*\\d{1,3}(?:\\.\\d+)?\\s*分");
    private static final Pattern DEDUCTION_RULE = Pattern.compile("(?:扣|减少|扣除)\\s*\\d{1,3}(?:\\.\\d+)?\\s*分");
    private static final String METHOD = "STRUCTURED_TABLE_DETERMINISTIC";

    private DeterministicScoreFactExtractor() {
    }

    /**
     * Extract score facts using a verified table mapping.
     *
     * @param grid canonical grid, including merge continuation cells
     * @param mapping verified semantic column mapping
     * @return facts in physical row order; no LLM or normalization is called
     */
    public static List<ScoreFact> extract(CanonicalTableGrid grid, TableColumnMapping mapping) {
        if (grid == null || mapping == null || !grid.isStructurallyValid()) {
            return List.of();
        }
        int startRow = Math.min(Math.max(mapping.headerRows(), 0), grid.rows());

        // ---- Pass 1: collect facts with empty parentFactId, keyed by row ----
        Map<Integer, ScoreFact> factByRow = new LinkedHashMap<>();
        for (int row = startRow; row < grid.rows(); row++) {
            List<CanonicalCell> rowCells = grid.cellsInRow(row).stream()
                    .sorted(Comparator.comparingInt(CanonicalCell::column))
                    .toList();
            if (rowCells.isEmpty()) {
                continue;
            }
            CanonicalCell detailCell = cellAt(rowCells, mapping.detailCol()).orElse(null);
            String detail = detailCell == null ? "" : detailCell.rawText();
            CanonicalCell dimCell = cellAt(rowCells, mapping.dimCol()).orElse(null);
            String dimension = cleanDimension(dimCell == null ? "" : dimCell.rawText());
            String code = cellAt(rowCells, mapping.codeCol()).map(CanonicalCell::rawText).orElse("").trim();
            String sourceText = rowCells.stream()
                    .filter(cell -> !cell.isMergedContinue())
                    .map(CanonicalCell::rawText)
                    .filter(text -> !text.isBlank())
                    .reduce((left, right) -> left + " | " + right)
                    .orElse("");
            if (sourceText.isBlank()) {
                continue;
            }
            if (isSummaryTotalRow(code, dimension)) {
                continue;
            }

            WeightResolution weight = resolveWeight(rowCells, dimCell, detailCell, mapping);
            ScoreRule rule = classifyRule(detail);
            String factId = factId(grid.tableId(), row, code, dimension);
            ScoreFact.ValidationStatus status = weight.ambiguous() || weight.weight() == null
                    ? ScoreFact.ValidationStatus.REVIEW_REQUIRED
                    : ScoreFact.ValidationStatus.AUTO_APPROVED;
            double confidence = status == ScoreFact.ValidationStatus.AUTO_APPROVED ? 1.0 : 0.0;
            factByRow.put(row, new ScoreFact(
                    factId,
                    grid.tableId(),
                    rowCells.stream().filter(cell -> !cell.isMergedContinue())
                            .map(CanonicalCell::cellId).toList(),
                    sourceText,
                    code,
                    dimension,
                    weight.weight(),
                    weight.evidence(),
                    rule,
                    "",
                    METHOD,
                    confidence,
                    status));
        }

        // ---- Pass 2: resolve parent linkage from the grid's merged regions ----
        // For each merged region's continuation row, remember the origin cellId;
        // for each origin cellId, remember its fact's row. A continuation row
        // references the origin row's factId; an origin row has no parent.
        Map<Integer, String> originCellIdByRow = new HashMap<>();
        for (MergedRegion region : grid.mergedRegions()) {
            Optional<CanonicalCell> originOpt = grid.findCell(region.startCellId());
            if (originOpt.isEmpty()) {
                continue;
            }
            CanonicalCell origin = originOpt.get();
            for (int dr = 1; dr < region.rowspan(); dr++) {
                originCellIdByRow.put(origin.row() + dr, origin.cellId());
            }
        }
        Map<String, Integer> originRowByCellId = new HashMap<>();
        for (Map.Entry<Integer, ScoreFact> entry : factByRow.entrySet()) {
            int row = entry.getKey();
            CanonicalCell dimCell = cellAt(grid.cellsInRow(row), mapping.dimCol()).orElse(null);
            if (dimCell != null && !dimCell.isMergedContinue() && dimCell.rowspan() > 1) {
                originRowByCellId.put(dimCell.cellId(), row);
            }
        }

        List<ScoreFact> resolved = new ArrayList<>(factByRow.size());
        for (Map.Entry<Integer, ScoreFact> entry : factByRow.entrySet()) {
            int row = entry.getKey();
            ScoreFact fact = entry.getValue();
            String parentFactId = resolveParentFactId(
                    row, mapping.dimCol(), grid, factByRow,
                    originCellIdByRow, originRowByCellId);
            resolved.add(fact.withParentFactId(parentFactId));
        }
        return List.copyOf(resolved);
    }

    private static WeightResolution resolveWeight(
            List<CanonicalCell> rowCells,
            CanonicalCell dimCell,
            CanonicalCell detailCell,
            TableColumnMapping mapping) {
        if (mapping.weightCol() >= 0) {
            CanonicalCell explicitCell = cellAt(rowCells, mapping.weightCol()).orElse(null);
            WeightResolution explicit = parseExplicitWeight(explicitCell);
            if (explicit.weight() != null || explicit.ambiguous()) {
                return explicit;
            }
        }
        WeightResolution title = parseTitleWeight(dimCell);
        if (title.weight() != null || title.ambiguous()) {
            return title;
        }
        // A formula may be visible in the detail text, but without a declared
        // maximum it cannot be converted into an automatic weight.
        return new WeightResolution(null, null, false);
    }

    private static WeightResolution parseExplicitWeight(CanonicalCell cell) {
        if (cell == null || cell.rawText().isBlank() || cell.isMergedContinue()) {
            return new WeightResolution(null, null, false);
        }
        Matcher matcher = NUMERIC_WEIGHT.matcher(cell.rawText().trim());
        if (!matcher.matches()) {
            return new WeightResolution(null, null, false);
        }
        BigDecimal value = new BigDecimal(matcher.group(1));
        ScoreEvidence evidence = evidence(
                ScoreEvidence.Kind.EXPLICIT_WEIGHT_COLUMN,
                cell,
                matcher.group(1),
                "explicit weight column");
        return new WeightResolution(value, evidence, false);
    }

    private static WeightResolution parseTitleWeight(CanonicalCell cell) {
        if (cell == null || cell.rawText().isBlank() || cell.isMergedContinue()) {
            return new WeightResolution(null, null, false);
        }
        Matcher matcher = TITLE_WEIGHT.matcher(cell.rawText());
        BigDecimal value = null;
        String quoted = "";
        int count = 0;
        while (matcher.find()) {
            count++;
            value = new BigDecimal(matcher.group(1));
            quoted = matcher.group(0);
        }
        if (count == 0) {
            return new WeightResolution(null, null, false);
        }
        if (count > 1) {
            return new WeightResolution(null, null, true);
        }
        return new WeightResolution(
                value,
                evidence(ScoreEvidence.Kind.DIMENSION_TITLE_MAX_SCORE, cell, quoted, "single title maximum score"),
                false);
    }

    private static ScoreRule classifyRule(String detail) {
        if (detail == null || detail.isBlank()) {
            return ScoreRule.MAX_SCORE;
        }
        Matcher deduction = DEDUCTION_RULE.matcher(detail);
        Matcher award = AWARD_RULE.matcher(detail);
        if (deduction.find()) {
            return ScoreRule.DEDUCTION;
        }
        if (award.find()) {
            return ScoreRule.AWARD;
        }
        return ScoreRule.MAX_SCORE;
    }

    private static ScoreEvidence evidence(ScoreEvidence.Kind kind, CanonicalCell cell,
                                          String quoted, String explanation) {
        int offset = quoted.isBlank() ? -1 : cell.rawText().indexOf(quoted);
        return new ScoreEvidence(kind, quoted, cell.cellId(), offset,
                offset < 0 ? -1 : offset + quoted.length(), explanation);
    }

    private static java.util.Optional<CanonicalCell> cellAt(List<CanonicalCell> cells, int column) {
        return cells.stream().filter(cell -> cell.column() == column).findFirst();
    }

    private static String cleanDimension(String raw) {
        if (raw == null) {
            return "";
        }
        return TITLE_WEIGHT.matcher(raw).replaceAll("")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static boolean isSummaryTotalRow(String code, String dimension) {
        String codeText = normalizeCompact(code);
        String dimensionText = normalizeCompact(dimension);
        return isSummaryLabel(codeText) && hasTotalMarker(dimensionText);
    }

    private static String normalizeCompact(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "");
    }

    private static boolean isSummaryLabel(String text) {
        return text.equals("合计") || text.equals("总计") || text.equals("合计得分") || text.equals("总计得分");
    }

    private static boolean hasTotalMarker(String text) {
        return text.contains("满分") || text.contains("总分") || text.contains("得分");
    }

    /**
     * Resolve parent linkage for a single row given the full grid and the
     * fact map keyed by row.
     * <p>Semantics: a row whose dimCell is a merge continuation references
     * the origin row's factId; a row whose dimCell is a merge origin
     * (rowspan&gt;1) or non-merged has no parent.
     */
    private static String resolveParentFactId(
            int row,
            int dimCol,
            CanonicalTableGrid grid,
            Map<Integer, ScoreFact> factByRow,
            Map<Integer, String> originCellIdByRow,
            Map<String, Integer> originRowByCellId) {
        CanonicalCell dimCell = cellAt(
                grid.cellsInRow(row).stream()
                        .sorted(Comparator.comparingInt(CanonicalCell::column))
                        .toList(),
                dimCol).orElse(null);
        if (dimCell == null || !dimCell.isMergedContinue()) {
            return "";
        }
        String originCellId = originCellIdByRow.get(row);
        Integer originRow = originRowByCellId.get(originCellId);
        if (originRow == null) {
            return "";
        }
        ScoreFact originFact = factByRow.get(originRow);
        return originFact == null ? "" : originFact.factId();
    }

    private static String factId(String tableId, int row, String code, String dimension) {
        return "fact-" + Integer.toHexString((tableId + "|" + row + "|" + code + "|" + dimension)
                .toLowerCase(Locale.ROOT).hashCode());
    }

    private record WeightResolution(BigDecimal weight, ScoreEvidence evidence, boolean ambiguous) {
    }
}
