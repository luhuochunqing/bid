// Input: 原文片段（chunk 文本）+ LLM 输出文本（dim/detail/sourceText）
// Output: 原文中对应的精确片段（保留原文空白与换行）；未命中返回 null
// Pos: scoreparse/domain — LLM 输出原文保真对齐（纯核心，无框架依赖）
package com.xiyu.bid.scoreparse.domain;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM 输出与招标文件原文的空白弹性对齐器。
 *
 * <p>设计动机：评分标准解析的 LLM 输出（detail/dim/sourceText）即使 prompt 要求
 * "完整原文表述、禁止改写"，模型仍可能出现空白差异（全角/半角、换行合并、中文断词加空格）。
 * 本对齐器把 LLM 输出按空白拆段，构造空白弹性的正则在原文中定位，
 * 命中时返回<b>原文的精确片段</b>（逐字保留原文标点与空白），从而保证落库展示的文字与
 * 招标文件评分表原文一致；未命中（模型概括/改写导致子串关系不成立）返回 null，
 * 由调用方保留 LLM 原输出作为降级。
 *
 * <p>段间空白允许零个：中文原文通常无空格，模型可能输出"技术方案 完整性"
 * 对应原文"技术方案完整性"，零空白宽容可让此类输出对齐成功。
 */
public final class ScoreOriginalTextAligner {

    /** Unicode 空白类（与 ScoringItemExtractor.WHITESPACE_PATTERN 口径一致，覆盖全角空格/NBSP 等）。 */
    private static final String WHITESPACE_CLASS =
            "[\\s\\u00A0\\u00AD\\u2000-\\u200D\\u2028-\\u202F\\u205F\\u2060\\u3000\\uFEFF]";

    private ScoreOriginalTextAligner() {
    }

    /**
     * 在原文中定位 LLM 输出对应的精确原文片段。
     *
     * @param originalText 原文（chunk / 全文节选）
     * @param llmText      LLM 输出文本
     * @return 原文精确片段（忽略空白差异后构成子串时）；无法对齐返回 null
     */
    public static String align(String originalText, String llmText) {
        if (originalText == null || originalText.isBlank() || llmText == null) {
            return null;
        }
        String trimmed = llmText.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        Pattern pattern = Pattern.compile(buildFlexibleRegex(trimmed));
        Matcher matcher = pattern.matcher(originalText);
        return matcher.find() ? matcher.group() : null;
    }

    /** 按空白拆段 → 每段字面量转义 → 段间空白弹性（零或多个 Unicode 空白，含换行）。 */
    private static String buildFlexibleRegex(String trimmed) {
        String[] segments = trimmed.split(WHITESPACE_CLASS + "+");
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                regex.append(WHITESPACE_CLASS).append('*');
            }
            regex.append(Pattern.quote(segments[i]));
        }
        return regex.toString();
    }
}
