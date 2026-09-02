package com.xiyu.bid.casework.domain.policy;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

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
            "负责", "落实", "关于", "等", "及", "与", "或"
    );

    private TextSimilarityPolicy() {}

    /**
     * 对文本进行分词，返回 token 集合。
     */
    public static Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();
        if (text == null || text.isBlank()) {
            return tokens;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        for (String segment : normalized.split("\\s+|[，。、；：！？\"'（）【】]")) {
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

    /**
     * 针对招投标评分条款的增强分词（含编号剥离与 n-gram 抽取）。
     */
    public static Set<String> extractTokens(String text) {
        if (!hasText(text)) {
            return Collections.emptySet();
        }

        Set<String> tokens = new LinkedHashSet<>();
        String[] rawWords = text.replaceAll("[\\p{Punct}\\p{Space}\\u3000-\\u303F\\uFF00-\\uFFEF]+", " ").trim().split("\\s+");
        for (String word : rawWords) {
            String cleanWord = cleanTitle(word);
            if (cleanWord.length() >= 2 && cleanWord.length() <= 8) {
                tokens.add(cleanWord);
            }
            if (cleanWord.matches(".*[\\u4e00-\\u9fa5].*")) {
                for (int i = 0; i <= cleanWord.length() - 2; i++) {
                    tokens.add(cleanWord.substring(i, i + 2));
                }
                for (int i = 0; i <= cleanWord.length() - 3; i++) {
                    tokens.add(cleanWord.substring(i, i + 3));
                }
            }
        }
        return tokens;
    }

    /**
     * 计算两个 token 集合的 Jaccard 相似度。
     */
    public static double jaccardSimilarity(Set<String> a, Set<String> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        int intersectionSize = intersection.size();
        if (intersectionSize == 0) {
            return 0.0;
        }
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return (double) intersectionSize / union.size();
    }

    /**
     * 计算两个文本的 Jaccard 相似度（自动分词）。
     */
    public static double jaccardSimilarityOfText(String textA, String textB) {
        return jaccardSimilarity(tokenize(textA), tokenize(textB));
    }

    /**
     * 判断字符串是否有实际文本内容（非 null 且非空白）。
     */
    public static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    /**
     * 判断文本是否包含中日韩统一表意文字。
     */
    public static boolean containsCjk(String text) {
        return text.codePoints().anyMatch(cp -> {
            Character.UnicodeBlock block = Character.UnicodeBlock.of(cp);
            return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                    || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                    || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS;
        });
    }

    /**
     * 剥离前缀编号（支持多层级数字、中英文章节、带括号序号、罗马数字、特殊序号等），提取纯净业务名称。
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
            cleaned = cleaned.replaceAll("^(第[0-9一二三四五六七八九十百\\._]+[章节条款项部分节]+|附[件表]+[0-9一二三四五六七八九十\\-_\\.]*|表[0-9\\-_\\.]+)[、\\.\\s:：\\-_]*", "");
            cleaned = cleaned.replaceAll("^[A-Za-z0-9]+([\\.\\-_][A-Za-z0-9]+)*([\\(（][0-9一二三四五六七八九十a-zA-Z]+[\\)）])?[、\\.\\s:：\\-_]*", "");
            cleaned = cleaned.replaceAll("^[\\(（\\[【][0-9一二三四五六七八九十a-zA-Z]+[\\)）\\]】][、\\.\\s:：\\-_]*", "");
            cleaned = cleaned.replaceAll("^[①②③④⑤⑥⑦⑧⑨⑩㈠㈡㈢㈣㈤㈥㈦㈧㈨㈩ⅠⅡⅢⅣⅤⅥⅦⅧⅨⅩ][、\\.\\s:：\\-_]*", "");
            cleaned = cleaned.replaceAll("^[一二三四五六七八九十]+[、\\.\\s:：\\-_]+", "");
            cleaned = cleaned.trim();
            maxIter--;
        } while (!cleaned.equals(prev) && !cleaned.isEmpty() && maxIter > 0);

        return cleaned.isEmpty() ? text.trim() : cleaned;
    }

    /**
     * 从项目名称、客户名称等文本中提取具有高辨识度的核心实体词/组织专有名词。
     */
    public static Set<String> extractEntityTokens(String... texts) {
        if (texts == null) {
            return Collections.emptySet();
        }
        Set<String> entities = new LinkedHashSet<>();
        for (String text : texts) {
            if (!hasText(text)) {
                continue;
            }
            // 1. 优先提取项目名称中的组织主体前缀（在年份/业务动作词之前的部分）
            String[] segments = text.split("(?i)[0-9]{4}年?|采购|招标|工程|服务|系统|平台|建设|改造|升级|运维|开发|咨询|实施|电商|工业品|办公|物资|设备|信息化|数字化|数智化");
            String orgSegment = (segments.length > 0 && hasText(segments[0])) ? segments[0] : text;

            String cleaned = orgSegment
                    .replaceAll("(有限公司|有限责任公司|股份有限公司|分公司|公司|集团|厂|局|院)", " ")
                    .replaceAll("[\\p{Punct}\\p{Space}\\u3000-\\u303F\\uFF00-\\uFFEF]+", " ")
                    .trim();

            for (String part : cleaned.split("\\s+")) {
                String cleanPart = cleanTitle(part);
                if (cleanPart.length() >= 2 && !STOP_WORDS.contains(cleanPart)) {
                    entities.add(cleanPart);
                    // 对 4 字及以上的复合专有名词，提取 2-3 字核心子词（如 "中国长江三峡" -> "长江三峡", "三峡"）
                    if (cleanPart.length() >= 4) {
                        for (int i = 0; i <= cleanPart.length() - 2; i++) {
                            String sub = cleanPart.substring(i, i + 2);
                            if (!STOP_WORDS.contains(sub)) {
                                entities.add(sub);
                            }
                        }
                    }
                }
            }
        }
        return entities;
    }
}
