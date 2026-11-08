// Input: 招标原文中的资格章节（包含 Markdown 标题与表格内容）
// Output: 完整资格条件（清洗 HTML 标签、解码实体并保留比较符），不以证书词表或证书名长度裁剪条件
// Pos: compliance/domain/policy/ - 资格章节边界与条件清洗纯核心
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.DocumentContent;
import org.jsoup.parser.Parser;
import org.jsoup.parser.Tag;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 完整资格条件与证书名称采用不同的提取边界。 */
public final class QualificationConditionExtractor {
    private static final String NUMBER = "(?:第[一二三四五六七八九十0-9]+章\\h*|[0-9]+(?:\\.[0-9]+)+[、.]?\\h*|[一二三四五六七八九十0-9]+[、.]\\h*)";
    private static final String TITLE = "(?:供应商|投标人|申请人)?(?:资格要求|资质要求|资格条件|资质条件|资格审查|资格性审查|专用资格条件|通用资格条件|资质及能力要求)";
    private static final Pattern TOC_LINE = Pattern.compile("(?m)(?:\\.{3,}|…{2,}|[-—–.·]{3,})\\s*[-—–]?\\s*\\d+\\s*[-—–]?\\s*$");
    private static final Pattern HEADING = Pattern.compile("(?m)^\\h*(?:#{1,6}\\h*)?" + NUMBER + "?" + TITLE
            + "(?!\\h*(?:相关证明文件|证明文件|证明材料|材料清单|文件格式|附件))\\h*(?:[(（][^)\n]*[)）])?\\h*[：:]?");
    private static final Pattern NEXT_SECTION = Pattern.compile("(?m)^\\h*(?:#{1,6}\\h*)?" + NUMBER + "?"
            + "(?:采购需求|采购文件|招标文件|投标文件格式|响应文件格式|投标文件组成|响应文件组成|文件组成|响应文件的递交|投标文件的递交|开标|评标|招标范围|联系方式"
            + "|投标文件的语言|投标有效期|投标人应提供文件和资料|应提供文件和资料|招标文件的澄清|投标报价|第一卷|第二卷|第三卷|商务标|技术标|经济标|投标保证金|投标文件的编制)");
    private static final Pattern BULLET = Pattern.compile("^(?:[①②③④⑤⑥⑦⑧⑨⑩]|[（(][0-9一二三四五六七八九十]+[）)]|[0-9]+(?:\\.[0-9]+)+\\h+|[0-9]+[.、)]|[一二三四五六七八九十]+[、.])\\h*");
    private static final Pattern CHAPTER = Pattern.compile("^\\h*第[一二三四五六七八九十0-9]+章\\h*[^。；，\\n]{0,30}$");
    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^\\h*(#{1,6})\\h+.+");
    private static final Pattern MARKDOWN_LEVEL = Pattern.compile("^\\h*(#{1,6})");
    private static final Pattern NUMERIC_PREFIX = Pattern.compile("^\\h*([0-9]+(?:\\.[0-9]+)*)(?:[、.]?)(?=[^0-9.]|$)");
    /** 日期和带单位的小数是数据，不是章节或条目编号。 */
    private static final Pattern VALUE_PREFIX = Pattern.compile("^(?:[0-9]{4}\\.[0-9]{1,2}\\.[0-9]{1,2}(?=\\D|$)"
            + "|[0-9]+\\.[0-9]+\\h*(?:亿元|万元|元|%|％|年|天|人|项|分)(?=以上|以下|及|内|外|起|至|\\s|[，。；：:]|$))");
    private static final Pattern CHINESE_BULLET = Pattern.compile("^\\h*[一二三四五六七八九十]+[、.]");
    private enum NumberRole { NONE, CONDITION, NEXT_SECTION, UNKNOWN }
    private record SourceLine(String text, String original, boolean startsCondition) { }
    private record NumberPath(List<String> parts, String remainder, boolean separated) { }

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
            return new QualificationCondition(condition.text(), source, condition.certificates());
        }).toList();
    }

    private static List<QualificationCondition> extractRaw(final String text) {
        if (text == null || text.isBlank()) return List.of();
        Matcher heading = HEADING.matcher(text);
        LinkedHashSet<QualificationCondition> conditions = new LinkedHashSet<>();
        boolean found = false;
        while (heading.find()) {
            int lineStart = text.lastIndexOf('\n', heading.start());
            lineStart = lineStart == -1 ? 0 : lineStart + 1;
            int lineEnd = text.indexOf('\n', heading.end());
            lineEnd = lineEnd == -1 ? text.length() : lineEnd;
            String headingLine = text.substring(lineStart, lineEnd);
            if (TOC_LINE.matcher(headingLine).find()) continue;

            found = true;
            Matcher next = NEXT_SECTION.matcher(text);
            int end = next.find(heading.end()) ? next.start() : text.length();
            String opening = heading.group();
            List<SourceLine> lines = new ArrayList<>();
            List<String> parentPath = numberPath(opening).parts();
            BigInteger lastOrdinal = null;
            boolean uncertainScope = false;
            for (String line : text.substring(heading.end(), end).split("\\r?\\n", -1)) {
                if (TOC_LINE.matcher(line).find()) continue;
                if (endsSection(line, opening)) break;
                NumberPath path = numberPath(line);
                NumberRole role = HEADING.matcher(line).find() ? NumberRole.NONE : numberRole(parentPath, path, lastOrdinal);
                if (role == NumberRole.NEXT_SECTION) break;
                boolean ambiguous = role == NumberRole.UNKNOWN || CHINESE_BULLET.matcher(line).find() && !HEADING.matcher(line).find();
                if (ambiguous && !uncertainScope) {
                    conditions.addAll(assembleConditions(lines));
                    lines.clear();
                    uncertainScope = true;
                }
                if (role == NumberRole.CONDITION && path.parts().size() == 1) lastOrdinal = new BigInteger(path.parts().get(0));
                lines.add(new SourceLine(role == NumberRole.CONDITION ? path.remainder() : line, line, role == NumberRole.CONDITION));
            }
            List<QualificationCondition> remaining = assembleConditions(lines);
            conditions.addAll(uncertainScope ? remaining.stream().map(QualificationConditionExtractor::unknownScope).toList() : remaining);
        }
        if (found) return List.copyOf(conditions);
        List<QualificationCondition> fallback = new ArrayList<>();
        for (String raw : DeterministicQualificationExtractor.extractCertificates(text)) {
            appendCondition(fallback, raw, raw);
        }
        return List.copyOf(fallback);
    }

    private static boolean endsSection(final String line, final String opening) {
        if (HEADING.matcher(line).find()) return false;
        Matcher markdown = MARKDOWN_HEADING.matcher(line);
        Matcher start = MARKDOWN_LEVEL.matcher(opening);
        if (markdown.matches()) return !start.find() || markdown.group(1).length() <= start.group(1).length();
        return CHAPTER.matcher(line).matches();
    }

    private static NumberPath numberPath(final String text) {
        String plain = text.trim().replaceFirst("^#{1,6}\\h*", "");
        Matcher prefix = NUMERIC_PREFIX.matcher(plain);
        if (VALUE_PREFIX.matcher(plain).find() || !prefix.find()) return new NumberPath(List.of(), plain, false);
        String number = prefix.group(1);
        if (!number.contains(".") && (prefix.end(1) == plain.length()
                || plain.charAt(prefix.end(1)) != '.' && plain.charAt(prefix.end(1)) != '、')) return new NumberPath(List.of(), plain, false);
        var parts = java.util.Arrays.stream(number.split("\\.")).map(n -> new BigInteger(n).toString()).toList();
        boolean separated = prefix.end(1) < plain.length() && (Character.isWhitespace(plain.charAt(prefix.end(1)))
                || plain.charAt(prefix.end(1)) == '.' || plain.charAt(prefix.end(1)) == '、');
        return new NumberPath(parts, plain.substring(prefix.end()).trim(), separated);
    }

    /** 只用编号路径及已观察的列表序列判定；两种解释均成立时不能猜成强制资格。 */
    private static NumberRole numberRole(final List<String> parent, final NumberPath path, final BigInteger lastOrdinal) {
        List<String> candidate = path.parts();
        if (candidate.isEmpty()) return NumberRole.NONE;
        if (parent.isEmpty()) {
            BigInteger ordinal = new BigInteger(candidate.get(0));
            boolean sequence = lastOrdinal == null ? ordinal.equals(BigInteger.ONE)
                    : (candidate.size() > 1 && ordinal.equals(lastOrdinal)) || ordinal.equals(lastOrdinal.add(BigInteger.ONE));
            return sequence ? NumberRole.CONDITION : NumberRole.UNKNOWN;
        }
        if (candidate.size() > 1) {
            boolean child = candidate.size() > parent.size() && candidate.subList(0, parent.size()).equals(parent);
            return child ? NumberRole.CONDITION : path.separated() && isLaterSection(parent, candidate)
                    ? NumberRole.NEXT_SECTION : NumberRole.UNKNOWN;
        }
        BigInteger ordinal = new BigInteger(candidate.get(0));
        boolean sequence = lastOrdinal == null ? ordinal.equals(BigInteger.ONE) : ordinal.equals(lastOrdinal.add(BigInteger.ONE));
        boolean afterParent = ordinal.compareTo(new BigInteger(parent.get(0))) > 0;
        return sequence ? afterParent ? NumberRole.UNKNOWN : NumberRole.CONDITION
                : lastOrdinal == null && afterParent && parent.size() == 1 ? NumberRole.NEXT_SECTION : NumberRole.UNKNOWN;
    }

    private static boolean isLaterSection(final List<String> parent, final List<String> candidate) {
        for (int i = 0; i < Math.min(parent.size(), candidate.size()); i++) {
            int comparison = new BigInteger(candidate.get(i)).compareTo(new BigInteger(parent.get(i)));
            if (comparison != 0) return comparison > 0;
        }
        return false;
    }

    private static QualificationCondition unknownScope(final QualificationCondition condition) {
        var certificates = condition.certificates().stream().map(c ->
                c.applicability() == QualificationCondition.Applicability.OPTIONAL ? c : new QualificationCondition.CertificateRequirement(
                        c.requirementText(), c.target(), QualificationCondition.Applicability.UNKNOWN,
                        "章节与列表边界不明确；" + c.basis(), c.cellIndex())).toList();
        return new QualificationCondition(condition.text(), condition.source(), certificates);
    }

    private static List<QualificationCondition> assembleConditions(final List<SourceLine> lines) {
        List<QualificationCondition> result = new ArrayList<>();
        StringBuilder paragraph = new StringBuilder();
        StringBuilder original = new StringBuilder();
        List<String> header = List.of();
        for (SourceLine sourceLine : lines) {
            String line = sourceLine.text();
            String trimmed = line.trim();
            if (HEADING.matcher(trimmed).find()) {
                appendCondition(result, paragraph.toString(), original.toString());
                paragraph.setLength(0);
                original.setLength(0);
                continue;
            }
            if (trimmed.contains("|")) {
                appendCondition(result, paragraph.toString(), original.toString());
                paragraph.setLength(0);
                original.setLength(0);
                if (trimmed.matches("[|\\s:-]+")) continue;
                List<String> cells = java.util.Arrays.stream(trimmed.replaceAll("^\\||\\|$", "").split("\\|", -1))
                        .map(String::trim).toList();
                if (isTableHeader(cells)) { header = cells; continue; }
                appendCondition(result, tableCondition(cells, header), sourceLine.original());
            } else {
                if (sourceLine.startsCondition() || trimmed.isBlank() || BULLET.matcher(trimmed).find()
                        || paragraph.toString().matches("(?s).*[。；;]$")) {
                    appendCondition(result, paragraph.toString(), original.toString());
                    paragraph.setLength(0);
                    original.setLength(0);
                }
                paragraph.append(trimmed);
                if (!original.isEmpty()) original.append('\n');
                original.append(sourceLine.original());
            }
        }
        appendCondition(result, paragraph.toString(), original.toString());
        return result;
    }

    private static final Pattern HTML_TAG_RE = Pattern.compile(
            "(?i)</?([a-z][a-z0-9]*)(?=\\s|/?>)(?:[^<>\"']|\"[^\"]*\"|'[^']*')*>");

    public static String stripHtml(final String text) {
        if (text == null || text.isBlank()) return "";
        // 只移除已知 HTML 标签；先去标签再解码，避免将编码后的文字当作标记吞掉。
        String clean = HTML_TAG_RE.matcher(text).replaceAll(match -> Matcher.quoteReplacement(
                Tag.isKnownTag(match.group(1).toLowerCase(java.util.Locale.ROOT)) ? "" : match.group()));
        return Parser.unescapeEntities(clean, false).trim();
    }

    private static String tableCondition(final List<String> cells, final List<String> header) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < cells.size(); i++) {
            boolean serial = i < header.size() && header.get(i).equals("序号") && cells.get(i).matches("[0-9]+");
            if (!serial && !cells.get(i).isBlank()) values.add(cells.get(i));
        }
        return String.join("；", values);
    }

    private static boolean isTableHeader(final List<String> cells) {
        return cells.size() >= 2 && cells.get(0).equals("序号") && cells.stream().allMatch(c ->
                c.matches("序号|审查项目|审查标准|审查内容|资格条件|资格要求|证明材料|备注"));
    }

    private static boolean appendCondition(final List<QualificationCondition> result, final String text, final String quote) {
        String item = cleanCondition(stripHtml(text));
        if (item.isBlank() || isHeading(item) || item.matches("无|不适用|不要求|/|[-:|\\s]+")) return false;
        return result.add(new QualificationCondition(item, new QualificationCondition.Source("", "", null, quote), null));
    }

    /** 已解码条件的编号与标点清理；不再解释 HTML 或实体。 */
    static String cleanCondition(final String text) {
        String clean = text == null ? "" : text.trim().replaceFirst("^#{1,6}\\h*", "");
        clean = clean.replaceAll("^[|；;。，,、：:\\s]+|[|；;。，,：:\\s]+$", "");
        if (VALUE_PREFIX.matcher(clean).find()) return clean;
        return BULLET.matcher(clean).replaceFirst("").trim();
    }

    static boolean isHeading(final String text) {
        return text.matches(TITLE);
    }
}
