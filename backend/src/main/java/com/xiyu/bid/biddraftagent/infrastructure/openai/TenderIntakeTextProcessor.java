package com.xiyu.bid.biddraftagent.infrastructure.openai;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

class TenderIntakeTextProcessor {

    private static final int INTAKE_CONTEXT_RADIUS = 3;
    private static final int INTAKE_CONTEXT_MAX_CHARS = 20_000;
    private static final List<String> INTAKE_KEYWORDS = List.of(
            "项目名称", "项目标题", "标讯标题", "招标项目", "采购项目", "公告标题",
            "招标编号", "采购编号", "项目编号", "标段名称", "包号", "品目名称",
            "预算", "最高限价", "控制价", "金额", "人民币", "采购预算", "预算金额",
            "限价", "总价", "单价", "报价", "投标保证金", "总部", "所在地", "地区",
            "地点", "地址", "省", "市", "实施地点", "交货地点", "服务地点", "项目地点",
            "行政区划", "截止", "递交", "投标截止", "开标时间", "报名", "报名开始",
            "报名结束", "响应截止", "提交截止", "资格预审截止", "开标日期", "采购人",
            "采购单位", "招标人", "招标机构", "代理机构", "采购代理机构", "组织单位",
            "主办单位", "采购部门", "需求单位", "联系人", "联系方式", "经办人",
            "项目负责人", "负责人", "联系电话", "电话", "传真", "电子邮箱", "通讯地址",
            "客户类型", "优先级", "采购方式", "招标方式", "组织形式", "项目概况",
            "项目描述", "采购内容", "招标范围", "标签", "项目背景", "建设内容",
            "服务范围", "技术要求", "资格条件", "商务要求"
    );

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
        return INTAKE_KEYWORDS.stream().anyMatch(line::contains);
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
                dateStr += "T" + time.trim() + ":00";
            }
            dates.add(dateStr);
        }
        if (!dates.isEmpty()) {
            hints.append("- 日期：").append(String.join("、", dates)).append("\n");
        }

        // 2. 金额提取：匹配 "数字+万/亿/元" 格式
        List<String> amounts = new ArrayList<>();
        Matcher amountMatcher = Pattern.compile(
                "(\\d+(?:\\.\\d+)?)\\s*(?:万|亿)?\\s*元"
        ).matcher(text);
        while (amountMatcher.find()) {
            amounts.add(amountMatcher.group());
        }
        if (!amounts.isEmpty()) {
            hints.append("- 金额：").append(String.join("、", amounts)).append("\n");
        }

        // 3. 手机号提取
        List<String> phones = new ArrayList<>();
        Matcher phoneMatcher = Pattern.compile(
                "1[3-9]\\d{9}"
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
            JsonNode root = new ObjectMapper().readTree(structuredMetadata);
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
        return raw.replace("<document>", "&lt;document&gt;").replace("</document>", "&lt;/document&gt;");
    }
}