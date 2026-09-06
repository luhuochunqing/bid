// Input: 招标文件文本内容（String）
// Output: 确定性提取的废标/一票否决条款及关键词列表（List<TenderRequirements.VetoClause>）
// Pos: compliance/domain/policy/ - 招标一票否决与废标条款确定性提取纯核心策略（FP-Java 无外部依赖）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 招标文件一票否决与废标条款确定性提取策略（纯核心纯函数，无 Spring 依赖）.
 *
 * <p>基于招投标法与标准公文规范，从招标文件中确定性抽取具有否决投标、作废标处理、
 * 取消投标资格等法律后果的一票否决条款，并抽取对应场景关键词.
 */
public final class DeterministicVetoExtractor {

    /** 废标/一票否决章节标题正则. */
    private static final Pattern VETO_HEADING_PATTERN = Pattern.compile(
            "(?:^|\\n)\\s*(?:[一二三四五六七八九十\\d]+[、.\\s]*)?"
                    + "(?:否决投标(?:的情形|条款|的条件)?|废标(?:条件|条款|的情形)?|无效投标(?:的情形)?|一票否决(?:条款|情形)?|实质性要求和条件)[^\\n]*");

    /** 下一主章节截至锚点正则. */
    private static final Pattern NEXT_SECTION_PATTERN = Pattern.compile(
            "\\n\\s*(?:[一二三四五六七八九十\\d]+[、.\\s]*)?(?:评标办法|综合评估法|最低评标价法|中标候选人|中标人确定|合同条款|响应文件格式|采购文件格式|开标)");

    /** 条目分割标号正则. */
    private static final Pattern BULLET_PATTERN = Pattern.compile(
            "([①②③④⑤⑥⑦⑧⑨⑩]|(?:^|\\n|[；;。])\\s*(?:[（\\(]\\d+[）\\)]|\\d+\\.\\d+(?:\\.\\d+)*[、.\\s]*|\\d+[.、\\)]|[一二三四五六七八九十]+[、.]|[（\\(][一二三四五六七八九十]+[）\\)]))");

    /** 前导标号清洗正则（含前导标点分号、句号、空白等）. */
    private static final Pattern LEADING_BULLET_PATTERN = Pattern.compile(
            "^[；;。，,\\s]*(?:[①②③④⑤⑥⑦⑧⑨⑩]|[\\s（(]?\\d+[)）]?[.、\\s]*|\\d+\\.\\d+(?:\\.\\d+)*[、.\\s]*|[一二三四五六七八九十]+[、.]|[（(][一二三四五六七八九十]+[)）])[\\s]*");

    /** 废标/否决投标法律效果特征词（需包含明确处置动作，避免'无废标'单字误触）. */
    private static final List<String> VETO_CONSEQUENCE_KEYWORDS = List.of(
            "否决其投标", "作废标处理", "视为无效投标", "一票否决", "不予受理", "取消其投标资格",
            "按废标处理", "视为放弃投标", "拒绝其投标", "做废标处理", "予以废标", "视为废标"
    );

    /** 常见一票否决高频场景关键词库. */
    private static final List<String> KEYWORD_TAXONOMY = List.of(
            "分包", "转包", "违法分包", "串通投标", "弄虚作假", "行贿", "虚假材料",
            "最高限价", "最高投标限价", "控制价", "低于成本", "联合体", "签章", "盖章", "签字",
            "备选方案", "选择性报价", "保证金", "资质证书", "授权委托书"
    );

    private static final String BULLET_DELIMITER = "\u0001\u0002";

    private DeterministicVetoExtractor() {
    }

    /**
     * 确定性提取招标文件中的废标/一票否决条款及关键词.
     */
    public static List<TenderRequirements.VetoClause> extract(final String tenderText) {
        if (tenderText == null || tenderText.isBlank()) {
            return List.of();
        }

        LinkedHashSet<TenderRequirements.VetoClause> clauses = new LinkedHashSet<>();
        List<String> sections = locateVetoSections(tenderText);

        for (String section : sections) {
            String marked = BULLET_PATTERN.matcher(section).replaceAll(BULLET_DELIMITER + "$1");
            String[] rawChunks = marked.split(Pattern.quote(BULLET_DELIMITER));

            for (String raw : rawChunks) {
                String clean = cleanItem(raw);
                if (clean.length() >= 6 && !isVetoIntro(clean)) {
                    clauses.add(new TenderRequirements.VetoClause(clean, extractKeywords(clean)));
                }
            }
        }

        // 行内嵌入废标条款扫描（补漏）：扫描包含明确否决法律后果句子的行/句
        clauses.addAll(scanInlineVetoClauses(tenderText, clauses));

        return List.copyOf(clauses);
    }

    private static boolean isVetoIntro(final String text) {
        return text.contains("下列情形之一") || text.endsWith("：") || text.endsWith(":")
                || text.matches("^(?:[一二三四五六七八九十\\d]+[、.\\s]*)?(?:否决投标|废标|无效投标|一票否决)[^。；]*$");
    }

    private static List<String> locateVetoSections(final String text) {
        Matcher matcher = VETO_HEADING_PATTERN.matcher(text);
        List<String> sections = new ArrayList<>();

        while (matcher.find()) {
            int start = matcher.start();
            Matcher nextMatcher = NEXT_SECTION_PATTERN.matcher(text);
            int end;
            if (nextMatcher.find(start + 1)) {
                end = nextMatcher.start();
            } else {
                end = Math.min(text.length(), start + 4000);
            }
            sections.add(text.substring(start, end));
        }
        return sections;
    }

    private static List<TenderRequirements.VetoClause> scanInlineVetoClauses(
            final String text, final LinkedHashSet<TenderRequirements.VetoClause> existingClauses) {
        List<TenderRequirements.VetoClause> inlineClauses = new ArrayList<>();
        String[] lines = text.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.length() < 10 || trimmed.length() > 300) {
                continue;
            }
            if (isVetoClause(trimmed)) {
                String clean = cleanItem(trimmed);
                if (existingClauses.stream().noneMatch(c -> c.clause().contains(clean) || clean.contains(c.clause()))
                        && inlineClauses.stream().noneMatch(c -> c.clause().contains(clean) || clean.contains(c.clause()))) {
                    inlineClauses.add(new TenderRequirements.VetoClause(clean, extractKeywords(clean)));
                }
            }
        }
        return inlineClauses;
    }

    private static boolean isVetoClause(final String text) {
        for (String keyword : VETO_CONSEQUENCE_KEYWORDS) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> extractKeywords(final String clauseText) {
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        for (String kw : KEYWORD_TAXONOMY) {
            if (clauseText.contains(kw)) {
                keywords.add(kw);
            }
        }
        if (clauseText.contains("盖章") || clauseText.contains("签字")) {
            keywords.add("签章");
        }
        if (clauseText.contains("最高投标限价") || clauseText.contains("控制价")) {
            keywords.add("最高限价");
        }
        return List.copyOf(keywords);
    }

    private static String cleanItem(final String raw) {
        String cleaned = LEADING_BULLET_PATTERN.matcher(raw.trim()).replaceFirst("");
        return cleaned.replaceAll("\\s+", " ").trim();
    }
}
