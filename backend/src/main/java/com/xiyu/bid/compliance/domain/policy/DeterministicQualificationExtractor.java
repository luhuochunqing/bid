// Input: 招标文件解析后的文本内容（String）
// Output: 确定性提取的专用行业资质与证书要求列表（List<String>）/ 通用主体资格条件列表（List<String>）
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
 * <p>基于中国招投标法律法规与标准公文规范（《招标投标法》第 18 条），供应商资格条件分为两大维度：
 * 1. 通用主体资格条件（独立法人/民事责任/财务信誉/违法失信等）：由《营业执照》及承诺函证明，由 Rule 4（营业执照）等规则闭环；
 * 2. 专用行业资质许可（增值电信/安全生产/建筑施工/等保/ISO 认证等）：由专业证书证明，由 Rule 5（资质比对清单）闭环。
 *
 * <p>本策略对条款做确定性分类与过滤，仅将专用行业资质证书送入资质比对清单，杜绝通用法人条款误入资质库导致错配或未维护误报。
 */
public final class DeterministicQualificationExtractor {

    /** 资格要求章节标题正则. */
    private static final Pattern HEADING_PATTERN = Pattern.compile(
            "(?:^|\\n)\\s*(?:[一二三四五六七八九十\\d]+[、.\\s]*)?"
                    + "(?:供应商|投标人|申请人)?(?:资格要求|资质要求|资格条件|资质条件|资格审查|资格性审查|专用资格条件|通用资格条件|资质及能力要求)[^\\n]*");

    /** 下一主章节截至锚点正则（避免跨章节误提取评标办法或文件获取等）. */
    private static final Pattern NEXT_SECTION_PATTERN = Pattern.compile(
            "\\n\\s*(?:[一二三四五六七八九十\\d]+[、.\\s]*)?(?:采购文件|招标文件|响应文件的递交|投标文件的递交|开标|评标|招标范围|联系方式)");

    /** 条目分割标号正则：①-⑩、（1）-（99）、1.-99.、3.1.1 等多级序号、中文一、二、Markdown 标题与表格行边界. */
    private static final Pattern BULLET_PATTERN = Pattern.compile(
            "([①②③④⑤⑥⑦⑧⑨⑩]|(?:^|\\n|[；;。])\\s*(?:[（\\(]\\d+[）\\)]|\\d+\\.\\d+(?:\\.\\d+)*[、.\\s]*|\\d+[.、\\)]|[一二三四五六七八九十]+[、.]|[（\\(][一二三四五六七八九十]+[）\\)])|\\n\\s*#{1,6}\\s+|\\n\\s*\\|)");

    /** 单条专用资质有效长度上限（法定证书名不超过 100 字，杜绝评分细则与长句粘连渗透）. */
    public static final int MAX_QUALIFICATION_LENGTH = 100;

    /** 评分细则、表格说明与格式模板排除关键词（坚决阻断评分项与模板渗漏为有效资格与资质条目）. */
    private static final List<String> SCORING_AND_TEMPLATE_EXCLUDE_KEYWORDS = List.of(
            "评审细则", "评分细则", "评分标准", "商务评审", "技术评审", "价格评审", "打分",
            "分值", "最高分", "最低分", "得分", "加权系数", "评审内容", "评审要素", "评审办法",
            "填写认证名称", "有🞎", "无🞎", "应答文件格式", "格式模板", "响应说明",
            "结构化响应表", "未取得认证", "取得认证的得", "取得1项", "取得2项",
            "编制说明", "填表说明"
    );

    /** 违法失信与行政处罚关键词（属于通用资格或否决条款，严禁作为专用资质证书提取）. */
    private static final List<String> PENALTY_AND_DISCREDIT_KEYWORDS = List.of(
            "停产", "停业", "暂扣", "吊销", "被责令", "破产", "清算", "违法", "失信", "资信不良", "资格不符"
    );


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

    /** 专用资质证书核心特征关键词（特定行业行政许可、体系认证、工程资质、等级评定等，需由资质库证书或专业批文证明）. */
    private static final List<String> SPECIFIC_CERTIFICATE_KEYWORDS = List.of(
            "许可证", "认证", "ISO", "CMMI", "ITSS", "CSBM", "CCRC", "CISSP", "CISP",
            "等保", "等级保护", "备案证明", "总承包", "专业承包", "施工劳务", "施工资质", "设计资质", "勘察资质",
            "增值电信", "互联网信息服务", "安全生产", "资质证书", "资格证书", "等级证书", "能力证书",
            "高新技术企业", "专精特新", "系统集成", "测绘资质", "经营许可"
    );

    /** 通用主体与法定义务资格条件特征词（民事主体/财务信誉/违法失信等，由营业执照及承诺函证明，非专用资质库证书）. */
    private static final List<String> GENERAL_CAPACITY_KEYWORDS = List.of(
            "独立法人", "民事责任", "民事责任能力", "订立合同", "合法注册", "营业执照",
            "信用中国", "失信被执行人", "经营异常名录", "严重违法失信", "中国执行信息公开网",
            "停产", "停业", "破产", "财务状况", "依法缴纳税收", "社会保障资金", "财务会计制度",
            "重大违法记录", "行贿犯罪", "联合体", "代理商", "单位负责人为同一人", "控股、管理关系"
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
     * 确定性提取招标文件中的专用行业资质与证书要求（供资质比对与一票否决使用）.
     * 自动过滤通用主体资格条款（如独立法人、财务信誉），杜绝资质库错配.
     */
    public static List<String> extract(final String tenderText) {
        return extractCertificates(tenderText);
    }

    /**
     * 专用行业资质证书提取（与 {@link #extract(String)} 等价）.
     */
    public static List<String> extractCertificates(final String tenderText) {
        return extractChunks(tenderText).stream()
                .filter(DeterministicQualificationExtractor::isSpecificCertificate)
                .toList();
    }

    /**
     * 确定性提取通用主体资格条件（独立法人、民事责任能力、信用合规等）.
     */
    public static List<String> extractGeneralCapacities(final String tenderText) {
        return extractChunks(tenderText).stream()
                .filter(DeterministicQualificationExtractor::isGeneralCapacity)
                .toList();
    }

    private static List<String> extractChunks(final String tenderText) {
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
                if (isValidText(clean)) {
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

    static String cleanItem(final String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String clean = text.trim();
        while (!clean.isEmpty() && isLeadingPunctuation(clean.charAt(0))) {
            clean = clean.substring(1).trim();
        }
        clean = LEADING_BULLET_PATTERN.matcher(clean).replaceFirst("").trim();
        while (!clean.isEmpty() && isLeadingPunctuation(clean.charAt(0))) {
            clean = clean.substring(1).trim();
        }
        clean = LEADING_PREFIX_PATTERN.matcher(clean).replaceFirst("").trim();
        while (!clean.isEmpty() && isTrailingPunctuation(clean.charAt(clean.length() - 1))) {
            clean = clean.substring(0, clean.length() - 1).trim();
        }
        return clean;
    }

    private static boolean isLeadingPunctuation(final char c) {
        return c == '|' || c == '；' || c == ';' || c == '。' || c == '，' || c == ',' || c == '、' || c == '：' || c == ':';
    }

    private static boolean isTrailingPunctuation(final char c) {
        return c == '|' || c == '；' || c == ';' || c == '。' || c == '，' || c == ',' || c == '：' || c == ':';
    }

    static boolean isIntroductoryHeading(final String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        String clean = text.trim();
        if (clean.matches(".*(?:应具有|须具备|应具备|具备|具有|满足|符合)?(?:以下|下列)\\s*(?:证书|资质|条件|要求|证明材料|证明).*")) {
            return clean.replaceFirst(".*(?:以下|下列)\\s*(?:证书|资质|条件|要求|证明材料|证明)[：:]?", "").isBlank();
        }
        if (clean.matches("^(?:[\\d一二三四五六七八九十.、\\s（\\)]*)"
                + "(?:供应商|投标人|申请人)?(?:资格要求|资质要求|通用资格条件|专用资格条件|资格条件|资质条件|资质及能力要求)[：:]?$")) {
            return true;
        }
        return clean.endsWith("：") || clean.endsWith(":");
    }

    /**
     * 判定条款是否为专用行业资质证书要求（增值电信、安全生产、等保、ISO 认证、总承包等）.
     */
    public static boolean isSpecificCertificate(final String clean) {
        if (!isValidText(clean) || isIntroductoryHeading(clean)) {
            return false;
        }
        if (PENALTY_AND_DISCREDIT_KEYWORDS.stream().anyMatch(clean::contains)) {
            return false;
        }
        return SPECIFIC_CERTIFICATE_KEYWORDS.stream().anyMatch(clean::contains);
    }

    /**
     * 判定条款是否为通用主体资格条件（独立法人、民事责任、财务信誉、信用失信等）.
     */
    public static boolean isGeneralCapacity(final String clean) {
        if (!isValidText(clean) || isIntroductoryHeading(clean)) {
            return false;
        }
        boolean hasGeneral = GENERAL_CAPACITY_KEYWORDS.stream().anyMatch(clean::contains);
        return hasGeneral && SPECIFIC_CERTIFICATE_KEYWORDS.stream().noneMatch(clean::contains);
    }

    private static boolean isValidText(final String clean) {
        if (clean == null || clean.length() < 4 || clean.length() > MAX_QUALIFICATION_LENGTH) {
            return false;
        }
        if (clean.contains("|") || clean.startsWith("---") || clean.startsWith(":---")) {
            return false;
        }
        if (isIntroductoryHeading(clean)) {
            return false;
        }
        if ("/".equals(clean) || "无".equals(clean) || "不适用".equals(clean) || "不要求".equals(clean)
                || clean.endsWith("：/") || clean.endsWith(":/")) {
            return false;
        }
        if (SCORING_AND_TEMPLATE_EXCLUDE_KEYWORDS.stream().anyMatch(clean::contains)) {
            return false;
        }
        boolean isIgnoredPrefix = IGNORE_PREFIXES.stream().anyMatch(clean::startsWith);
        if (isIgnoredPrefix && CERTIFICATE_CORE_KEYWORDS.stream().noneMatch(clean::contains)) {
            return false;
        }
        return true;
    }
}
