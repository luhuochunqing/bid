package com.xiyu.bid.compliance.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 标书质量核查文本匹配工具（纯核心，无副作用）.
 */
final class QualityCheckTextUtil {

    private QualityCheckTextUtil() {
    }

    static boolean containsAny(final String content, final String... keywords) {
        if (content == null || content.isBlank()) {
            return false;
        }
        String lower = content.toLowerCase();
        for (String keyword : keywords) {
            if (lower.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    static boolean containsPattern(final String content, final String regex) {
        if (content == null || content.isBlank()) {
            return false;
        }
        return Pattern.compile(regex).matcher(content).find();
    }

    static boolean containsDatePattern(final String content) {
        if (content == null) return false;
        return containsPattern(content, "\\d{4}[-/年]\\d{1,2}[-/月]");
    }

    static boolean containsPhonePattern(final String content) {
        if (content == null) return false;
        return containsPattern(content, "1[3-9]\\d{9}|\\d{3,4}-\\d{7,8}");
    }

    static boolean containsEmailPattern(final String content) {
        if (content == null) return false;
        return containsPattern(content, "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    }

    static boolean containsPageNumberPattern(final String content) {
        if (content == null) return false;
        return containsPattern(content, "第\\s*\\d+\\s*页|Page\\s*\\d+|\\b\\d+\\s*/\\s*\\d+\\b");
    }

    static List<String> extractAmounts(final String content) {
        List<String> amounts = new ArrayList<>();
        if (content == null) return amounts;
        Pattern pattern = Pattern.compile(
                "(?:投标总价|总报价|合计金额|总金额)[:：\\s]*([\\d,\\.]+)");
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            amounts.add(matcher.group(1).replace(",", ""));
        }
        return amounts;
    }

    static boolean allEqual(final List<String> list) {
        if (list.isEmpty()) return true;
        String first = list.get(0);
        return list.stream().allMatch(first::equals);
    }
}
