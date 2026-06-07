package com.xiyu.bid.biddraftagent.domain;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 正则感知的关键词匹配工具。
 * 检测关键词是否含正则元字符，有则用 Pattern.compile().find()，否则用 contains() 快速路径。
 * 修复分类策略中 ".*"、"[案法]" 等正则语法未生效的死代码问题。
 */
public final class RegexKeywordMatcher {

    private static final String REGEX_META_CHARS = ".*+?[](){}\\^$|";
    private static final Map<String, Pattern> PATTERN_CACHE = new ConcurrentHashMap<>();

    private RegexKeywordMatcher() {}

    /**
     * 检查文本是否匹配任一关键词。
     * 纯文本关键词使用 contains()（快速路径），正则关键词使用 Pattern（慢路径）。
     */
    public static boolean matchesAny(String text, List<String> keywords) {
        if (text == null || keywords == null || keywords.isEmpty()) return false;
        String lower = text.toLowerCase(Locale.ROOT);
        for (String kw : keywords) {
            if (containsRegexMeta(kw)) {
                Pattern pattern = PATTERN_CACHE.computeIfAbsent(
                        kw, k -> Pattern.compile(k, Pattern.CASE_INSENSITIVE));
                if (pattern.matcher(lower).find()) return true;
            } else {
                if (lower.contains(kw.toLowerCase(Locale.ROOT))) return true;
            }
        }
        return false;
    }

    private static boolean containsRegexMeta(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (REGEX_META_CHARS.indexOf(s.charAt(i)) >= 0) return true;
        }
        return false;
    }
}
