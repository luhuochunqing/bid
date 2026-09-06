// Input: 页面文本或 DocumentContent (含有 Markdown 表格结构)
// Output: 表格填写完整性核验结果（空单元格、斜杠占位符、模板留白检测与行列定位）
// Pos: compliance/domain/policy/ - 表格填写完整性计算助手（FP-Java 纯核心，严格 <= 300 行）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.DocumentContent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 表格填写完整性计算助手 (FP-Java 纯核心).
 */
public final class TableCellIntegrityCalculator {

    private static final Pattern SEPARATOR_LINE_PATTERN = Pattern.compile(
            "^\\|?\\s*(?::?-+:?\\s*\\|)+\\s*(?::?-+:?\\s*)?\\|?$"
    );

    private static final Pattern SLASH_DASH_BLANK_PATTERN = Pattern.compile(
            "^[/\\\\\\-—_]{1,6}$"
    );

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile(
            "_{3,}|【(?:请|在此|填写|待填|待定|此处)[^】]{0,30}】|\\[(?:请|在此|填写|待填)[^\\]]{0,30}\\]"
    );

    private TableCellIntegrityCalculator() {
    }

    public record CellViolation(
            int pageNumber,
            int rowNumber,
            int colNumber,
            String colHeader,
            String cellContent,
            String violationType,
            String rawLine) {
    }

    public record TableIntegrityResult(
            boolean hasViolation,
            List<CellViolation> violations,
            String summaryMessage,
            Integer firstErrorPage) {

        public static TableIntegrityResult clean() {
            return new TableIntegrityResult(false, Collections.emptyList(), "表格填写完整，无空单元格与无效斜杠留白", null);
        }

        public static TableIntegrityResult ofViolations(final List<CellViolation> violations) {
            if (violations == null || violations.isEmpty()) {
                return clean();
            }
            CellViolation first = violations.get(0);
            StringBuilder sb = new StringBuilder("检测到 ")
                    .append(violations.size())
                    .append(" 处表格填写不完整：第 ")
                    .append(first.pageNumber())
                    .append(" 页表格第 ")
                    .append(first.rowNumber())
                    .append(" 行[")
                    .append(first.colHeader())
                    .append("]")
                    .append(first.violationType())
                    .append("（值: \"")
                    .append(first.cellContent())
                    .append("\"）");
            return new TableIntegrityResult(true, Collections.unmodifiableList(violations), sb.toString(), first.pageNumber());
        }
    }

    public static TableIntegrityResult validateDocument(final DocumentContent bidDoc) {
        if (bidDoc == null || bidDoc.pages() == null || bidDoc.pages().isEmpty()) {
            return TableIntegrityResult.clean();
        }

        List<CellViolation> allViolations = new ArrayList<>();
        for (DocumentContent.PageContent page : bidDoc.pages()) {
            if (BidLetterCheckPolicy.isCatalogOrOutlinePage(page.text())) {
                continue;
            }
            TableIntegrityResult pageRes = validateText(page.pageNumber(), page.text());
            if (pageRes.hasViolation()) {
                allViolations.addAll(pageRes.violations());
            }
        }
        return TableIntegrityResult.ofViolations(allViolations);
    }

    public static TableIntegrityResult validateText(final int pageNumber, final String text) {
        if (text == null || text.isBlank() || !text.contains("|")) {
            return TableIntegrityResult.clean();
        }

        String[] lines = text.split("\\r?\\n");
        List<CellViolation> violations = new ArrayList<>();

        List<String> currentHeaders = new ArrayList<>();
        boolean inTable = false;
        int rowIdx = 0;

        for (String line : lines) {
            String trimmed = line.trim();
            if (isTableRow(trimmed)) {
                if (isSeparatorRow(trimmed)) {
                    // 分隔行直接跳过，表明前一行是有效表头
                    inTable = true;
                    continue;
                }

                List<String> cells = splitCells(trimmed);
                if (cells.isEmpty()) {
                    continue;
                }

                if (!inTable) {
                    // 表头行
                    currentHeaders = cells;
                    inTable = true;
                    rowIdx = 0;
                } else {
                    // 数据行
                    rowIdx++;
                    violations.addAll(checkDataRow(pageNumber, rowIdx, cells, currentHeaders, trimmed));
                }
            } else {
                // 表格中断
                inTable = false;
                currentHeaders = new ArrayList<>();
                rowIdx = 0;
            }
        }

        return TableIntegrityResult.ofViolations(violations);
    }

    private static boolean isTableRow(final String line) {
        if (line == null || line.length() < 3) {
            return false;
        }
        return line.startsWith("|") || (line.contains("|") && line.endsWith("|"));
    }

    private static boolean isSeparatorRow(final String line) {
        return SEPARATOR_LINE_PATTERN.matcher(line).matches();
    }

    private static List<String> splitCells(final String line) {
        String s = line.trim();
        if (s.startsWith("|")) {
            s = s.substring(1);
        }
        if (s.endsWith("|")) {
            s = s.substring(0, s.length() - 1);
        }
        String[] parts = s.split("\\|", -1);
        List<String> cells = new ArrayList<>();
        for (String p : parts) {
            cells.add(p.trim());
        }
        return cells;
    }

    private static List<CellViolation> checkDataRow(
            final int pageNumber,
            final int rowIdx,
            final List<String> cells,
            final List<String> headers,
            final String rawLine) {

        List<CellViolation> rowViolations = new ArrayList<>();
        for (int c = 0; c < cells.size(); c++) {
            String colHeader = (c < headers.size()) ? headers.get(c) : ("第" + (c + 1) + "列");
            if (isExemptColumn(colHeader)) {
                continue;
            }

            String cell = cells.get(c);
            if (cell.isEmpty()) {
                rowViolations.add(new CellViolation(
                        pageNumber, rowIdx, c + 1, colHeader, "", "空单元格未填写", rawLine));
            } else if (SLASH_DASH_BLANK_PATTERN.matcher(cell).matches()) {
                rowViolations.add(new CellViolation(
                        pageNumber, rowIdx, c + 1, colHeader, cell, "无效斜杠/横杠留白", rawLine));
            } else if (PLACEHOLDER_PATTERN.matcher(cell).find()) {
                rowViolations.add(new CellViolation(
                        pageNumber, rowIdx, c + 1, colHeader, cell, "未替换模板占位符", rawLine));
            }
        }
        return rowViolations;
    }

    private static boolean isExemptColumn(final String header) {
        if (header == null) {
            return false;
        }
        return header.contains("偏离说明") || header.contains("偏离情况")
                || header.contains("偏离") || header.contains("备注")
                || header.contains("说明") || header.contains("其他说明");
    }
}
