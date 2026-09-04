package com.xiyu.bid.casework.domain.policy;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.xiyu.bid.casework.domain.policy.TextSimilarityPolicy.cleanTitle;
import static com.xiyu.bid.casework.domain.policy.TextSimilarityPolicy.hasText;

/**
 * 纯核心：标点符号、引号与高质量实体提取策略（FP-Java 无副作用辅助类）。
 */
public final class PunctuationEntityExtractor {

    private static final Pattern QUOTE_PATTERN = Pattern.compile("[“\"「『]([^“”\"」』]{2,20})[”\"」』]");
    private static final Pattern CLAUSE_SPLIT_PATTERN = Pattern.compile("[；;\\n\\r。！？!?]+");
    private static final Pattern COMMA_SPLIT_PATTERN = Pattern.compile("[，,]+");
    private static final Pattern ENUM_COMMA_SPLIT_PATTERN = Pattern.compile("、+");
    private static final Pattern CONJUNCTION_SPLIT_PATTERN = Pattern.compile(
            "以及|(?<![涉普提])及(?!时)|(?<![参给])与|和(?![谐平])|/"
    );

    private static final Pattern LEADING_NOISE_PATTERN = Pattern.compile(
            "^(包括但不限于|包含但不限于|包括|包含|涵盖|如|例如|支持|提供|具备|具有|满足|进行|须|按照|向|针对|关于|为)+"
    );
    private static final Pattern TRAILING_NOISE_PATTERN = Pattern.compile(
            "(能力|等等|等|相关|之一|之二|内容|情况|工作)+$"
    );
    private static final Pattern LEADING_PUNCT_PATTERN = Pattern.compile(
            "^[\\p{Punct}\\p{Space}\\u3000-\\u303F\\uFF00-\\uFFEF]+"
    );
    private static final Pattern TRAILING_PUNCT_PATTERN = Pattern.compile(
            "[\\p{Punct}\\p{Space}\\u3000-\\u303F\\uFF00-\\uFFEF]+$"
    );

    private PunctuationEntityExtractor() {}

    /**
     * 分层解析标点符号，提取顿号（、）、分号（；）及引号中的高质量实体/并列业务词。
     */
    public static Set<String> extractPunctuationEntities(String... texts) {
        if (texts == null || texts.length == 0) {
            return Collections.emptySet();
        }
        Set<String> entities = new LinkedHashSet<>();
        for (String text : texts) {
            if (!hasText(text)) {
                continue;
            }
            Matcher quoteMatcher = QUOTE_PATTERN.matcher(text);
            while (quoteMatcher.find()) {
                String qEntity = cleanEntityToken(quoteMatcher.group(1));
                if (isQualityEntity(qEntity)) {
                    entities.add(qEntity);
                }
            }
            for (String clause : CLAUSE_SPLIT_PATTERN.split(text)) {
                if (!hasText(clause)) {
                    continue;
                }
                for (String seg : COMMA_SPLIT_PATTERN.split(clause)) {
                    if (!hasText(seg)) {
                        continue;
                    }
                    if (seg.contains("、")) {
                        for (String item : ENUM_COMMA_SPLIT_PATTERN.split(seg)) {
                            String clean = cleanEntityToken(item);
                            if (isQualityEntity(clean)) {
                                entities.add(clean);
                            }
                        }
                    } else {
                        entities.addAll(extractConjunctionEntities(seg));
                    }
                }
            }
        }
        return entities;
    }

    private static Set<String> extractConjunctionEntities(String seg) {
        if (!hasText(seg)) {
            return Collections.emptySet();
        }
        String cleanSeg = cleanEntityToken(seg);
        if (cleanSeg.contains("与") || cleanSeg.contains("及") || cleanSeg.contains("和")
                || cleanSeg.contains("/") || cleanSeg.contains("以及")) {
            String[] parts = CONJUNCTION_SPLIT_PATTERN.split(cleanSeg);
            if (parts.length >= 2) {
                Set<String> subEntities = new LinkedHashSet<>();
                for (String part : parts) {
                    String cleanPart = cleanEntityToken(part);
                    if (isQualityEntity(cleanPart)) {
                        subEntities.add(cleanPart);
                    }
                }
                return subEntities;
            }
        }
        if (isQualityEntity(cleanSeg) && cleanSeg.length() <= 10) {
            return Set.of(cleanSeg);
        }
        return Collections.emptySet();
    }

    /**
     * 清洗实体词前后噪音助词与泛化前缀/后缀。
     */
    public static String cleanEntityToken(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }
        String cleaned = cleanTitle(token.trim());
        cleaned = LEADING_PUNCT_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = TRAILING_PUNCT_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = LEADING_NOISE_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = TRAILING_NOISE_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = LEADING_PUNCT_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = TRAILING_PUNCT_PATTERN.matcher(cleaned).replaceAll("");
        return cleaned.trim();
    }

    /**
     * 判断提取的实体是否为高质量有效实体。
     */
    public static boolean isQualityEntity(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String trimmed = token.trim();
        int len = trimmed.length();
        if (len < 2 || len > 25 || TextSimilarityPolicy.STOP_WORDS.contains(trimmed)) {
            return false;
        }
        if (trimmed.indexOf('，') >= 0 || trimmed.indexOf(',') >= 0
                || trimmed.indexOf('；') >= 0 || trimmed.indexOf(';') >= 0
                || trimmed.indexOf('、') >= 0 || trimmed.indexOf('。') >= 0) {
            return false;
        }
        if (isValidSpecialEntity(trimmed)) {
            return containsAlphanumericOrCjk(trimmed);
        }
        char first = trimmed.charAt(0);
        char last = trimmed.charAt(len - 1);
        if (isLeadingParticle(first) || isTrailingParticle(last)) {
            return false;
        }
        return containsAlphanumericOrCjk(trimmed);
    }

    private static boolean isValidSpecialEntity(String trimmed) {
        if (trimmed.startsWith("在线") || trimmed.startsWith("在途") || trimmed.startsWith("在职")
                || trimmed.startsWith("以太")
                || trimmed.startsWith("等保") || trimmed.startsWith("等级")
                || trimmed.startsWith("及时")
                || trimmed.startsWith("华为")) {
            return true;
        }
        return trimmed.endsWith("华为");
    }

    public static boolean isParticle(char ch) {
        return isLeadingParticle(ch);
    }

    public static boolean isLeadingParticle(char ch) {
        return ch == '的' || ch == '得' || ch == '地' || ch == '了' || ch == '着'
                || ch == '在' || ch == '向' || ch == '从' || ch == '把' || ch == '被'
                || ch == '且' || ch == '和' || ch == '及' || ch == '与'
                || ch == '之' || ch == '或' || ch == '为' || ch == '以' || ch == '由'
                || ch == '于';
    }

    public static boolean isTrailingParticle(char ch) {
        return ch == '的' || ch == '得' || ch == '地' || ch == '了' || ch == '着'
                || ch == '之' || ch == '等';
    }

    public static boolean containsAlphanumericOrCjk(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || (c >= '\u4e00' && c <= '\u9fa5')) {
                return true;
            }
        }
        return false;
    }
}
