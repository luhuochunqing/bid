// Input: 原始提取的编号文本（如 "null.1", "1.2", "null", "一、"）
// Output: 清洗后的干净编号（空或纯净编号，无 null/undefined 污染）
// Pos: scoreparse/domain — 纯核心，无框架依赖
// 维护声明: 维护者按项目SOP；spec 046 / spec 058 编号收口防线
package com.xiyu.bid.scoreparse.domain;

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
            "(?i)(?:^|(?<=[._/\\\\\\-\\s]))(?:null|undefined|none|n/?a)(?:(?=[._/\\\\\\-\\s])|$)"
    );

    private ScoreCodeSanitizer() {
    }

    /**
     * 清洗编号，返回纯净的编号字符串；若无有效编号则返回空字符串 ""。
     * <p>归一化说明（行为变更备忘）：
     * <ul>
     *   <li>NFKC 将全角/兼容字符转为半角：ｎｕｌｌ→null，（null）→(null)，．／→./，①→1，２→2；</li>
     *   <li>圈号/上标/连字同样归一（⑩→10，²→2，ﬁ→fi）——编号若真用此类字符区分会被改写，
     *   当前按"招标编号不用圈号/上标区分"接受，改动需同步更新本注释与单测；</li>
     *   <li>CJK 表意字不受 NFKC 影响（一/一、/二、/一百原样保留）；U+3001 、非空格不参与切分；</li>
     *   <li>零宽字符（U+200B-200D/FEFF）在 NFKC 之前显式剥除；长度截断 50 字符。</li>
     * </ul>
     */
    public static String clean(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        // 剥除零宽字符与全角字符归一化（如 ｎｕｌｌ -> null，（null） -> (null)）
        s = s.replaceAll("[\\u200B-\\u200D\\uFEFF]", "");
        s = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFKC).trim();
        if (s.isEmpty() || NULL_EXACT_PATTERN.matcher(s).matches()) {
            return "";
        }

        // 清除括号包裹的 null，如 "(null)1" -> "1"
        s = s.replaceAll("(?i)\\([\\s]*null[\\s]*\\)", "");

        // 逐段清洗：将连接符周围的 null/undefined 剔除
        // 例如 "null.1" -> ".1" -> "1", "1.null" -> "1.", "1.null.2" -> "1..2", "null/1" -> "1"
        String cleaned = NULL_SEGMENT_CLEAN.matcher(s).replaceAll("");

        // 清理多余的前导、尾随或重复分隔符，如 "..", "--", 首尾点
        // 分隔符集含反斜杠（"null\1" -> "1"，与前端 sanitizeCode 对齐）
        cleaned = cleaned.replaceAll("[._/\\\\\\-\\s]{2,}", ".");
        cleaned = cleaned.replaceAll("^[._/\\\\\\-\\s]+", "");
        cleaned = cleaned.replaceAll("[._/\\\\\\-\\s]+$", "");

        if (cleaned.isEmpty() || NULL_EXACT_PATTERN.matcher(cleaned).matches()) {
            return "";
        }

        // 安全长度截断（最多 50 字符）
        return cleaned.length() > 50 ? cleaned.substring(0, 50) : cleaned;
    }
}
