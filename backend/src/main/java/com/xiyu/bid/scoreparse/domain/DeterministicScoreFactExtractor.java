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
import java.util.stream.IntStream;

/** Extracts evidence-bound score facts without inventing weights. */
public final class DeterministicScoreFactExtractor {
    public static final String EVALUATION_CRITERIA_METHOD = "STRUCTURED_EVALUATION_CRITERIA";
    private static final Pattern NUMERIC_WEIGHT = Pattern.compile(
            "^[~≈]?\\s*(\\d{1,3}(?:\\.\\d+)?)\\s*(?:分|分值|%)?\\s*$");
    private static final Pattern TITLE_WEIGHT = Pattern.compile(
            "[（(]\\s*(\\d{1,3}(?:\\.\\d+)?)\\s*分\\s*[）)]");
    private static final Pattern AWARD_RULE = Pattern.compile("(?:得|奖励|加)\\s*\\d{1,3}(?:\\.\\d+)?\\s*分");
    private static final Pattern DEDUCTION_RULE = Pattern.compile("(?:扣|减少|扣除)\\s*\\d{1,3}(?:\\.\\d+)?\\s*分");
    private static final String METHOD = "STRUCTURED_TABLE_DETERMINISTIC";

    private DeterministicScoreFactExtractor() {}

    public static List<ScoreFact> extract(CanonicalTableGrid grid, TableColumnMapping mapping) {
        if (grid == null || mapping == null || !grid.isStructurallyValid()) {
            return List.of();
        }
        int startRow = Math.min(Math.max(mapping.headerRows(), 0), grid.rows());
        var header = grid.cellsInRow(0);
        String method = TableColumnMapping.isEvaluationCriteriaHeader(IntStream.range(0, grid.columns())
                .mapToObj(col -> cellAt(header, col).filter(CanonicalCell::isUsableOrigin).map(CanonicalCell::rawText)
                        .orElse("")).toList()) ? EVALUATION_CRITERIA_METHOD : METHOD;

        Map<Integer, ScoreFact> factByRow = new LinkedHashMap<>();
        Map<Integer, ScoreFact> parentFactByOriginRow = new HashMap<>();
        for (int row = startRow; row < grid.rows(); row++) {
            List<CanonicalCell> rowCells = grid.cellsInRow(row).stream()
                    .sorted(Comparator.comparingInt(CanonicalCell::column)).toList();
            if (rowCells.isEmpty()) continue;
            CanonicalCell detailCell = cellAt(rowCells, mapping.detailCol()).orElse(null);
            String detail = detailCell == null ? "" : detailCell.rawText();
            CanonicalCell dimCell = cellAt(rowCells, mapping.dimCol()).orElse(null);
            String rawDimension = dimCell == null ? "" : dimCell.rawText();
            String dimension = ScoreItemTitleExtractor.cleanDimensionLabel(rawDimension);
            String code = cellAt(rowCells, mapping.codeCol()).map(CanonicalCell::rawText).orElse("").trim();
            String sourceText = rowCells.stream().filter(cell -> !cell.isMergedContinue())
                    .map(CanonicalCell::rawText).filter(text -> !text.isBlank())
                    .reduce((left, right) -> left + " | " + right).orElse("");
            if (sourceText.isBlank()) continue;
            if (rowCells.stream().anyMatch(cell -> cell.isUsableOrigin()
                    && cell.column() == 0 && cell.colspan() == grid.columns())
                    && (ScoreTableSummaryPolicy.isReviewerInstruction(sourceText)
                    || (mapping.parentDimCol() == 1 && mapping.dimCol() == 2 && mapping.weightCol() == 3
                        && mapping.detailCol() == 4 && sourceText.matches("(?s)^注[：:]相关承诺内容[，,].*")
                        && !sourceText.matches("(?s).*(?:得分|扣分|[0-9]+\\s*分|否决|废标).*")))) continue;
            if (ScoreTableSummaryPolicy.isSummaryRow(code, dimension)
                    || ScoreTableSummaryPolicy.isSummaryRow(rowCells)) {
                continue;
            }

            List<String> sourceCellIds = rowCells.stream()
                    .filter(cell -> !cell.isMergedContinue()).map(CanonicalCell::cellId).toList();
            WeightResolution titleWeight = parseTitleWeight(dimCell);
            WeightResolution explicitWeight = mapping.weightCol() < 0
                    ? new WeightResolution(null, null, false)
                    : parseExplicitWeight(cellAt(rowCells, mapping.weightCol()).orElse(null));
            boolean splitMergedOrigin = dimCell != null && !dimCell.isMergedContinue()
                    && dimCell.rowspan() > 1 && !detail.isBlank()
                    && titleWeight.weight() != null && !titleWeight.ambiguous()
                    && explicitWeight.weight() != null && !explicitWeight.ambiguous()
                    && explicitWeight.weight().compareTo(BigDecimal.ZERO) > 0
                    && hasScoredContinuation(grid, row, dimCell, mapping);
            if (splitMergedOrigin) {
                String leafDim = ScoreItemTitleExtractor.resolveDimension(rawDimension, detail);
                String parentId = factId(grid.tableId(), row, "", dimension, "parent");
                parentFactByOriginRow.put(row, fact(parentId, grid.tableId(), List.of(dimCell.cellId()),
                        dimCell.rawText(), "", dimension, titleWeight, ScoreRule.MAX_SCORE, "",
                        method, ScoreFact.ValidationStatus.REVIEW_REQUIRED));
                factByRow.put(row, fact(factId(grid.tableId(), row, code, leafDim, "leaf"),
                        grid.tableId(), sourceCellIds, sourceText, code, leafDim, explicitWeight,
                        classifyRule(detail), parentId, method, ScoreFact.ValidationStatus.AUTO_APPROVED));
                continue;
            }
            WeightResolution weight = resolveWeight(rowCells, dimCell, detailCell, mapping);
            boolean isDimMergedContinuation = dimCell != null && dimCell.isMergedContinue();
            boolean noCodeOrWeight = code.isBlank()
                    && (weight.weight() == null || weight.weight().signum() == 0);
            if (noCodeOrWeight && dimension.isBlank() && !isDimMergedContinuation) {
                continue;
            }
            // 跨页溢出残行识别：无编号、无有效分值，且无独立细项维度（属于大类跨行单元格延续、大类名称或空维度），
            // 或细则文本以标点/序号小标开头（上一页细则被分页截断后的文本残片），直接跳过
            boolean isOverflowContinuation = noCodeOrWeight
                    && (!detail.isBlank() && (detail.startsWith("；") || detail.startsWith(";")
                    || detail.startsWith(")") || detail.startsWith("）")
                    || detail.matches("^[0-9一二三四五六七八九十]+[、.\\s].*")
                    || (dimCell != null && (dimCell.rowspan() > 1 || isCategoryTitle(dimCell.rawText())))));
            if (isOverflowContinuation) {
                continue;
            }
            String leafDim = ScoreItemTitleExtractor.resolveDimension(rawDimension, detail);
            String factId = factId(grid.tableId(), row, code, leafDim);
            ScoreFact.ValidationStatus status = weight.ambiguous() || weight.weight() == null
                    ? ScoreFact.ValidationStatus.REVIEW_REQUIRED
                    : ScoreFact.ValidationStatus.AUTO_APPROVED;
            factByRow.put(row, fact(factId, grid.tableId(), sourceCellIds, sourceText, code,
                    leafDim, weight, classifyRule(detail), "", method, status));
        }

        Map<Integer, String> dimensionOriginCellIdByRow = new HashMap<>();
        for (MergedRegion region : grid.mergedRegions()) {
            Optional<CanonicalCell> originOpt = grid.findCell(region.startCellId());
            if (originOpt.isEmpty()) continue;
            CanonicalCell origin = originOpt.get();
            if (origin.column() != mapping.dimCol()) {
                continue;
            }
            for (int dr = 1; dr < region.rowspan(); dr++) {
                dimensionOriginCellIdByRow.put(origin.row() + dr, origin.cellId());
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

        List<ScoreFact> resolved = new ArrayList<>(factByRow.size() + parentFactByOriginRow.size());
        for (Map.Entry<Integer, ScoreFact> entry : factByRow.entrySet()) {
            int row = entry.getKey();
            ScoreFact fact = entry.getValue();
            ScoreFact parent = parentFactByOriginRow.get(row);
            if (parent != null) {
                resolved.add(parent);
                resolved.add(fact);
                continue;
            }
            resolved.add(resolveContinuationFact(
                    row, mapping, grid, fact, factByRow,
                    parentFactByOriginRow, dimensionOriginCellIdByRow, originRowByCellId));
        }
        return List.copyOf(resolved);
    }

    private static final Pattern CATEGORY_TITLE_PATTERN = Pattern.compile("(?s).*(?:部分|大类|标包|分包|类别|评审大项|合计|总计|小计).*");
    private static boolean isCategoryTitle(String text) {
        return text != null && !text.isBlank() && CATEGORY_TITLE_PATTERN.matcher(text.trim()).matches();
    }

    private static WeightResolution resolveWeight(
            List<CanonicalCell> rowCells, CanonicalCell dimCell, CanonicalCell detailCell, TableColumnMapping mapping) {
        if (mapping.weightCol() >= 0) {
            WeightResolution explicit = parseExplicitWeight(cellAt(rowCells, mapping.weightCol()).orElse(null));
            if (explicit.weight() != null || explicit.ambiguous()) return explicit;
            if (dimCell != null && (dimCell.rowspan() > 1 || isCategoryTitle(dimCell.rawText()))) {
                return new WeightResolution(null, null, false);
            }
        }
        WeightResolution title = parseTitleWeight(dimCell);
        return (title.weight() != null || title.ambiguous()) ? title : new WeightResolution(null, null, false);
    }

    private static boolean hasScoredContinuation(
            CanonicalTableGrid grid, int originRow, CanonicalCell origin, TableColumnMapping mapping) {
        if (mapping.weightCol() < 0) return false;
        boolean coversDetail = mapping.detailCol() >= 0 && origin.column() <= mapping.detailCol()
                && (origin.column() + origin.colspan()) > mapping.detailCol();
        for (int row = originRow + 1; row < originRow + origin.rowspan(); row++) {
            List<CanonicalCell> cells = grid.cellsInRow(row);
            String detail = cellAt(cells, mapping.detailCol()).map(CanonicalCell::rawText).orElse("");
            WeightResolution weight = parseExplicitWeight(cellAt(cells, mapping.weightCol()).orElse(null));
            boolean hasDetail = coversDetail || !detail.isBlank();
            if (hasDetail && weight.weight() != null && !weight.ambiguous()
                    && weight.weight().compareTo(BigDecimal.ZERO) > 0) {
                return true;
            }
        }
        return false;
    }

    private static WeightResolution parseExplicitWeight(CanonicalCell cell) {
        if (cell == null || cell.rawText().isBlank() || cell.isMergedContinue()) return new WeightResolution(null, null, false);
        Matcher matcher = NUMERIC_WEIGHT.matcher(cell.rawText().trim());
        if (!matcher.matches()) return new WeightResolution(null, null, false);
        return new WeightResolution(new BigDecimal(matcher.group(1)),
                evidence(ScoreEvidence.Kind.EXPLICIT_WEIGHT_COLUMN, cell, matcher.group(1), "explicit weight column"), false);
    }

    private static WeightResolution parseTitleWeight(CanonicalCell cell) {
        if (cell == null || cell.rawText().isBlank() || cell.isMergedContinue()) return new WeightResolution(null, null, false);
        Matcher matcher = TITLE_WEIGHT.matcher(cell.rawText());
        BigDecimal value = null;
        String quoted = "";
        int count = 0;
        while (matcher.find()) {
            count++;
            value = new BigDecimal(matcher.group(1));
            quoted = matcher.group(0);
        }
        if (count == 0) return new WeightResolution(null, null, false);
        if (count > 1) return new WeightResolution(null, null, true);
        return new WeightResolution(value,
                evidence(ScoreEvidence.Kind.DIMENSION_TITLE_MAX_SCORE, cell, quoted, "single title maximum score"), false);
    }

    private static ScoreRule classifyRule(String detail) {
        if (detail == null || detail.isBlank()) return ScoreRule.MAX_SCORE;
        return DEDUCTION_RULE.matcher(detail).find() ? ScoreRule.DEDUCTION
                : (AWARD_RULE.matcher(detail).find() ? ScoreRule.AWARD : ScoreRule.MAX_SCORE);
    }

    private static ScoreEvidence evidence(ScoreEvidence.Kind kind, CanonicalCell cell, String quoted, String explanation) {
        String q = quoted == null ? "" : quoted;
        int offset = q.isBlank() ? -1 : cell.rawText().indexOf(q);
        return new ScoreEvidence(kind, q.replaceAll("\\s+", " ").trim(), cell.cellId(),
                offset, offset < 0 ? -1 : offset + q.length(), explanation);
    }

    private static ScoreFact fact(String factId, String tableId, List<String> sourceCellIds, String sourceText,
                                  String code, String dimension, WeightResolution weight, ScoreRule rule,
                                  String parentFactId, String method, ScoreFact.ValidationStatus status) {
        return new ScoreFact(factId, tableId, sourceCellIds, sourceText, code, dimension,
                weight.weight(), weight.evidence(), rule, parentFactId, method,
                status == ScoreFact.ValidationStatus.AUTO_APPROVED ? 1.0 : 0.0, status);
    }

    private static java.util.Optional<CanonicalCell> cellAt(List<CanonicalCell> cells, int column) {
        return cells.stream().filter(cell -> cell.column() == column).findFirst();
    }

    private static ScoreFact resolveContinuationFact(
            int row, TableColumnMapping mapping, CanonicalTableGrid grid, ScoreFact fact,
            Map<Integer, ScoreFact> factByRow, Map<Integer, ScoreFact> parentFactByOriginRow,
            Map<Integer, String> dimensionOriginCellIdByRow, Map<String, Integer> originRowByCellId) {
        CanonicalCell dimCell = cellAt(grid.cellsInRow(row), mapping.dimCol()).orElse(null);
        if (dimCell == null || !dimCell.isMergedContinue()) return fact;
        String originCellId = dimensionOriginCellIdByRow.get(row);
        Integer originRow = originRowByCellId.get(originCellId);
        if (originRow == null) return fact;
        ScoreFact parent = resolveParentFact(originRow, mapping, grid, factByRow, parentFactByOriginRow);
        // Only the merged cell is shared. The origin row's rule may supply a row-specific title.
        String dim = fact.dimension().isBlank()
                ? grid.findCell(originCellId).map(cell -> ScoreItemTitleExtractor.resolveDimension(cell.rawText(), "")).orElse("")
                : fact.dimension();
        List<String> sourceCellIds = new ArrayList<>(fact.sourceCellIds());
        if (originCellId != null && !sourceCellIds.contains(originCellId)) sourceCellIds.add(originCellId);
        String sourceText = fact.sourceText();
        if (!dim.isBlank() && !sourceText.contains(dim)) sourceText = dim + " | " + sourceText;
        return fact.withParentFactId(parent != null ? parent.factId() : "")
                .withDimension(dim).withSourceCellIdsAndText(sourceCellIds, sourceText);
    }

    private static ScoreFact resolveParentFact(
            int originRow, TableColumnMapping mapping, CanonicalTableGrid grid,
            Map<Integer, ScoreFact> factByRow, Map<Integer, ScoreFact> parentFactByOriginRow) {
        ScoreFact explicitParent = parentFactByOriginRow.get(originRow);
        if (explicitParent != null) return explicitParent;
        ScoreFact originFact = factByRow.get(originRow);
        if (originFact == null || originFact.weight() == null || originFact.weight().compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        CanonicalCell dimCell = cellAt(grid.cellsInRow(originRow), mapping.dimCol()).orElse(null);
        if (dimCell == null || dimCell.isMergedContinue() || dimCell.rowspan() <= 1
                || !hasScoredContinuation(grid, originRow, dimCell, mapping)) {
            return null;
        }
        CanonicalCell detailCell = mapping.detailCol() >= 0
                ? cellAt(grid.cellsInRow(originRow), mapping.detailCol()).orElse(null) : null;
        return (detailCell == null || detailCell.isMergedContinue() || detailCell.rawText().isBlank()) ? originFact : null;
    }

    private static String factId(String tableId, int row, String code, String dimension) {
        return "fact-" + Integer.toHexString((tableId + "|" + row + "|" + code + "|" + dimension).toLowerCase(Locale.ROOT).hashCode());
    }

    private static String factId(String tableId, int row, String code, String dimension, String role) {
        return factId(tableId, row, code, dimension + "|" + role);
    }

    private record WeightResolution(BigDecimal weight, ScoreEvidence evidence, boolean ambiguous) {}
}
