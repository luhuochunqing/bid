// Input: SidecarTableExtraction（sidecar /extract-tables 结构化表格数据）
// Output: List<ScoreCandidate>（评分项候选池）
// Pos: scoreparse/domain — Phase 1 结构化表格提取通道，纯核心无框架依赖
package com.xiyu.bid.scoreparse.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 结构化表格 → ScoreCandidate 列表。
 * <p>从 sidecar 提取的合并单元格感知表格数据中，识别评分表并提取评分项候选。
 */
public final class StructuredTableCandidateExtractor {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z]{0,2}\\d+([\\.\\(]\\d+[\\)]?)*$");
    private static final Pattern BRACKET_WEIGHT = ScoreParseConstants.BRACKET_WEIGHT_PATTERN;
    private static final Pattern GRADE_SCORE = Pattern.compile("得\\s*(\\d{1,3}(?:\\.\\d+)?)\\s*分");
    private static final Pattern HEADER_ROW = Pattern.compile(
            "评分因素|评审因素|评分项|评审项|序号|编号|条款号|条款内容|编列内容|评审内容|评分标准|分值$");
    private static final Pattern PURE_WEIGHT_PATTERN = Pattern.compile("^(\\d+(?:\\.\\d+)?)\\s*(?:分|%)?$");
    private static final Pattern NON_DIM_TEXT = Pattern.compile("^(?:\\d+(?:\\.\\d+)?(?:分|%)?|最低分|最高分|分值|满分|权重|得分|/|／|-)$");
    private static final Pattern CONTAINS_HAN = Pattern.compile("[\\p{IsHan}]");
    private static final int DIM_MAX_CHARS = 20;
    private static final int MAX_PARENT_DIM_COLS = 3;

    private StructuredTableCandidateExtractor() {
    }

    public static List<ScoreCandidate> extract(SidecarTableExtraction extraction) {
        return extractWithSectionReport(extraction).candidates();
    }

    /**
     * 带章节推导上报的提取：多表独立结构下某表未推导出章节大类时，
     * 其表索引进入 {@code sectionDimMissTables}，由 application 层打日志
     * （纯核心禁日志，miss 在此只做数据上报；单表 null 属混合单表设计，不上报）。
     */
    public static SectionReport extractWithSectionReport(SidecarTableExtraction extraction) {
        if (extraction == null || extraction.tables().isEmpty()) return new SectionReport(List.of(), List.of());
        List<SidecarTableExtraction.TablePayload> scoreTables = filterScoreTables(extraction.tables());
        List<ScoreCandidate> candidates = new ArrayList<>();
        List<Integer> missTables = new ArrayList<>();
        boolean multiTable = scoreTables.size() > 1;
        for (SidecarTableExtraction.TablePayload table : scoreTables) {
            TableResult result = extractFromTable(table);
            candidates.addAll(result.candidates());
            if (multiTable && result.sectionDim() == null) missTables.add(table.tableIndex());
        }
        return new SectionReport(List.copyOf(candidates), List.copyOf(missTables));
    }

    /** 提取报告：全量候选 + 多表场景章节推导缺失表索引（空表不缺失，只记 null 推导）。 */
    public record SectionReport(List<ScoreCandidate> candidates, List<Integer> sectionDimMissTables) {
    }

    private record TableResult(List<ScoreCandidate> candidates, String sectionDim) {
    }

    private static List<SidecarTableExtraction.TablePayload> filterScoreTables(List<SidecarTableExtraction.TablePayload> tables) {
        List<SidecarTableExtraction.TablePayload> marked = tables.stream().filter(SidecarTableExtraction.TablePayload::isScoreTable).toList();
        return !marked.isEmpty() ? marked : tables.stream().filter(t -> t.rows() != null && t.rows().size() >= 3).toList();
    }

    private static TableResult extractFromTable(SidecarTableExtraction.TablePayload table) {
        List<ScoreCandidate> candidates = new ArrayList<>();
        List<List<SidecarTableExtraction.CellPayload>> rows = table.rows();
        if (rows == null || rows.isEmpty()) return new TableResult(candidates, null);

        String sectionDim = ScoreTableSectionPolicy.inferSectionDimension(rows);
        String[] colParentDim = new String[MAX_PARENT_DIM_COLS];
        int[] colRemainingRowspan = new int[MAX_PARENT_DIM_COLS];
        for (int r = 0; r < rows.size(); r++) {
            List<SidecarTableExtraction.CellPayload> row = rows.get(r);
            if (row == null || row.isEmpty()) {
                decrementRemaining(colRemainingRowspan, colParentDim);
                continue;
            }
            int scanLimit = Math.min(row.size(), MAX_PARENT_DIM_COLS);
            for (int i = 0; i < scanLimit; i++) {
                SidecarTableExtraction.CellPayload cell = row.get(i);
                if (!cell.isMergedContinue() && cell.rowspan() > 1) {
                    String text = cleanParentText(cell.text());
                    if (text != null) {
                        colParentDim[i] = text;
                        colRemainingRowspan[i] = cell.rowspan();
                    }
                }
            }
            List<SidecarTableExtraction.CellPayload> visibleCells = visibleCells(row);
            if (visibleCells.isEmpty() || isHeaderRow(visibleCells, r) || isSummaryRow(visibleCells)) {
                decrementRemaining(colRemainingRowspan, colParentDim);
                continue;
            }

            List<String> activeParents = new ArrayList<>();
            for (int i = 0; i < MAX_PARENT_DIM_COLS; i++) {
                if (colRemainingRowspan[i] > 0 && colParentDim[i] != null) {
                    activeParents.add(colParentDim[i]);
                }
            }
            for (String mp : extractMergedParents(row)) {
                if (!activeParents.contains(mp)) activeParents.add(mp);
            }
            decrementRemaining(colRemainingRowspan, colParentDim);

            ScoreCandidate candidate = parseRow(visibleCells, r, activeParents, sectionDim);
            if (candidate != null) candidates.add(candidate);
        }
        return new TableResult(candidates, sectionDim);
    }

    private static boolean isSummaryRow(List<SidecarTableExtraction.CellPayload> cells) {
        if (cells == null || cells.isEmpty()) return false;
        String first = cells.get(0).text().trim();
        return first.startsWith("合计") || first.startsWith("总计") || first.startsWith("汇总")
                || first.equals("总分") || first.equals("小计");
    }

    private static List<String> extractMergedParents(List<SidecarTableExtraction.CellPayload> fullRow) {
        List<String> parents = new ArrayList<>();
        int scanLimit = Math.min(fullRow.size(), MAX_PARENT_DIM_COLS);
        for (int i = 0; i < scanLimit; i++) {
            SidecarTableExtraction.CellPayload cell = fullRow.get(i);
            if (cell.isMergedContinue() && !cell.text().isBlank()) {
                String cleaned = cleanParentText(cell.text());
                if (cleaned != null) parents.add(cleaned);
            }
        }
        return parents;
    }

    private static String cleanParentText(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String stripped = BRACKET_WEIGHT.matcher(raw).replaceAll("").replace("\n", " ").replaceAll("\\s+", " ").trim();
        if (stripped.isEmpty() || NON_DIM_TEXT.matcher(stripped).find() || CODE_PATTERN.matcher(stripped).matches()) {
            return null;
        }
        return stripped;
    }

    private static void decrementRemaining(int[] remaining, String[] dims) {
        for (int i = 0; i < remaining.length; i++) {
            if (remaining[i] > 0 && --remaining[i] == 0) dims[i] = null;
        }
    }

    private static List<SidecarTableExtraction.CellPayload> visibleCells(List<SidecarTableExtraction.CellPayload> row) {
        return row.stream().filter(c -> !c.isMergedContinue()).toList();
    }

    private static boolean isHeaderRow(List<SidecarTableExtraction.CellPayload> cells, int rowIndex) {
        if (rowIndex == 0) return true;
        long count = cells.stream().map(c -> c.text().trim())
                .filter(t -> t.length() <= 10 && HEADER_ROW.matcher(t).find()).count();
        return count >= 2;
    }

    private static ScoreCandidate parseRow(List<SidecarTableExtraction.CellPayload> cells,
            int rowIndex, List<String> activeParents, String sectionDim) {
        List<String> texts = cells.stream().map(SidecarTableExtraction.CellPayload::text).toList();
        String code = extractCode(texts);
        BracketWeightResult bracketResult = extractBracketWeight(texts);

        BigDecimal weight = bracketResult.weight();
        String dim = bracketResult.dimFromCol3();
        if ((dim == null || dim.isBlank()) && activeParents != null && !activeParents.isEmpty()) {
            dim = activeParents.get(activeParents.size() - 1);
        }
        if (dim == null || dim.isBlank()) dim = extractDim(texts);

        String detail = extractDetail(texts, bracketResult.col3Index(), code, dim);
        if (detail.isBlank() || detail.replaceAll("[/／\\-\\s]", "").isEmpty()) return null;

        BigDecimal gradeScore = maxGradeScore(detail);
        if (gradeScore == null) gradeScore = maxGradeScore(String.join(" ", texts));

        boolean isSpanningCell = bracketResult.col3Index() >= 0
                && cells.get(bracketResult.col3Index()).rowspan() > 1;

        if (isSpanningCell && weight != null && gradeScore != null && gradeScore.compareTo(weight) < 0) {
            weight = gradeScore;
        }
        if (weight == null) weight = extractPureWeight(texts, code);
        if (weight == null) weight = gradeScore;
        if (weight == null && ScoreTableSectionPolicy.isPriceFormula(texts)) {
            weight = BigDecimal.ZERO; // 价格公式兜底；放行侧见 ScoreCandidatePostProcessor#isPriceFormulaItem
        }
        if (weight == null) return null;

        String parentDim = null;
        if (activeParents != null && !activeParents.isEmpty()) {
            final String finalDim = dim;
            List<String> outer = activeParents.stream().filter(p -> finalDim == null || !p.equals(finalDim)).toList();
            if (!outer.isEmpty()) parentDim = String.join(" / ", outer);
        }
        if (sectionDim != null && !sectionDim.isBlank()) {
            if (parentDim == null) {
                parentDim = sectionDim; // 独立分表（1359商务/技术/报价），无跨行父维度
            } else if (!parentDim.contains("商务") && !parentDim.contains("技术")
                    && !parentDim.contains("报价") && !parentDim.contains("价格")) {
                parentDim = sectionDim + " / " + parentDim; // 前缀章节大类；含大类关键词时不重复注入
            }
        }

        String sourceText = cells.stream().map(c -> c.text().trim())
                .filter(t -> !t.isEmpty()).reduce((a, b) -> a + " | " + b).orElse("");
        String dimSource = parentDim != null ? "CELL" : "UNKNOWN";

        return new ScoreCandidate(code, dim, detail, weight, null, null,
                sourceText, "table-row-" + rowIndex, "STRUCTURED_TABLE", parentDim, dimSource);
    }

    private static String extractCode(List<String> texts) {
        if (texts.isEmpty()) return "";
        String first = texts.get(0).trim();
        return (first.length() <= 8 && CODE_PATTERN.matcher(first).matches()) ? first : "";
    }

    private static BracketWeightResult extractBracketWeight(List<String> texts) {
        boolean skipFirstColumn = texts.size() >= 3 && CODE_PATTERN.matcher(texts.get(0).trim()).matches();
        for (int idx = Math.min(texts.size() - 1, 3); idx >= 0; idx--) {
            if (idx == 0 && skipFirstColumn) continue;
            String col = texts.get(idx);
            Matcher matcher = BRACKET_WEIGHT.matcher(col);
            if (matcher.find()) {
                BigDecimal weight = new BigDecimal(matcher.group(1));
                String dim = BRACKET_WEIGHT.matcher(col).replaceAll("").replace("\n", " ").replaceAll("\\s+", " ").trim();
                return new BracketWeightResult(weight, dim.isEmpty() ? null : dim, idx);
            }
        }
        return new BracketWeightResult(null, null, -1);
    }

    private static BigDecimal extractPureWeight(List<String> texts, String code) {
        for (int i = texts.size() - 1; i >= 0; i--) {
            String t = texts.get(i).trim();
            if (code != null && !code.isBlank() && t.equals(code)) continue;
            Matcher m = PURE_WEIGHT_PATTERN.matcher(t);
            if (m.matches()) {
                try {
                    return new BigDecimal(m.group(1));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return null;
    }

    private static String extractDetail(List<String> texts, int col3Index, String code, String dim) {
        int start = col3Index >= 0 ? col3Index + 1 : (code != null && !code.isBlank() ? 1 : 0);
        List<String> parts = new ArrayList<>();
        for (int i = start; i < texts.size(); i++) {
            String t = texts.get(i).trim();
            if (t.isEmpty() || NON_DIM_TEXT.matcher(t).matches()) continue;
            if (col3Index < 0 && dim != null && t.equals(dim)) continue;
            parts.add(t);
        }
        if (!parts.isEmpty()) return String.join(" ", parts);

        String best = null;
        int bestLen = 0;
        for (int i = 0; i < texts.size(); i++) {
            String t = texts.get(i).trim();
            if (t.isEmpty() || (i == 0 && CODE_PATTERN.matcher(t).matches())) continue;
            if (t.length() > bestLen) { bestLen = t.length(); best = t; }
        }
        return best != null ? best : "";
    }

    private static String extractDim(List<String> texts) {
        String best = null;
        for (int i = 0; i < texts.size(); i++) {
            String t = texts.get(i).trim();
            if (t.isEmpty() || (i == 0 && CODE_PATTERN.matcher(t).matches()) || GRADE_SCORE.matcher(t).find()) continue;
            if (t.length() <= DIM_MAX_CHARS && CONTAINS_HAN.matcher(t).find()) {
                if (best == null || t.length() > best.length()) best = t;
            }
        }
        if (best != null) {
            best = best.replaceAll("[：:（(]\\s*\\d+(?:\\.\\d+)?\\s*(?:分|%|分值)\\s*[）)]?", "").trim();
        }
        return (best == null || best.isBlank()) ? null : best;
    }

    private static BigDecimal maxGradeScore(String text) {
        Matcher matcher = GRADE_SCORE.matcher(text);
        BigDecimal max = null;
        while (matcher.find()) {
            BigDecimal value = new BigDecimal(matcher.group(1));
            max = max == null || value.compareTo(max) > 0 ? value : max;
        }
        return max;
    }

    private record BracketWeightResult(BigDecimal weight, String dimFromCol3, int col3Index) {
    }
}
