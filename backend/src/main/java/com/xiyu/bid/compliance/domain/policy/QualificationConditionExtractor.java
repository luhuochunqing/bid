// Input: 招标原文中的资格章节（包含 Markdown 标题）
// Output: 完整资格条件，不以证书词表或证书名长度裁剪条件
// Pos: compliance/domain/policy/ - 资格章节边界与条件清洗纯核心
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.DocumentContent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 完整资格条件与证书名称采用不同的提取边界。 */
public final class QualificationConditionExtractor {
    private static final String NUMBER = "(?:第[一二三四五六七八九十0-9]+章\\h*|[一二三四五六七八九十0-9]+[、.]\\h*)?";
    private static final String TITLE = "(?:供应商|投标人|申请人)?(?:资格要求|资质要求|资格条件|资质条件|资格审查|资格性审查|专用资格条件|通用资格条件|资质及能力要求)";
    private static final Pattern HEADING = Pattern.compile("(?m)^\\h*(?:#{1,6}\\h*)?" + NUMBER + TITLE + "\\h*[：:]?");
    private static final Pattern NEXT_SECTION = Pattern.compile("(?m)^\\h*(?:#{1,6}\\h*)?" + NUMBER
            + "(?:采购文件|招标文件|投标文件格式|响应文件格式|投标文件组成|响应文件组成|文件组成|响应文件的递交|投标文件的递交|开标|评标|招标范围|联系方式)");
    private static final Pattern BULLET = Pattern.compile("^(?:[①②③④⑤⑥⑦⑧⑨⑩]|[（(][0-9一二三四五六七八九十]+[）)]|[0-9]+(?:\\.[0-9]+)+\\h+|[0-9]+[.、)]|[一二三四五六七八九十]+[、.])\\h*");
    private static final Pattern CHAPTER = Pattern.compile("^\\h*(?:第[一二三四五六七八九十0-9]+章|[一二三四五六七八九十]+[、.])\\h*([^。；，\\n]{1,30})$");
    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^\\h*(#{1,6})\\h+.+");
    private static final Pattern MARKDOWN_LEVEL = Pattern.compile("^\\h*(#{1,6})");

    private QualificationConditionExtractor() { }

    public static List<String> extract(final String text) {
        return extractRaw(text).stream().map(QualificationCondition::text).toList();
    }

    public static List<QualificationCondition> extractStructured(final DocumentContent document) {
        return extractRaw(document.fullText()).stream().map(condition -> {
            String quote = condition.source().quote();
            List<Integer> pages = document.pages().stream().filter(p -> !quote.isBlank() && p.text().contains(quote))
                    .map(DocumentContent.PageContent::pageNumber).distinct().toList();
            var source = new QualificationCondition.Source(document.fileName(), document.fileHash(),
                    pages.size() == 1 ? pages.get(0) : null, quote);
            return new QualificationCondition(condition.text(), source, null);
        }).toList();
    }

    private static List<QualificationCondition> extractRaw(final String text) {
        if (text == null || text.isBlank()) return List.of();
        Matcher heading = HEADING.matcher(text);
        LinkedHashSet<QualificationCondition> conditions = new LinkedHashSet<>();
        boolean found = false;
        while (heading.find()) {
            found = true;
            Matcher next = NEXT_SECTION.matcher(text);
            int end = next.find(heading.end()) ? next.start() : text.length();
            String opening = heading.group();
            List<String> lines = new ArrayList<>();
            for (String line : text.substring(heading.end(), end).split("\\r?\\n", -1)) {
                if (endsSection(line, opening)) break;
                lines.add(line);
            }
            conditions.addAll(assembleConditions(lines));
        }
        return found ? List.copyOf(conditions)
                : QualificationCondition.complete(null, DeterministicQualificationExtractor.extractCertificates(text));
    }

    private static boolean endsSection(final String line, final String opening) {
        if (HEADING.matcher(line).find()) return false;
        Matcher markdown = MARKDOWN_HEADING.matcher(line);
        Matcher start = MARKDOWN_LEVEL.matcher(opening);
        if (markdown.matches()) return !start.find() || markdown.group(1).length() <= start.group(1).length();
        Matcher chapter = CHAPTER.matcher(line);
        return chapter.matches() && !chapter.group(1).matches(".*(?:资格|法人|财务|信用|信誉|业绩|纳税|社保|人员|证书|认证).*?")
                && !chapter.group(1).matches(".*(?:具有|应当|必须|须|不得|提供).*?");
    }

    private static List<QualificationCondition> assembleConditions(final List<String> lines) {
        List<QualificationCondition> result = new ArrayList<>();
        StringBuilder paragraph = new StringBuilder();
        StringBuilder original = new StringBuilder();
        List<String> header = List.of();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.contains("|")) {
                appendCondition(result, paragraph.toString(), original.toString());
                paragraph.setLength(0);
                original.setLength(0);
                if (trimmed.matches("[|\\s:-]+")) continue;
                List<String> cells = java.util.Arrays.stream(trimmed.replaceAll("^\\||\\|$", "").split("\\|", -1))
                        .map(String::trim).toList();
                if (isTableHeader(cells)) { header = cells; continue; }
                List<String> values = new ArrayList<>();
                for (int i = 0; i < cells.size(); i++) {
                    if (i < header.size() && header.get(i).equals("序号") && cells.get(i).matches("[0-9]+")) continue;
                    if (!cells.get(i).isBlank()) values.add(cells.get(i));
                }
                appendCondition(result, String.join("；", values), line);
            } else {
                if (trimmed.isBlank() || BULLET.matcher(trimmed).find()
                        || paragraph.toString().matches("(?s).*[。；;]$")) {
                    appendCondition(result, paragraph.toString(), original.toString());
                    paragraph.setLength(0);
                    original.setLength(0);
                }
                paragraph.append(trimmed);
                if (!original.isEmpty()) original.append('\n');
                original.append(line);
            }
        }
        appendCondition(result, paragraph.toString(), original.toString());
        return result;
    }

    private static boolean isTableHeader(final List<String> cells) {
        return cells.size() >= 2 && cells.get(0).equals("序号") && cells.stream().allMatch(c ->
                c.matches("序号|审查项目|审查标准|审查内容|资格条件|资格要求|证明材料|备注"));
    }

    private static boolean appendCondition(final List<QualificationCondition> result, final String text, final String quote) {
        String item = cleanCondition(text);
        if (item.isBlank() || isHeading(item) || item.matches("无|不适用|不要求|/|[-:|\\s]+")) return false;
        return result.add(new QualificationCondition(item, new QualificationCondition.Source("", "", null, quote), null));
    }

    static String cleanCondition(final String text) {
        String clean = text == null ? "" : text.trim().replaceFirst("^#{1,6}\\h*", "");
        clean = clean.replaceAll("^[|；;。，,、：:\\s]+|[|；;。，,：:\\s]+$", "");
        return BULLET.matcher(clean).replaceFirst("").trim();
    }

    static boolean isHeading(final String text) {
        return text.matches(TITLE);
    }
}
