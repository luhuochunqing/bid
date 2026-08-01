// Input: 章节文本（由 SectionLocator 定位）
// Output: List<String> 需求条目列表
// Pos: biddraftagent/infrastructure/openai - 需求条目提取器
package com.xiyu.bid.biddraftagent.infrastructure.openai;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 需求条目提取器。
 *
 * <p>从定位到的章节文本中提取需求条目行（List<String>）。
 * 适用于资质、技术、商务、风险等非结构化维度。
 *
 * <p>提取规则：
 * <ul>
 *   <li>跳过章节标题行（含别名关键词的短行）</li>
 *   <li>跳过表头行（"序号"/"内容"/"要求"等）</li>
 *   <li>跳过纯描述性文字（无编号且超过 100 字的行视为描述）</li>
 *   <li>收集编号行（1./1.1/A1/（1）等开头）和非编号的短需求行</li>
 *   <li>遇到下一章节标题（第X章/第X条）时停止</li>
 * </ul>
 */
final class RequirementExtractor {

    private static final String WHITESPACE_PATTERN =
            "[\\s\\u00A0\\u00AD\\u2000-\\u200D\\u2028-\\u202F\\u205F\\u2060\\u3000\\uFEFF]+";

    private static final Pattern NEXT_CHAPTER_PATTERN =
            Pattern.compile("^第[一二三四五六七八九十百千万\\d]+[章节条]");

    private static final Pattern ITEM_NUMBER_PATTERN =
            Pattern.compile("^(?:[A-Z]?\\d+[\\.、）)]?\\s*|[（(]\\d+[)）]\\s*|\\d+[\\.、]\\s*)");

    private static final List<String> HEADER_KEYWORDS = List.of(
            "序号", "内容", "要求", "条款", "说明", "备注", "项目");

    private RequirementExtractor() {
    }

    /**
     * 从章节文本中提取需求条目。
     *
     * @param sectionText 章节文本
     * @param aliases     本维度的别名词表（用于排除标题行）
     * @return 需求条目列表；无匹配时返回空列表
     */
    static List<String> extract(String sectionText, List<String> aliases) {
        if (sectionText == null || sectionText.isBlank()) {
            return List.of();
        }

        List<String> items = new ArrayList<>();
        for (String rawLine : sectionText.split("\\R")) {
            if (rawLine == null || rawLine.isBlank()) {
                continue;
            }
            String normalized = normalizeSpaces(rawLine);
            if (isHeaderOrNoise(normalized, aliases)) {
                continue;
            }
            if (NEXT_CHAPTER_PATTERN.matcher(normalized).find() && !containsAlias(normalized, aliases)) {
                break;
            }

            String item = normalized.trim();
            if (item.length() >= 4) {
                items.add(item);
            }
        }
        return items;
    }

    private static boolean isHeaderOrNoise(String normalized, List<String> aliases) {
        if (normalized.length() > 200) {
            return true;
        }
        // 章节标题行（含别名且很短，通常 <= 15 字）
        if (normalized.length() <= 15) {
            for (String alias : aliases) {
                if (normalized.contains(alias)) {
                    return true;
                }
            }
        }
        // 表头行（以关键字开头或等于关键字的短行）
        if (normalized.length() <= 20) {
            for (String keyword : HEADER_KEYWORDS) {
                if (normalized.startsWith(keyword) || normalized.equals(keyword)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsAlias(String normalized, List<String> aliases) {
        for (String alias : aliases) {
            if (normalized.contains(alias)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeSpaces(String text) {
        if (text == null) return "";
        return text.replaceAll(WHITESPACE_PATTERN, " ").trim();
    }
}
