package com.xiyu.bid.casework.domain.policy;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文本相似度计算纯核心策略。
 *
 * <p>统一的分词、前缀剥离与相似度计算工具，避免在多个策略类中重复实现。
 */
public final class TextSimilarityPolicy {

    /**
     * 招投标通用停用词集合（过滤无实质区分度的泛化词汇）。
     */
    public static final Set<String> STOP_WORDS = Set.of(
            "中国", "全国", "全省", "省", "市", "区", "县",
            "投标人", "招标人", "采购人", "招标文件", "投标文件", "要求", "项目", "提供", "具备",
            "进行", "根据", "包含", "包括", "满足", "方案", "系统", "服务", "管理", "工作",
            "综合", "评审", "相关", "以及", "本项", "原则", "规范", "标准", "支持", "保障",
            "有限公司", "有限责任公司", "股份有限公司", "分公司", "公司", "集团", "采购", "招标", "投标",
            "年度", "第一", "第二", "第三", "第四", "第五", "响应", "承诺", "材料", "内容",
            "负责", "落实", "关于", "等", "及", "与", "或",
            "的", "了", "在", "是", "为", "以", "由", "于", "之", "向", "从", "把", "被", "且", "等等",
            "不可", "编辑", "不可编辑", "可编辑", "可编", "标讯", "标包", "标段", "测试"
    );

    // 通用分词与空白模式
    private static final Pattern PUNCT_SPACE_PATTERN = Pattern.compile("[\\p{Punct}\\p{Space}\\u3000-\\u303F\\uFF00-\\uFFEF]+");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern TOKENIZE_SPLIT_PATTERN = Pattern.compile("\\s+|[，。、；：！？\"'（）【】]");

    // 标题编号前缀剥离模式（精确保留 ISO9001/CMMI/B2B/ERP/AI 等英文缩写与品牌名）
    private static final Pattern[] TITLE_PREFIX_PATTERNS = {
            Pattern.compile("^(第[0-9一二三四五六七八九十百\\._]+[章节条款项部分节]+|附[件表]+[0-9一二三四五六七八九十\\-_\\.]*|表[0-9\\-_\\.]+)[、\\.\\s:：\\-_]*"),
            Pattern.compile("^(([0-9]+([\\.\\-_][0-9]+)+|[A-Za-z]{1,2}[\\-_][0-9]+([\\.\\-_][0-9]+)*)([\\(（][0-9一二三四五六七八九十a-zA-Z]+[\\)）])?|[0-9]+[\\(（][0-9一二三四五六七八九十a-zA-Z]+[\\)）]|([0-9]{1,3}|[A-Za-z]{1,2}|[A-Za-z][0-9]{1,2})[、\\.:：])[、\\.\\s:：\\-_]*"),
            Pattern.compile("^[\\(（\\[【][0-9一二三四五六七八九十a-zA-Z]+[\\)）\\]】][、\\.\\s:：\\-_]*"),
            Pattern.compile("^[①②③④⑤⑥⑦⑧⑨⑩㈠㈡㈢㈣㈤㈥㈦㈧㈨㈩ⅠⅡⅢⅣⅤⅥⅦⅧⅨⅩ][、\\.\\s:：\\-_]*"),
            Pattern.compile("^[一二三四五六七八九十]+[、\\.\\s:：\\-_]+")
    };

    // 组织专有名词提取模式
    private static final Pattern ORG_DELIMITER_PATTERN = Pattern.compile(
            "(?i)[0-9]{4}年?|采购|招标|项目|工程|服务|系统|平台|建设|改造|升级|运维|开发|咨询|实施|电商|工业品|办公|物资|设备|信息化|数字化|数智化|不可编辑|可编辑|标讯|标包|标段|测试"
    );
    private static final Pattern ORG_SUFFIX_PATTERN = Pattern.compile("有限公司|有限责任公司|股份有限公司|分公司|公司|集团|厂|局|院");
    private static final Pattern ORG_GEO_PREFIX_PATTERN = Pattern.compile(
            "^(中国|中央|国家|全国|全省|华东|华北|华南|华中|西南|西北|东北|长江|黄河|珠江|[\\u4e00-\\u9fa5]{2,4}(?:省|市|自治区|特区))"
    );

    private TextSimilarityPolicy() {}

    public static Set<String> tokenize(String text) {
        if (!hasText(text)) {
            return Collections.emptySet();
        }
        Set<String> tokens = new HashSet<>();
        String normalized = text.toLowerCase(Locale.ROOT);
        for (String segment : TOKENIZE_SPLIT_PATTERN.split(normalized)) {
            String trimmed = segment.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (!containsCjk(trimmed) && trimmed.length() >= 2) {
                tokens.add(trimmed);
            } else {
                for (int i = 0; i < trimmed.length() - 1; i++) {
                    tokens.add(trimmed.substring(i, i + 2));
                }
            }
        }
        return tokens;
    }

    public static Set<String> extractPunctuationEntities(String... texts) {
        return PunctuationEntityExtractor.extractPunctuationEntities(texts);
    }

    public static String cleanEntityToken(String token) {
        return PunctuationEntityExtractor.cleanEntityToken(token);
    }

    public static boolean isQualityEntity(String token) {
        return PunctuationEntityExtractor.isQualityEntity(token);
    }

    public static boolean isParticle(char ch) {
        return PunctuationEntityExtractor.isParticle(ch);
    }

    public static Set<String> extractTokens(String text) {
        if (!hasText(text)) {
            return Collections.emptySet();
        }
        Set<String> tokens = new LinkedHashSet<>(PunctuationEntityExtractor.extractPunctuationEntities(text));
        String stripped = PUNCT_SPACE_PATTERN.matcher(text).replaceAll(" ").trim();
        if (!stripped.isEmpty()) {
            for (String word : WHITESPACE_PATTERN.split(stripped)) {
                String cleanWord = cleanTitle(word);
                int len = cleanWord.length();
                if (len >= 2 && len <= 20) {
                    if (isQualityEntity(cleanWord)) {
                        tokens.add(cleanWord);
                    }
                }
                if (containsCjk(cleanWord)) {
                    tokens.addAll(segmentCjk(cleanWord));
                }
            }
        }
        return tokens;
    }

    private static Set<String> segmentCjk(String text) {
        Set<String> result = new LinkedHashSet<>();
        int n = text.length();
        int i = 0;
        while (i < n) {
            char c = text.charAt(i);
            if (isModalOrParticle(c)) {
                i++;
                continue;
            }
            boolean matched = false;
            int maxLen = Math.min(8, n - i);
            for (int len = maxLen; len >= 2; len--) {
                String sub = text.substring(i, i + len);
                if (BiddingDomainLexicon.contains(sub)) {
                    result.add(sub);
                    if (len == 4) {
                        String p1 = sub.substring(0, 2);
                        String p2 = sub.substring(2, 4);
                        if (BiddingDomainLexicon.contains(p1)) {
                            result.add(p1);
                        }
                        if (BiddingDomainLexicon.contains(p2)) {
                            result.add(p2);
                        }
                    }
                    i += len;
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                if (i + 1 < n && hasLexiconMatch(text, i + 1, n)) {
                    i++;
                    continue;
                }
                if (i + 2 <= n) {
                    String sub2 = text.substring(i, i + 2);
                    if (isQualityEntity(sub2) && !containsModalOrParticle(sub2)) {
                        result.add(sub2);
                        i += 2;
                    } else {
                        i++;
                    }
                } else {
                    i++;
                }
            }
        }
        return result;
    }

    private static boolean hasLexiconMatch(String text, int start, int end) {
        int maxLen = Math.min(8, end - start);
        for (int len = maxLen; len >= 2; len--) {
            if (BiddingDomainLexicon.contains(text.substring(start, start + len))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isModalOrParticle(char ch) {
        return PunctuationEntityExtractor.isParticle(ch)
                || ch == '须' || ch == '应' || ch == '需' || ch == '较' || ch == '并'
                || ch == '且' || ch == '及' || ch == '与' || ch == '或' || ch == '等'
                || ch == '把' || ch == '被' || ch == '从' || ch == '向' || ch == '由'
                || ch == '于' || ch == '在' || ch == '是' || ch == '为' || ch == '以';
    }

    private static boolean containsModalOrParticle(String s) {
        if (s == null || s.length() < 2) return false;
        return isModalOrParticle(s.charAt(0)) || isModalOrParticle(s.charAt(s.length() - 1));
    }

    public static double jaccardSimilarity(Set<String> a, Set<String> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        if (intersection.isEmpty()) {
            return 0.0;
        }
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return (double) intersection.size() / union.size();
    }

    public static double jaccardSimilarityOfText(String textA, String textB) {
        return jaccardSimilarity(tokenize(textA), tokenize(textB));
    }

    public static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    public static boolean containsCjk(String text) {
        return text.codePoints().anyMatch(cp -> {
            Character.UnicodeBlock block = Character.UnicodeBlock.of(cp);
            return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                    || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                    || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS;
        });
    }

    /**
     * 剥离前缀编号，提取纯净业务名称。
     */
    public static String cleanTitle(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String cleaned = text.trim();
        String prev;
        int maxIter = 5;
        do {
            prev = cleaned;
            for (Pattern p : TITLE_PREFIX_PATTERNS) {
                cleaned = p.matcher(cleaned).replaceAll("");
            }
            cleaned = cleaned.trim();
            maxIter--;
        } while (!cleaned.equals(prev) && !cleaned.isEmpty() && maxIter > 0);

        return cleaned.isEmpty() ? text.trim() : cleaned;
    }

    public static Set<String> extractEntityTokens(String... texts) {
        if (texts == null) {
            return Collections.emptySet();
        }
        Set<String> entities = new LinkedHashSet<>();
        for (String text : texts) {
            if (!hasText(text)) {
                continue;
            }
            String[] segments = ORG_DELIMITER_PATTERN.split(text);
            String orgSegment = (segments.length > 0 && hasText(segments[0])) ? segments[0] : text;
            String cleanedWithoutSuffix = ORG_SUFFIX_PATTERN.matcher(orgSegment).replaceAll(" ");
            String cleaned = PUNCT_SPACE_PATTERN.matcher(cleanedWithoutSuffix).replaceAll(" ").trim();
            for (String part : WHITESPACE_PATTERN.split(cleaned)) {
                String cleanPart = cleanEntityToken(part);
                if (cleanPart.length() >= 2 && !STOP_WORDS.contains(cleanPart) && isQualityEntity(cleanPart)) {
                    entities.addAll(extractHierarchicalEntities(cleanPart));
                }
            }
        }
        return entities;
    }

    private static Set<String> extractHierarchicalEntities(String rawEntity) {
        Set<String> result = new LinkedHashSet<>();
        String current = rawEntity;
        while (current.length() >= 2 && !STOP_WORDS.contains(current) && isQualityEntity(current)) {
            result.add(current);
            Matcher matcher = ORG_GEO_PREFIX_PATTERN.matcher(current);
            if (matcher.find()) {
                String matchedPrefix = matcher.group(1);
                String remainder = cleanEntityToken(current.substring(matchedPrefix.length()));
                if (!remainder.equals(current) && remainder.length() >= 2) {
                    current = remainder;
                    continue;
                }
            }
            break;
        }
        return result;
    }
}
