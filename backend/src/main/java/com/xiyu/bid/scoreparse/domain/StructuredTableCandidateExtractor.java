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
 * 列对齐策略复用 {@link ScoreTableRowParser} 的优先级：
 * <ul>
 *   <li>code：首列匹配短编号模式</li>
 *   <li>weight：优先括号分值（"财务状况（10分）" → 10）；兜底"得X分"最大值</li>
 *   <li>dim：列3去括号分值后文本</li>
 *   <li>detail：列3+列4拼接，或最长非编号非分值列</li>
 * </ul>
 */
public final class StructuredTableCandidateExtractor {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z]{0,2}\\d+([\\.\\(]\\d+[\\)]?)*$");
    private static final Pattern BRACKET_WEIGHT = ScoreParseConstants.BRACKET_WEIGHT_PATTERN;
    private static final Pattern GRADE_SCORE = Pattern.compile("得\\s*(\\d{1,3}(?:\\.\\d+)?)\\s*分");
    private static final Pattern HEADER_ROW = Pattern.compile(
            "评分因素|评审因素|评分项|评审项|序号|编号|评审内容|评分标准|分值$");
    private static final Pattern CONTAINS_HAN = Pattern.compile("[\\p{IsHan}]");
    private static final int DIM_MAX_CHARS = 20;

    private StructuredTableCandidateExtractor() {
    }

    public static List<ScoreCandidate> extract(SidecarTableExtraction extraction) {
        if (extraction == null || extraction.tables().isEmpty()) return List.of();
        List<SidecarTableExtraction.TablePayload> scoreTables = filterScoreTables(extraction.tables());
        return scoreTables.stream().flatMap(t -> extractFromTable(t).stream()).toList();
    }

    private static List<SidecarTableExtraction.TablePayload> filterScoreTables(List<SidecarTableExtraction.TablePayload> tables) {
        List<SidecarTableExtraction.TablePayload> marked = tables.stream().filter(SidecarTableExtraction.TablePayload::isScoreTable).toList();
        return !marked.isEmpty() ? marked : tables.stream().filter(t -> t.rows() != null && t.rows().size() >= 3).toList();
    }

    private static final int MAX_PARENT_DIM_COLS = 3;
    private static final Pattern NON_DIM_TEXT = Pattern.compile("^(?:\\d+(?:\\.\\d+)?(?:分|%)?|最低分|最高分|分值|满分|权重|得分)$");

    private static List<ScoreCandidate> extractFromTable(SidecarTableExtraction.TablePayload table) {
        List<ScoreCandidate> candidates = new ArrayList<>();
        List<List<SidecarTableExtraction.CellPayload>> rows = table.rows();
        if (rows == null || rows.isEmpty()) {
            return candidates;
        }

        String[] colParentDim = new String[MAX_PARENT_DIM_COLS];
        int[] colRemainingRowspan = new int[MAX_PARENT_DIM_COLS];
        for (int r = 0; r < rows.size(); r++) {
            List<SidecarTableExtraction.CellPayload> row = rows.get(r);
            if (row == null || row.isEmpty()) {
                continue;
            }
            int scanLimit = Math.min(row.size(), MAX_PARENT_DIM_COLS);
            for (int i = 0; i < scanLimit; i++) {
                SidecarTableExtraction.CellPayload cell = row.get(i);
                if (!cell.isMergedContinue() && cell.rowspan() > 1) {
                    String text = stripBracketWeight(cell.text());
                    if (text != null && !NON_DIM_TEXT.matcher(text.trim()).find()) {
                        colParentDim[i] = text;
                        colRemainingRowspan[i] = cell.rowspan();
                    }
                }
            }
            List<SidecarTableExtraction.CellPayload> visibleCells = visibleCells(row);
            if (visibleCells.isEmpty() || isHeaderRow(visibleCells, r)) {
                decrementRemaining(colRemainingRowspan, colParentDim);
                continue;
            }
            String parentDim = extractParentDim(row);
            if (parentDim == null) {
                String topParent = colRemainingRowspan[0] > 0 ? colParentDim[0] : null;
                String subParent = null;
                for (int i = scanLimit - 1; i >= 1; i--) {
                    if (colRemainingRowspan[i] > 0 && colParentDim[i] != null) {
                        subParent = colParentDim[i];
                        break;
                    }
                }
                parentDim = topParent != null && subParent != null && !topParent.equals(subParent)
                        ? topParent + " / " + subParent : (subParent != null ? subParent : topParent);
            }
            decrementRemaining(colRemainingRowspan, colParentDim);
            ScoreCandidate candidate = parseRow(visibleCells, r, parentDim);
            if (candidate != null) candidates.add(candidate);
        }
        return candidates;
    }

    private static String extractParentDim(List<SidecarTableExtraction.CellPayload> fullRow) {
        String parentText = null;
        int scanLimit = Math.min(fullRow.size(), MAX_PARENT_DIM_COLS);
        for (int i = 0; i < scanLimit; i++) {
            SidecarTableExtraction.CellPayload cell = fullRow.get(i);
            if (cell.isMergedContinue() && !cell.text().isBlank()) {
                String stripped = stripBracketWeight(cell.text());
                if (stripped != null && !NON_DIM_TEXT.matcher(stripped.trim()).find()) {
                    parentText = stripped;
                }
            }
        }
        return parentText;
    }

    private static String stripBracketWeight(String text) {
        if (text == null || text.isBlank()) return null;
        String dim = BRACKET_WEIGHT.matcher(text).replaceAll("").replace("\n", " ").replaceAll("\\s+", " ").trim();
        return dim.isEmpty() ? null : dim;
    }

    private static void decrementRemaining(int[] remaining, String[] dims) {
        for (int i = 0; i < remaining.length; i++) {
            if (remaining[i] > 0 && --remaining[i] == 0) dims[i] = null;
        }
    }

    /** 提取非合并延续的可见单元格。 */
    private static List<SidecarTableExtraction.CellPayload> visibleCells(List<SidecarTableExtraction.CellPayload> row) {
        List<SidecarTableExtraction.CellPayload> result = new ArrayList<>();
        for (SidecarTableExtraction.CellPayload cell : row) {
            if (!cell.isMergedContinue()) {
                result.add(cell);
            }
        }
        return result;
    }

    /** 表头行判定：首行，或含 ≥2 个短表头关键词单元格（≤10 字符，防数据行误判）。 */
    private static boolean isHeaderRow(List<SidecarTableExtraction.CellPayload> cells, int rowIndex) {
        if (rowIndex == 0) {
            return true;
        }
        int headerKeywordCount = 0;
        for (SidecarTableExtraction.CellPayload cell : cells) {
            String text = cell.text().trim();
            if (text.length() <= 10 && HEADER_ROW.matcher(text).find()) {
                headerKeywordCount++;
            }
        }
        return headerKeywordCount >= 2;
    }

    private static ScoreCandidate parseRow(List<SidecarTableExtraction.CellPayload> cells,
            int rowIndex, String parentDim) {
        List<String> texts = cells.stream().map(SidecarTableExtraction.CellPayload::text).toList();

        String code = extractCode(texts);
        BracketWeightResult bracketResult = extractBracketWeight(texts);

        String detail = extractDetail(texts, bracketResult.col3Index());
        if (detail.isBlank()) {
            return null;
        }

        BigDecimal weight = bracketResult.weight();
        if (weight == null) {
            weight = maxGradeScore(detail);
        }
        if (weight == null) {
            String fullRow = String.join(" ", texts);
            weight = maxGradeScore(fullRow);
        }

        String dim = bracketResult.dimFromCol3();
        if (dim == null || dim.isBlank()) {
            dim = extractDim(texts);
        }

        String sourceText = cells.stream()
                .map(c -> c.text().trim())
                .filter(t -> !t.isEmpty())
                .reduce((a, b) -> a + " | " + b)
                .orElse("");

        String dimSource = parentDim != null ? "CELL" : "UNKNOWN";

        return new ScoreCandidate(
                code,
                dim,
                detail,
                weight,
                null,
                null,
                sourceText,
                "table-row-" + rowIndex,
                "STRUCTURED_TABLE",
                parentDim,
                dimSource
        );
    }

    private static String extractCode(List<String> texts) {
        if (texts.isEmpty()) {
            return "";
        }
        String first = texts.get(0).trim();
        if (first.length() <= 8 && CODE_PATTERN.matcher(first).matches()) {
            return first;
        }
        return "";
    }

    private static BracketWeightResult extractBracketWeight(List<String> texts) {
        // 从列3向列0扫描（跳过列0如果是编号列）
        boolean skipFirstColumn = texts.size() >= 3 && CODE_PATTERN.matcher(texts.get(0).trim()).matches();
        for (int idx = Math.min(texts.size() - 1, 3); idx >= 0; idx--) {
            if (idx == 0 && skipFirstColumn) {
                continue;
            }
            String col = texts.get(idx);
            Matcher matcher = BRACKET_WEIGHT.matcher(col);
            if (matcher.find()) {
                BigDecimal weight = new BigDecimal(matcher.group(1));
                String dim = BRACKET_WEIGHT.matcher(col).replaceAll("").trim();
                dim = dim.replace("\n", " ").replaceAll("\\s+", " ").trim();
                return new BracketWeightResult(weight, dim.isEmpty() ? null : dim, idx);
            }
        }
        return new BracketWeightResult(null, null, -1);
    }

    private static String extractDetail(List<String> texts, int col3Index) {
        if (texts.size() >= 5 && col3Index == 2) {
            String col4 = texts.get(3).trim();
            String col5 = texts.get(4).trim();
            if (!col4.isEmpty() && !col5.isEmpty()) {
                return col4 + " " + col5;
            } else if (!col4.isEmpty()) {
                return col4;
            } else if (!col5.isEmpty()) {
                return col5;
            }
        }

        // 回退：最长非编号非纯分值列
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
            if (t.isEmpty() || (i == 0 && CODE_PATTERN.matcher(t).matches())) continue;
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
