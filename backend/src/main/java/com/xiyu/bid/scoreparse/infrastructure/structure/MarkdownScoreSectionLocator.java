// Input: 招标文件 Markdown 全文（doc-insight 转换产物）
// Output: 评分标准候选区域列表（含前后文与章节标题）
// Pos: scoreparse/infrastructure/structure — 纯静态方法，无框架依赖
// 维护声明: 维护者按项目SOP；spec 041 FR-001 召回二（文档结构解析）

package com.xiyu.bid.scoreparse.infrastructure.structure;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 文档结构召回定位器（spec 041 FR-001 召回二）。
 * <p>从 Markdown 全文中定位评分标准候选区域：
 * <ul>
 *   <li>含评分关键词的标题章节（评标办法/评分标准/评分细则/评分规则）下的表格</li>
 *   <li>任意含分值特征（分值/满分/得分 + 数字）的 Markdown 表格</li>
 *   <li>候选区域保留表格前后若干行上下文（注/说明常在表后）</li>
 * </ul>
 * <p>纯静态方法，输出交由四路召回合并层去重。
 */
public final class MarkdownScoreSectionLocator {

    /** 评分标准标题关键词 */
    private static final Pattern SCORE_HEADING_PATTERN = Pattern.compile(
            "评分办法|评标办法|评分标准|评分细则|评分规则|评审办法|评审标准");

    /** 表格分值特征：表头含分值列，或表格内容含数字+分 */
    private static final Pattern TABLE_SCORE_PATTERN = Pattern.compile(
            "分值|满分|得分|评分");

    private static final int CONTEXT_BEFORE_LINES = 2;
    private static final int CONTEXT_AFTER_LINES = 3;

    private MarkdownScoreSectionLocator() {
    }

    /**
     * 候选区域。
     *
     * @param content      区域文本（表格 + 前后文）
     * @param location     位置描述（行号区间，供 location 字段追溯）
     * @param sectionTitle 所属章节标题（无评分关键词标题时为 null）
     */
    public record ScoreSection(String content, String location, String sectionTitle) {
    }

    /** 定位全部评分标准候选区域 */
    public static List<ScoreSection> locate(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }
        String[] lines = markdown.split("\n", -1);
        List<ScoreSection> sections = new ArrayList<>();
        int i = 0;
        while (i < lines.length) {
            if (isTableLine(lines[i])) {
                int blockStart = i;
                while (i < lines.length && isTableLine(lines[i])) {
                    i++;
                }
                int blockEnd = i; // exclusive
                String blockText = joinLines(lines, blockStart, blockEnd);
                if (TABLE_SCORE_PATTERN.matcher(blockText).find()) {
                    sections.add(buildSection(lines, blockStart, blockEnd));
                }
            } else {
                i++;
            }
        }
        return sections;
    }

    private static boolean isTableLine(String line) {
        String trimmed = line == null ? "" : line.trim();
        return trimmed.startsWith("|");
    }

    private static ScoreSection buildSection(String[] lines, int blockStart, int blockEnd) {
        int from = Math.max(0, blockStart - CONTEXT_BEFORE_LINES);
        int to = Math.min(lines.length, blockEnd + CONTEXT_AFTER_LINES);
        String content = joinLines(lines, from, to);
        String location = "L" + (blockStart + 1) + "-L" + blockEnd;
        return new ScoreSection(content, location, findSectionTitle(lines, blockStart));
    }

    /** 表格前最近的标题行；含评分关键词才返回，否则 null */
    private static String findSectionTitle(String[] lines, int blockStart) {
        for (int i = blockStart - 1; i >= 0; i--) {
            String trimmed = lines[i] == null ? "" : lines[i].trim();
            if (trimmed.startsWith("#")) {
                String title = trimmed.replaceFirst("^#+\\s*", "");
                return SCORE_HEADING_PATTERN.matcher(title).find() ? title : null;
            }
        }
        return null;
    }

    private static String joinLines(String[] lines, int from, int to) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < to; i++) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(lines[i] == null ? "" : lines[i]);
        }
        return sb.toString();
    }
}
