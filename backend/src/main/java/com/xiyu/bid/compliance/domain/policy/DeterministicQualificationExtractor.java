// Input: 招标文件解析后的文本内容（String）
// Output: 确定性提取的硬性资质与资格要求列表（List<String>），用于双轨融合与规则兜底
// Pos: compliance/domain/policy/ - 招标资质与资格确定性提取纯核心策略（FP-Java 无外部依赖）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 招标资质与资格要求确定性提取策略（纯核心纯函数，无 Spring 依赖）.
 *
 * <p>基于中国招投标法律法规与标准公文规范（《招标投标法》第 18 条），任何合规招标文件
 * 必然包含供应商资格要求章节（通用资格 + 专用资质证书）。本策略通过确定性章节锚定、
 * 标号分条提取与关键词特征匹配，毫秒级提取硬性资格要求，与 LLM 提取形成双轨互补，
 * 彻底杜绝大模型因注意力稀释、JSON 格式破坏或超时引发的漏识别与 0 匹配。
 */
public final class DeterministicQualificationExtractor {

    /** 资格要求章节标题正则. */
    private static final Pattern HEADING_PATTERN = Pattern.compile(
            "(?:^|\\n)\\s*(?:[一二三四五六七八九十\\d]+[、.\\s]*)?"
                    + "(?:供应商|投标人|申请人)?(?:资格要求|资质要求|资格条件|资质条件|资格审查|资格性审查|专用资格条件|通用资格条件|资质及能力要求)[^\\n]*");

    /** 下一主章节截至锚点正则（避免跨章节误提取评标办法或文件获取等）. */
    private static final Pattern NEXT_SECTION_PATTERN = Pattern.compile(
            "\\n\\s*(?:[一二三四五六七八九十\\d]+[、.\\s]*)?(?:采购文件|招标文件|响应文件的递交|投标文件的递交|开标|评标|招标范围|联系方式)");

    /** 条目分割标号正则：①-⑩、（1）-（99）、1.-99.、3.1.1 等多级序号、中文一、二、等. */
    private static final Pattern BULLET_PATTERN = Pattern.compile(
            "([①②③④⑤⑥⑦⑧⑨⑩]|(?:^|\\n|[；;。])\\s*(?:[（\\(]\\d+[）\\)]|\\d+\\.\\d+(?:\\.\\d+)*[、.\\s]*|\\d+[.、\\)]|[一二三四五六七八九十]+[、.]|[（\\(][一二三四五六七八九十]+[）\\)]))");

    /** 汉字之间因 OCR 或排版换行的折行修复正则. */
    private static final Pattern CHINESE_LINEBREAK_PATTERN = Pattern.compile("([\\u4e00-\\u9fa5])\\s*\\n\\s*([\\u4e00-\\u9fa5])");

    /** 连续空白压缩正则. */
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    /** 前导多级标号与条目符号清洗正则. */
    private static final Pattern LEADING_BULLET_PATTERN = Pattern.compile(
            "^(?:[①②③④⑤⑥⑦⑧⑨⑩]|[\\s（(]?\\d+[)）]?[.、\\s]*|\\d+\\.\\d+(?:\\.\\d+)*[、.\\s]*|[一二三四五六七八九十]+[、.]|[（(][一二三四五六七八九十]+[)）])[\\s]*");

    /** 前导引导词清洗正则. */
    private static final Pattern LEADING_PREFIX_PATTERN = Pattern.compile(
            "^(?:(?:资质要求|资格要求|通用资格条件|专用资格条件)[：:]|(?:供应商|投标人|申请人)(?:应具有以下证书|须具备以下(?:资质|资格|证书))[：:])[\\s]*");

    /** 资质核心特征关键词库. */
    private static final List<String> QUALIFICATION_KEYWORDS = List.of(
            "许可证", "证书", "资质", "等级", "备案证明", "认证",
            "ISO", "CMMI", "ITSS", "CSBM", "等保", "安全生产",
            "总承包", "专业承包", "营业执照", "独立法人", "独立承担民事责任",
            "民事责任能力", "信用中国", "失信被执行人", "增值电信"
    );

    /** 非资质条目排除前缀（无具体证书时排除人员/财务/业绩说明）. */
    private static final List<String> IGNORE_PREFIXES = List.of(
            "人员要求", "财务要求", "业绩要求", "其他要求"
    );

    /** 核心证书许可关键字（用于从人员/业绩等要求中挽救包含证书的条款）. */
    private static final List<String> CERTIFICATE_CORE_KEYWORDS = List.of(
            "许可证", "证书", "资质", "备案证明", "认证"
    );

    private static final String BULLET_DELIMITER = "\u0001\u0002";

    private DeterministicQualificationExtractor() {
    }

    /**
     * 确定性提取招标文件文本中的资格与资质要求.
     *
     * @param tenderText 招标文件全文解析文本
     * @return 提取出的硬性资质条款列表（去重、保序、不可变）
     */
    public static List<String> extract(final String tenderText) {
        if (tenderText == null || tenderText.isBlank()) {
            return List.of();
        }

        List<String> sections = locateSections(tenderText);
        LinkedHashSet<String> extracted = new LinkedHashSet<>();

        for (String section : sections) {
            String marked = BULLET_PATTERN.matcher(section).replaceAll(BULLET_DELIMITER + "$1");
            String[] rawChunks = marked.split(Pattern.quote(BULLET_DELIMITER));

            for (String raw : rawChunks) {
                String item = normalizeChunkText(raw);
                if (item.isBlank()) {
                    continue;
                }

                String clean = cleanItem(item);
                if (isValidQualification(clean)) {
                    extracted.add(clean);
                }
            }
        }

        return List.copyOf(extracted);
    }

    private static List<String> locateSections(final String text) {
        Matcher matcher = HEADING_PATTERN.matcher(text);
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

        if (sections.isEmpty()) {
            // 未找到明确章节标题时，全文参与兜底扫描
            sections.add(text);
        }
        return sections;
    }

    private static String normalizeChunkText(final String raw) {
        String noLinebreak = CHINESE_LINEBREAK_PATTERN.matcher(raw).replaceAll("$1$2");
        return WHITESPACE_PATTERN.matcher(noLinebreak).replaceAll(" ").trim();
    }

    private static String cleanItem(final String text) {
        String clean = LEADING_BULLET_PATTERN.matcher(text).replaceFirst("").trim();
        clean = LEADING_PREFIX_PATTERN.matcher(clean).replaceFirst("").trim();
        // 去除尾部多余标点
        while (!clean.isEmpty() && isTrailingPunctuation(clean.charAt(clean.length() - 1))) {
            clean = clean.substring(0, clean.length() - 1).trim();
        }
        return clean;
    }

    private static boolean isTrailingPunctuation(final char c) {
        return c == '；' || c == ';' || c == '。' || c == '，' || c == ',' || c == '：' || c == ':';
    }

    private static boolean isValidQualification(final String clean) {
        if (clean.length() < 4) {
            return false;
        }
        // 占位符与无要求过滤
        if ("/".equals(clean) || "无".equals(clean) || "不适用".equals(clean) || "不要求".equals(clean)
                || clean.endsWith("：/") || clean.endsWith(":/")) {
            return false;
        }
        // 排除纯人员/财务/业绩要求（未含证书许可关键字时）
        boolean isIgnoredPrefix = IGNORE_PREFIXES.stream().anyMatch(clean::startsWith);
        if (isIgnoredPrefix && CERTIFICATE_CORE_KEYWORDS.stream().noneMatch(clean::contains)) {
            return false;
        }

        return QUALIFICATION_KEYWORDS.stream().anyMatch(clean::contains);
    }
}
