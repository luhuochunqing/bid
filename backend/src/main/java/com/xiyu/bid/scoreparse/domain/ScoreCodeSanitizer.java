// Input: 原始提取的编号文本（如 "null.1", "1.2", "null", "一、"）
// Output: 清洗后的干净编号（空或纯净编号，无 null/undefined 污染）
// Pos: scoreparse/domain — 纯核心，无框架依赖
// 维护声明: 维护者按项目SOP；spec 046 / spec 058 编号收口防线
package com.xiyu.bid.scoreparse.domain;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 评分项编号清洗器（解决全链路 "null.1" / "null" / "undefined" 污染）。
 * <p>核心纪律：
 * <ul>
 *   <li>1. 根除字面量 "null"、"undefined"、"none"、"n/a" 等占位符，统一清洗为空串 ""；</li>
 *   <li>2. 逐段切分清洗复合编号（如 "null.1" -> "1", "null-2" -> "2", "1.null" -> "1", "1.null.2" -> "1.2"）；</li>
 *   <li>3. 严禁误伤中文数字章节编号（如 "一"、"一、"、"二"、"十." 是合法招标文件大项编号）；</li>
 *   <li>4. 在领域模型 ScoreCandidate 构造与各入口收口，防止脏数据扩散。</li>
 * </ul>
 */
public final class ScoreCodeSanitizer {

    private static final Pattern NULL_EXACT_PATTERN = Pattern.compile(
            "^(?:null|undefined|none|n/?a|\\(null\\)|（null）|null\\.0)$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern NULL_SEGMENT_CLEAN = Pattern.compile(
            "(?i)(?:^|(?<=[._/\\-\\s]))(?:null|undefined|none|n/?a)(?:(?=[._/\\-\\s])|$)"
    );

    private ScoreCodeSanitizer() {
    }

    /**
     * 清洗编号，返回纯净的编号字符串；若无有效编号则返回空字符串 ""。
     */
    public static String clean(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        if (s.isEmpty() || NULL_EXACT_PATTERN.matcher(s).matches()) {
            return "";
        }

        // 逐段清洗：将连接符周围的 null/undefined 剔除
        // 例如 "null.1" -> ".1" -> "1", "1.null" -> "1.", "1.null.2" -> "1..2"
        String cleaned = NULL_SEGMENT_CLEAN.matcher(s).replaceAll("");

        // 清理多余的前导、尾随或重复分隔符，如 "..", "--", 首尾点
        cleaned = cleaned.replaceAll("[._/\\-\\s]{2,}", ".");
        cleaned = cleaned.replaceAll("^[._/\\-\\s]+", "");
        cleaned = cleaned.replaceAll("[._/\\-\\s]+$", "");

        if (cleaned.isEmpty() || NULL_EXACT_PATTERN.matcher(cleaned).matches()) {
            return "";
        }

        // 安全长度截断（最多 50 字符）
        return cleaned.length() > 50 ? cleaned.substring(0, 50) : cleaned;
    }
}
