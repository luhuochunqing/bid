package com.xiyu.bid.biddraftagent.infrastructure.openai;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

class TenderIntakeTextProcessor {

    /** 候选文本上下文半径（行数）。包级可见以便测试引用，避免魔法数字。 */
    static final int INTAKE_CONTEXT_RADIUS = 3;
    private static final int INTAKE_CONTEXT_MAX_CHARS = 20_000;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final List<String> INTAKE_KEYWORDS = List.of(
            "项目名称", "项目标题", "标讯标题", "招标项目", "采购项目", "公告标题",
            "招标编号", "采购编号", "项目编号", "标段名称", "包号", "品目名称",
            "预算", "最高限价", "控制价", "金额", "人民币", "采购预算", "预算金额",
            "限价", "总价", "单价", "报价", "投标保证金", "总部", "所在地", "地区",
            "地点", "地址", "省", "市", "实施地点", "交货地点", "服务地点", "项目地点",
            "行政区划", "截止", "递交", "投标截止", "开标时间", "报名", "报名开始",
            "报名结束", "响应截止", "提交截止", "资格预审截止", "开标日期",
            // 招标主体相关：组织单位/主办单位/采购部门可能为招标主体，保留
            // 招标机构/代理机构/采购代理机构是代理机构语义，不映射到招标主体，已移除
            "组织单位", "主办单位", "采购部门", "联系人", "联系方式", "经办人",
            "项目负责人", "负责人", "联系电话", "电话", "传真", "电子邮箱", "通讯地址",
            "客户类型", "优先级", "采购方式", "招标方式", "组织形式", "项目概况",
            "项目描述", "采购内容", "招标范围", "标签", "项目背景", "建设内容",
            "服务范围", "技术要求", "资格条件", "商务要求"
    );

    /**
     * 合并 INTAKE_KEYWORDS 与 {@link PurchaserAliases#ALL}（招标主体明确标签），
     * 避免重复维护两份招标主体别名。包级可见以便同包测试做同步性断言。
     * 注意：{@link PurchaserAliases#POSSIBLE}（组织单位/主办单位/采购部门）
     * 已显式列入 INTAKE_KEYWORDS，此处不重复合并。
     */
    static final List<String> ALL_INTAKE_KEYWORDS =
            Stream.concat(INTAKE_KEYWORDS.stream(), PurchaserAliases.ALL.stream())
                    .distinct()
                    .toList();

    static String buildTenderIntakeCandidateText(String text) {
        String normalized = text == null ? "" : text;
        String[] lines = normalized.split("\\R");
        List<String> selected = new ArrayList<>();
        boolean[] include = new boolean[lines.length];
        for (int i = 0; i < lines.length; i++) {
            if (!containsIntakeKeyword(lines[i])) {
                continue;
            }
            int start = Math.max(0, i - INTAKE_CONTEXT_RADIUS);
            int end = Math.min(lines.length - 1, i + INTAKE_CONTEXT_RADIUS);
            for (int j = start; j <= end; j++) {
                include[j] = true;
            }
        }
        for (int i = 0; i < lines.length; i++) {
            if (include[i]) {
                selected.add(lines[i]);
            }
        }
        String candidate = String.join("\n", selected).trim();
        if (candidate.isBlank()) {
            candidate = normalized.substring(0, Math.min(normalized.length(), 8_000));
        }
        return candidate.length() <= INTAKE_CONTEXT_MAX_CHARS
                ? candidate
                : candidate.substring(0, INTAKE_CONTEXT_MAX_CHARS);
    }

    private static boolean containsIntakeKeyword(String line) {
        if (line == null || line.isBlank()) {
            return false;
        }
        // 归一化匹配：PDF 提取的文本经常有"半角空格/全角空格/换行/制表符/零宽字符"打断关键词，
        // 例如"招 标 人：XXX"（封面美化排版）、"招\u200B标人：XXX"（含零宽空格）。
        // 这里对原文做归一化后匹配，但 selected.add(lines[i]) 仍加原文，AI 看到的是原始文本。
        String normalized = normalizeForMatching(line);
        return ALL_INTAKE_KEYWORDS.stream().anyMatch(normalized::contains);
    }

    /**
     * 归一化文本用于关键词匹配：移除所有空白字符与不可见字符。
     * 仅用于 containsIntakeKeyword 的匹配，不修改原文本——AI 仍看原文，保留语义信息。
     * 例："招 标 人：XXX" → "招标人：XXX"（命中关键词"招标人"）
     *
     * <p>覆盖范围（按 Unicode 区段）：
     * <ul>
     *   <li>{@code \s} — 基础 ASCII 空白：半角空格、\t、\n、\r、\f、\v</li>
     *   <li>{@code \u00A0} — 不间断空格 NBSP</li>
     *   <li>{@code \u00AD} — 软连字符 SHY（部分 PDF 提取工具会保留）</li>
     *   <li>{@code \u2000-\u200A} — Unicode 空格（En/Em/Thin/Hair 等 11 种）</li>
     *   <li>{@code \u200B-\u200D} — 零宽字符 ZWSP/ZWNJ/ZWJ</li>
     *   <li>{@code \u2028-\u2029} — 行/段落分隔符 LS/PS</li>
     *   <li>{@code \u202F} — 窄不间断空格 NNBSP</li>
     *   <li>{@code \u205F} — 中等数学空格 MMSP</li>
     *   <li>{@code \u2060} — 字连接符 WJ</li>
     *   <li>{@code \u3000} — 全角空格</li>
     *   <li>{@code \uFEFF} — BOM / 零宽不间断空格 ZWNBSP</li>
     * </ul>
     *
     * <p>不修改原文本是关键设计——归一化只用于"是否命中关键词"的判定，
     * AI 看到的仍是原始文本，保留所有排版和语义信息。
     */
    private static String normalizeForMatching(String text) {
        if (text == null) return "";
        return text.replaceAll(
                "[\\s\\u00A0\\u00AD\\u2000-\\u200D\\u2028-\\u202F\\u205F\\u2060\\u3000\\uFEFF]+",
                "");
    }

    /**
     * 正则预提取：从候选文本中提取日期、金额、手机号、邮箱，作为提示传给 AI。
     * 业界实践：LLM 对日期/金额的格式化不够稳定，先由正则精确提取再让 AI 做语义匹配，准确率更高。
     */
    static String buildRegexHints(String text) {
        if (text == null || text.isBlank()) {
            return "（无预提取信息）";
        }
        StringBuilder hints = new StringBuilder();
        hints.append("以下信息由正则表达式预提取，可能存在误匹配，请以候选文本原文为准：\n");

        // 1. 日期提取：支持 yyyy年MM月dd日、yyyy-MM-dd、yyyy/MM/dd，可选时间
        List<String> dates = new ArrayList<>();
        Matcher dateMatcher = Pattern.compile(
                "(\\d{4})[年\\-/](\\d{1,2})[月\\-/](\\d{1,2})\\s*日?\\s*(\\d{1,2}:\\d{2}(?::\\d{2})?)?"
        ).matcher(text);
        while (dateMatcher.find()) {
            String year = dateMatcher.group(1);
            String month = String.format("%02d", Integer.parseInt(dateMatcher.group(2)));
            String day = String.format("%02d", Integer.parseInt(dateMatcher.group(3)));
            String time = dateMatcher.group(4);
            String dateStr = year + "-" + month + "-" + day;
            if (time != null && !time.isBlank()) {
                dateStr += "T" + normalizeTime(time.trim());
            }
            // 附加日期前 15 个字符作为上下文，帮助 AI 区分是"获取文件时间"还是"投标截止时间"
            int start = Math.max(0, dateMatcher.start() - 15);
            String prefix = text.substring(start, dateMatcher.start()).trim();
            String label = prefix.isEmpty() ? "" : "（上下文：" + prefix + "）";
            dates.add(dateStr + label);
        }
        if (!dates.isEmpty()) {
            hints.append("- 日期：").append(String.join("、", dates)).append("\n");
        }

        // 2. 金额提取：匹配 "数字+万/亿/元" 格式，附加归一化提示
        List<String> amounts = new ArrayList<>();
        Matcher amountMatcher = Pattern.compile(
                "(\\d+(?:\\.\\d+)?)\\s*(万|亿)?\\s*元"
        ).matcher(text);
        while (amountMatcher.find()) {
            String num = amountMatcher.group(1);
            String unit = amountMatcher.group(2);
            String normalized;
            if ("万".equals(unit)) {
                normalized = num + "万元（归一化：" + new java.math.BigDecimal(num).multiply(new java.math.BigDecimal("10000")).toPlainString() + "元）";
            } else if ("亿".equals(unit)) {
                normalized = num + "亿元（归一化：" + new java.math.BigDecimal(num).multiply(new java.math.BigDecimal("100000000")).toPlainString() + "元）";
            } else {
                normalized = num + "元";
            }
            amounts.add(normalized);
        }
        if (!amounts.isEmpty()) {
            hints.append("- 金额：").append(String.join("、", amounts)).append("\n");
        }

        // 3. 手机号提取：确保不在更长数字串中间（前后非数字）
        List<String> phones = new ArrayList<>();
        Matcher phoneMatcher = Pattern.compile(
                "(?<!\\d)1[3-9]\\d{9}(?!\\d)"
        ).matcher(text);
        while (phoneMatcher.find()) {
            phones.add(phoneMatcher.group());
        }
        if (!phones.isEmpty()) {
            hints.append("- 手机号：").append(String.join("、", phones)).append("\n");
        }

        // 4. 邮箱提取
        List<String> emails = new ArrayList<>();
        Matcher emailMatcher = Pattern.compile(
                "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"
        ).matcher(text);
        while (emailMatcher.find()) {
            emails.add(emailMatcher.group());
        }
        if (!emails.isEmpty()) {
            hints.append("- 邮箱：").append(String.join("、", emails)).append("\n");
        }

        if (hints.toString().equals("以下信息由正则表达式预提取，可能存在误匹配，请以候选文本原文为准：\n")) {
            return "（无预提取信息）";
        }
        return hints.toString();
    }

    /** 归一化时间：HH:mm → HH:mm:00；HH:mm:ss → 保持不变 */
    static String normalizeTime(String time) {
        if (time == null || time.isBlank()) return "";
        long colonCount = time.chars().filter(c -> c == ':').count();
        if (colonCount == 1) {
            return time + ":00";
        }
        return time;
    }

    /**
     * 从 markitdown sidecar 返回的 structuredMetadata JSON 中提取文档标题结构。
     * 对于粘贴文本（structuredMetadata 为 null），返回空字符串。
     * 提取的标题层级帮助 AI 理解文档结构，提高字段定位准确率。
     */
    static String parseSectionsFromMetadata(String structuredMetadata) {
        if (structuredMetadata == null || structuredMetadata.isBlank()) {
            return "";
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(structuredMetadata);
            JsonNode sections = root.path("sections");
            if (!sections.isArray() || sections.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("文档标题结构（由 markitdown 提取，帮助定位字段所在章节）：\n");
            for (JsonNode sec : sections) {
                int level = sec.path("level").asInt(0);
                String heading = sec.path("heading").asText("");
                if (heading.isBlank()) continue;
                sb.append("  ".repeat(Math.max(0, level - 1)));
                sb.append("- ").append(heading).append("\n");
            }
            return sb.toString();
        } catch (JsonProcessingException e) {
            return "";
        }
    }

    static String sanitizeUntrusted(String raw) {
        if (raw == null) return "";
        return raw.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}