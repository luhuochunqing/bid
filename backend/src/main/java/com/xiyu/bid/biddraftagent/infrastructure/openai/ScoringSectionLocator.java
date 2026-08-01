// Input: 招标文件全文文本
// Output: 评分标准章节的文本片段（Optional<String>）
// Pos: biddraftagent/infrastructure/openai - 评分标准章节定位器（单一职责）
package com.xiyu.bid.biddraftagent.infrastructure.openai;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 评分标准章节定位器。
 *
 * <p>设计动机：招标文件按 4000 字符分块后送 AI 提取，评分表可能被切断导致
 * AI 漏提或错提。本类从全文中精准定位评分标准章节的文本范围，为后续
 * 正则兜底和 Prompt 聚焦提供基础。
 *
 * <p>对标 {@link PurchaserNameExtractor} 的设计模式：
 * <ul>
 *   <li>归一化匹配 - 复用 {@link TenderIntakeTextProcessor} 的空白归一化逻辑</li>
 *   <li>标签行优先 - 章节标题通常是独立成行的，匹配别名命中行</li>
 *   <li>章节边界判定 - 遇到下一个"第X章/第X条"格式行时结束</li>
 * </ul>
 *
 * <p>章节边界判定策略：
 * <ul>
 *   <li>下一行匹配"第X章"或"第X条"格式 -> 章节结束</li>
 *   <li>连续 3 个空行 -> 章节结束（保守回退）</li>
 *   <li>文件末尾 -> 自然结束</li>
 * </ul>
 */
final class ScoringSectionLocator {

    /** 归一化正则，与 TenderIntakeTextProcessor 保持一致。 */
    private static final String WHITESPACE_PATTERN =
            "[\\s\\u00A0\\u00AD\\u2000-\\u200D\\u2028-\\u202F\\u205F\\u2060\\u3000\\uFEFF]+";

    /** 下一章节标题格式：第X章、第X条。 */
    private static final java.util.regex.Pattern NEXT_CHAPTER_PATTERN =
            java.util.regex.Pattern.compile("^第[一二三四五六七八九十百千万\\d]+[章节条]");

    /** 连续空行触发章节结束的阈值。 */
    private static final int BLANK_LINES_TO_END = 3;

    private ScoringSectionLocator() {
    }

    /**
     * 从全文中定位评分标准章节文本。
     *
     * <p>按行扫描全文，归一化后匹配 {@link ScoringSectionAliases#ALL} 中的别名标签。
     * 命中行视为章节起始，收集到下一章节标题（第X章/第X条）或连续空行时结束。
     *
     * @param fullText 招标文件全文文本
     * @return 评分标准章节文本；未找到时返回 {@link Optional#empty()}
     */
    static Optional<String> locate(String fullText) {
        if (fullText == null || fullText.isBlank()) {
            return Optional.empty();
        }

        String[] lines = fullText.split("\\R");
        int startIndex = findScoringSectionStart(lines);
        if (startIndex < 0) {
            return Optional.empty();
        }

        List<String> sectionLines = collectSectionLines(lines, startIndex);
        if (sectionLines.isEmpty()) {
            return Optional.empty();
        }

        String section = String.join("\n", sectionLines).trim();
        return section.isBlank() ? Optional.empty() : Optional.of(section);
    }

    /**
     * 找到评分标准章节的起始行索引。
     * 别名标签必须出现在行首或占据整行（章节标题特征），排除描述性文字中的别名。
     */
    private static int findScoringSectionStart(String[] lines) {
        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String normalized = normalize(raw);
            if (isScoringSectionTitle(normalized)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 判断归一化后的行是否为评分标准章节标题。
     * 判定条件：行文本包含某个别名，且行长度不超过别名+15字符
     * （允许"第二章评标办法"、"评标办法（附表）"等变体）。
     * 排除描述性文字：行中不能包含"详见"/"参见"/"按照"/"根据"等引用词。
     */
    private static boolean isScoringSectionTitle(String normalizedLine) {
        if (normalizedLine.length() > 30) {
            return false;
        }
        if (normalizedLine.contains("详见") || normalizedLine.contains("参见")
                || normalizedLine.contains("按照") || normalizedLine.contains("根据")) {
            return false;
        }
        for (String alias : ScoringSectionAliases.ALL) {
            if (normalizedLine.contains(alias)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从起始行收集章节内容行，直到遇到下一章节标题或连续空行。
     */
    private static List<String> collectSectionLines(String[] lines, int startIndex) {
        List<String> collected = new ArrayList<>();
        int consecutiveBlanks = 0;

        for (int i = startIndex; i < lines.length; i++) {
            String line = lines[i];
            // 跳过起始标题行本身之前的空行
            if (i > startIndex && line != null && !line.isBlank()) {
                String normalized = normalize(line);
                if (NEXT_CHAPTER_PATTERN.matcher(normalized).find()) {
                    break;
                }
            }

            if (line == null || line.isBlank()) {
                consecutiveBlanks++;
                if (consecutiveBlanks >= BLANK_LINES_TO_END && !collected.isEmpty()) {
                    break;
                }
            } else {
                consecutiveBlanks = 0;
            }
            collected.add(line);
        }

        // 去除尾部空行
        while (!collected.isEmpty() && (collected.get(collected.size() - 1) == null
                || collected.get(collected.size() - 1).isBlank())) {
            collected.remove(collected.size() - 1);
        }

        return collected;
    }

    private static String normalize(String text) {
        if (text == null) return "";
        return text.replaceAll(WHITESPACE_PATTERN, "");
    }
}
