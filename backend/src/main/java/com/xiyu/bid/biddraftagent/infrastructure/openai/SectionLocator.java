// Input: 招标文件全文文本 + 别名词表
// Output: 定位到的章节文本片段（Optional<String>）
// Pos: biddraftagent/infrastructure/openai - 通用章节定位器
package com.xiyu.bid.biddraftagent.infrastructure.openai;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 通用章节定位器。
 *
 * <p>从招标文件全文中定位指定别名词表对应的章节文本范围。
 * 泛化自 {@link ScoringSectionLocator}，支持任意维度的章节定位。
 *
 * <p>章节边界判定策略：
 * <ul>
 *   <li>下一行匹配"第X章/第X条"格式且不含本维度别名 -> 章节结束</li>
 *   <li>连续 3 个空行 -> 章节结束</li>
 *   <li>文件末尾 -> 自然结束</li>
 * </ul>
 */
final class SectionLocator {

    private static final String WHITESPACE_PATTERN =
            "[\\s\\u00A0\\u00AD\\u2000-\\u200D\\u2028-\\u202F\\u205F\\u2060\\u3000\\uFEFF]+";

    private static final java.util.regex.Pattern NEXT_CHAPTER_PATTERN =
            java.util.regex.Pattern.compile("^第[一二三四五六七八九十百千万\\d]+[章节条]");

    private static final int BLANK_LINES_TO_END = 3;

    private SectionLocator() {
    }

    /**
     * 从全文中定位指定别名列表对应的章节文本。
     *
     * @param fullText 招标文件全文文本
     * @param aliases  章节标题别名词表
     * @return 章节文本；未找到时返回 {@link Optional#empty()}
     */
    static Optional<String> locate(String fullText, List<String> aliases) {
        if (fullText == null || fullText.isBlank() || aliases == null || aliases.isEmpty()) {
            return Optional.empty();
        }

        String[] lines = fullText.split("\\R");
        int startIndex = findSectionStart(lines, aliases);
        if (startIndex < 0) {
            return Optional.empty();
        }

        List<String> sectionLines = collectSectionLines(lines, startIndex, aliases);
        if (sectionLines.isEmpty()) {
            return Optional.empty();
        }

        String section = String.join("\n", sectionLines).trim();
        return section.isBlank() ? Optional.empty() : Optional.of(section);
    }

    private static int findSectionStart(String[] lines, List<String> aliases) {
        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String normalized = normalize(raw);
            if (isSectionTitle(normalized, aliases)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isSectionTitle(String normalizedLine, List<String> aliases) {
        if (normalizedLine.length() > 30) {
            return false;
        }
        if (normalizedLine.contains("详见") || normalizedLine.contains("参见")
                || normalizedLine.contains("按照") || normalizedLine.contains("根据")) {
            return false;
        }
        for (String alias : aliases) {
            if (normalizedLine.contains(alias)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> collectSectionLines(String[] lines, int startIndex, List<String> aliases) {
        List<String> collected = new ArrayList<>();
        int consecutiveBlanks = 0;

        for (int i = startIndex; i < lines.length; i++) {
            String line = lines[i];

            if (i > startIndex && line != null && !line.isBlank()) {
                String normalized = normalize(line);
                if (NEXT_CHAPTER_PATTERN.matcher(normalized).find() && !containsAlias(normalized, aliases)) {
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

        while (!collected.isEmpty() && (collected.get(collected.size() - 1) == null
                || collected.get(collected.size() - 1).isBlank())) {
            collected.remove(collected.size() - 1);
        }

        return collected;
    }

    private static boolean containsAlias(String normalized, List<String> aliases) {
        for (String alias : aliases) {
            if (normalized.contains(alias)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String text) {
        if (text == null) return "";
        return text.replaceAll(WHITESPACE_PATTERN, "");
    }
}
