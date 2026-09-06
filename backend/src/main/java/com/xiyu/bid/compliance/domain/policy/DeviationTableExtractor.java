// Input: 投标文件 DocumentContent
// Output: ExtractionResult 包含识别到的偏离表、正负偏离行与占位符
// Pos: compliance/domain/policy/ - 商务与技术偏离表结构化提取器（纯核心）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.DocumentContent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 商务与技术偏离表结构化提取器（纯核心无异常，<= 300 行）.
 */
public final class DeviationTableExtractor {

    public enum DeviationType { NONE, POSITIVE, NEGATIVE, UNKNOWN }
    public enum TableCategory { COMMERCIAL, TECHNICAL, GENERAL }

    public record DeviationRow(
            int rowNumber, String clause, String tenderRequirement,
            String bidResponse, DeviationType type, String description, String rawLine
    ) {}

    public record DeviationTable(
            TableCategory category, String title, int pageNumber,
            List<DeviationRow> rows, List<String> placeholders,
            boolean hasNegativeDeviation, boolean hasPositiveDeviation, boolean allNoneOrPositive
    ) {}

    public record ExtractionResult(
            List<DeviationTable> tables, boolean hasCommercialTable, boolean hasTechnicalTable,
            List<String> allPlaceholders, List<DeviationRow> negativeRows, List<DeviationRow> positiveRows
    ) {}

    private static final Pattern UNDERLINE_PATTERN = Pattern.compile("_{3,}");
    private static final Pattern BRACKET_PATTERN = Pattern.compile(
            "【(?:请|在此|在此处|填写|输入|插入|待填|说明)[^】]{0,30}】|\\[(?:请|在此|填写|待填|说明)[^\\]]{0,30}\\]");

    public static final List<String> NEGATIONS = List.of(
            "无任何", "绝无", "绝不包含", "并不存在", "不存在", "没有", "不含",
            "绝不具有", "并不具有", "我方未作", "未作出", "未发生", "不构成", "未", "无", "零"
    );
    public static final List<String> NEGATIVES = List.of("负偏离", "不满足", "负偏差", "未满足", "部分响应", "部分满足", "低于");
    public static final List<String> POSITIVES = List.of("正偏离", "优于", "高于", "超过招标要求");

    private DeviationTableExtractor() {
    }

    public static ExtractionResult extract(final DocumentContent bidDoc) {
        if (bidDoc == null) return emptyResult();
        List<DocumentContent.PageContent> pages = bidDoc.pages();
        if (pages == null || pages.isEmpty()) {
            if (bidDoc.fullText() == null || bidDoc.fullText().isBlank()) return emptyResult();
            pages = List.of(new DocumentContent.PageContent(1, bidDoc.fullText(), List.of()));
        }

        List<DeviationTable> tables = new ArrayList<>();
        List<String> allPlaceholders = new ArrayList<>();
        List<DeviationRow> allNegatives = new ArrayList<>();
        List<DeviationRow> allPositives = new ArrayList<>();
        boolean hasComm = false;
        boolean hasTech = false;

        for (DocumentContent.PageContent page : pages) {
            if (page.text() == null || isCatalogPage(page.text())) continue;
            for (DeviationTable dt : parseTablesInPage(page)) {
                tables.add(dt);
                if (dt.category() == TableCategory.COMMERCIAL) hasComm = true;
                if (dt.category() == TableCategory.TECHNICAL) hasTech = true;
                allPlaceholders.addAll(dt.placeholders());
                for (DeviationRow row : dt.rows()) {
                    if (row.type() == DeviationType.NEGATIVE) allNegatives.add(row);
                    else if (row.type() == DeviationType.POSITIVE) allPositives.add(row);
                }
            }
        }
        return new ExtractionResult(List.copyOf(tables), hasComm, hasTech,
                List.copyOf(allPlaceholders), List.copyOf(allNegatives), List.copyOf(allPositives));
    }

    private static List<DeviationTable> parseTablesInPage(final DocumentContent.PageContent page) {
        List<DeviationTable> tables = new ArrayList<>();
        List<String> curLines = new ArrayList<>();
        String curTitle = "";
        TableCategory curCat = TableCategory.GENERAL;

        for (String rawLine : page.text().split("\\R")) {
            String line = rawLine.trim();
            if (isDeviationTitle(line)) {
                if (!curLines.isEmpty()) {
                    DeviationTable t = buildTable(curCat, curTitle, page.pageNumber(), curLines);
                    if (t != null) tables.add(t);
                    curLines.clear();
                }
                curTitle = line;
                curCat = classifyCategory(line);
                continue;
            }
            if (!line.contains("|") && line.contains("\t") && line.split("\t+").length >= 2) {
                line = "| " + String.join(" | ", line.split("\t+")) + " |";
            }
            if (line.contains("|") && splitCells(line).size() >= 2) {
                curLines.add(line);
            } else if (!curLines.isEmpty()) {
                DeviationTable t = buildTable(curCat, curTitle, page.pageNumber(), curLines);
                if (t != null) tables.add(t);
                curLines.clear();
                curTitle = "";
                curCat = TableCategory.GENERAL;
            }
        }
        if (!curLines.isEmpty()) {
            DeviationTable t = buildTable(curCat, curTitle, page.pageNumber(), curLines);
            if (t != null) tables.add(t);
        }
        return tables;
    }

    private static DeviationTable buildTable(
            final TableCategory cat, final String title, final int pageNum, final List<String> lines) {
        if (lines.size() < 2) return null;
        int headerIdx = -1, clauseCol = -1, reqCol = -1, respCol = -1, typeCol = -1, descCol = -1;

        for (int i = 0; i < Math.min(lines.size(), 3); i++) {
            List<String> cols = splitCells(lines.get(i));
            for (int c = 0; c < cols.size(); c++) {
                String col = cols.get(c);
                if (col.contains("条款") || col.contains("章节") || col.contains("条目") || col.contains("需求项")) clauseCol = c;
                else if (col.contains("招标") || col.contains("采购") || col.contains("要求") || col.contains("标准") || col.contains("规范")) reqCol = c;
                else if (col.contains("投标") || col.contains("实际") || col.contains("应答") || col.contains("承诺") || (col.contains("响应") && !col.contains("差异") && !col.contains("偏离"))) respCol = c;
                else if (col.contains("说明") || col.contains("备注") || col.contains("原因")) descCol = c;
                else if (col.contains("偏离") || col.contains("差异") || col.contains("类型") || col.contains("情况") || col.contains("偏差")) typeCol = c;
            }
            if (clauseCol >= 0 || typeCol >= 0 || descCol >= 0) {
                headerIdx = i;
                break;
            }
        }
        if (headerIdx < 0) return null;

        List<DeviationRow> rows = new ArrayList<>();
        List<String> placeholders = new ArrayList<>();
        int count = 0;

        for (int i = headerIdx + 1; i < lines.size(); i++) {
            String rawLine = lines.get(i);
            if (isSeparatorLine(rawLine)) continue;
            List<String> cells = splitCells(rawLine);
            if (cells.isEmpty()) continue;
            count++;
            placeholders.addAll(extractPlaceholders(rawLine));
            String clause = getCell(cells, clauseCol), req = getCell(cells, reqCol);
            String resp = getCell(cells, respCol), typeStr = getCell(cells, typeCol), desc = getCell(cells, descCol);
            rows.add(new DeviationRow(count, clause, req, resp, resolveDeviationType(typeStr, desc), desc, rawLine));
        }
        if (rows.isEmpty()) return null;

        boolean hasNeg = rows.stream().anyMatch(r -> r.type() == DeviationType.NEGATIVE);
        boolean hasPos = rows.stream().anyMatch(r -> r.type() == DeviationType.POSITIVE);
        boolean allGood = rows.stream().allMatch(r -> r.type() == DeviationType.NONE || r.type() == DeviationType.POSITIVE);
        return new DeviationTable(cat, title, pageNum, List.copyOf(rows), List.copyOf(placeholders), hasNeg, hasPos, allGood);
    }

    private static final List<String> NONE_KEYWORDS = List.of(
            "无偏离", "完全响应", "全面响应", "全部响应", "完全满足", "全面满足",
            "全部满足", "满足要求", "符合要求", "遵照执行", "严格遵从", "无负偏离",
            "不存在负偏离", "无任何负偏离", "无负偏差", "符合招标文件要求",
            "满足技术参数要求", "符合规范要求", "响应招标文件", "满足招标要求",
            "完全符合", "执行招标要求", "出具检验合格报告", "提供检测报告", "已获原厂授权"
    );

    private static DeviationType resolveDeviationType(final String typeText, final String descText) {
        String combined = (typeText + " " + descText).trim();
        if (combined.isBlank()) return DeviationType.UNKNOWN;
        if (containsNonNegatedKeyword(combined, NEGATIVES)) return DeviationType.NEGATIVE;
        if (containsNonNegatedKeyword(combined, POSITIVES)) return DeviationType.POSITIVE;
        if (NONE_KEYWORDS.stream().anyMatch(combined::contains)
                || combined.equals("/") || combined.equals("-") || combined.equals("无")) {
            return DeviationType.NONE;
        }
        return DeviationType.UNKNOWN;
    }

    private static boolean containsNonNegatedKeyword(final String text, final List<String> keywords) {
        for (String kw : keywords) {
            int from = 0, idx;
            while ((idx = text.indexOf(kw, from)) >= 0) {
                if (!hasNegationPrefix(text, idx)) return true;
                from = idx + kw.length();
            }
        }
        return false;
    }

    private static boolean hasNegationPrefix(final String text, final int idx) {
        int start = Math.max(0, idx - 18);
        for (int i = idx - 1; i >= start; i--) {
            char c = text.charAt(i);
            if (c == '，' || c == '。' || c == '；' || c == '、' || c == '\n' || c == '|' || c == ',' || c == ';' || c == '.') {
                start = i + 1;
                break;
            }
        }
        String prefix = text.substring(start, idx).trim();
        for (String negation : NEGATIONS) {
            if (prefix.contains(negation)) return true;
        }
        return false;
    }

    private static List<String> extractPlaceholders(final String text) {
        List<String> list = new ArrayList<>();
        Matcher m1 = UNDERLINE_PATTERN.matcher(text);
        while (m1.find()) list.add(m1.group());
        Matcher m2 = BRACKET_PATTERN.matcher(text);
        while (m2.find()) list.add(m2.group());
        return list;
    }

    private static List<String> splitCells(final String line) {
        return Arrays.stream(line.split("\\|")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static String getCell(final List<String> cells, final int idx) {
        return (idx >= 0 && idx < cells.size()) ? cells.get(idx) : "";
    }

    private static boolean isSeparatorLine(final String line) {
        return line.replace("|", "").replace("-", "").replace(":", "").trim().isEmpty();
    }

    private static boolean isDeviationTitle(final String line) {
        return (line.contains("偏离表") || line.contains("规格偏离")) && !isCatalogPage(line);
    }

    private static TableCategory classifyCategory(final String line) {
        if (line.contains("商务")) return TableCategory.COMMERCIAL;
        if (line.contains("技术")) return TableCategory.TECHNICAL;
        return TableCategory.GENERAL;
    }

    private static boolean isCatalogPage(final String text) {
        return text.contains("......") || text.contains(". . . .") || text.contains("……");
    }

    private static ExtractionResult emptyResult() {
        return new ExtractionResult(List.of(), false, false, List.of(), List.of(), List.of());
    }
}
