package com.xiyu.bid.scoreparse.domain;

import java.util.Objects;

/**
 * 第一性原理文本包含校验器。
 * <p>
 * 纯函数数学防伪：校验模型/提取器摘录的细则文本是否真实物理存在于招标文件原文中，
 * 彻底消除 AI 幻觉，同时摆脱对表格几何网格的硬性依赖。
 */
public final class TextInclusionVerifier {

    private TextInclusionVerifier() {}

    public record Result(
            boolean verified,
            boolean exact,
            boolean normalized,
            String matchedText
    ) {
        public static Result unverified() {
            return new Result(false, false, false, "");
        }

        public static Result exact(String text) {
            return new Result(true, true, false, text);
        }

        public static Result normalized(String text) {
            return new Result(true, false, true, text);
        }
    }

    /**
     * 校验规则文本是否包含于标书全文中。
     *
     * @param ruleText 候选细则文本
     * @param documentText 标书全文或章节全文
     * @return 校验结果
     */
    public static Result verify(String ruleText, String documentText) {
        if (ruleText == null || ruleText.isBlank() || documentText == null || documentText.isBlank()) {
            return Result.unverified();
        }

        String trimmedRule = ruleText.trim();
        // 1. 字面完全包含
        if (documentText.contains(trimmedRule)) {
            return Result.exact(trimmedRule);
        }

        // 2. 标点与空白归一化包含
        String normDoc = normalize(documentText);
        String normRule = normalize(trimmedRule);

        if (!normRule.isEmpty() && normDoc.contains(normRule)) {
            return Result.normalized(trimmedRule);
        }

        return Result.unverified();
    }

    /**
     * 去除空白换行、统一中英文标点为半角规范字符。
     */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("[\\s\\r\\n\\t\\u3000\\u00a0]+", "")
                .toLowerCase()
                .replace('，', ',')
                .replace('。', '.')
                .replace('；', ';')
                .replace('：', ':')
                .replace('（', '(')
                .replace('）', ')')
                .replace('【', '[')
                .replace('】', ']')
                .replace('“', '"')
                .replace('”', '"')
                .replace('‘', '\'')
                .replace('’', '\'')
                .replace('、', ',');
    }
}
