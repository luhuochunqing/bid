// Input: 招标文件 Markdown 全文（doc-insight 转换产物）
// Output: 评分标准候选区域列表（含前后文与章节标题）
// Pos: scoreparse/infrastructure/structure — 纯静态方法，无框架依赖
// 维护声明: 维护者按项目SOP；spec 041 FR-001 召回二（文档结构解析）

package com.xiyu.bid.scoreparse.infrastructure.structure;

import com.xiyu.bid.biddraftagent.domain.PriceScoringKeywords;

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

    /** 表格分值特征：表头含分值列，或表格内容含数字+分；扩展报价参数特征防漏 */
    private static final Pattern TABLE_SCORE_PATTERN = Pattern.compile(
            "分值|满分|得分|评分|" + PriceScoringKeywords.SCORE_EXTENSION);
    /** 报价参数特征：参数块不含分值但紧邻评分表的报价参数块 */
    private static final Pattern PRICE_PARAM_PATTERN = PriceScoringKeywords.PRICE_PARAM_BLOCK;

    /**
     * 须知前附表判定特征：表头含"编列内容"（须知前附表专有列名）。
     * <p>须知前附表是采购程序说明表，含"综合评分"/"综合得分"字样但无评分规则，
     * 会误触发 TABLE_SCORE_PATTERN，须在结构层提前过滤。
     * <p>鉴别方式：检查表格首行，含"编列内容"且不含"评分因素"/"评分标准"列名时，
     * 认定为须知前附表并跳过。真正的评分标准表首行必含"评分因素"或"评分标准"列名。
     */
    private static final Pattern PROCEDURE_TABLE_MARKER = Pattern.compile("编列内容");
    /** 真正评分表的列名标志，用于区分须知前附表与评分标准表 */
    private static final Pattern SCORE_COLUMN_MARKER = Pattern.compile("评分因素|评分标准");

    private static final int CONTEXT_BEFORE_LINES = 2;
    /** 普通评分 section 上下文（保守，减少 token 消耗） */
    private static final int CONTEXT_AFTER_LINES = 3;
    /** 报价参数相关 section 上下文（报价参数表常跨页，需更多上下文保留完整性） */
    private static final int CONTEXT_AFTER_LINES_PRICE = 8;

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

    /** 下一章节标题格式：第X章、第X条。遇到则停止提取。 */
    private static final Pattern NEXT_CHAPTER_PATTERN = Pattern.compile(
            "^第[一二三四五六七八九十百千万\\d]+[章节条篇]");

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
                // 须知前附表过滤：表头含"编列内容"但不含"评分因素/评分标准"列名时，
                // 认定为采购程序说明表（须知前附表）并跳过，防止其中"综合评分"/"综合得分"
                // 字样误触发 TABLE_SCORE_PATTERN，将开启程序/保证金/公示等程序条款误入候选池。
                if (isProcedureNoticeTable(lines[blockStart])) {
                    continue;
                }
                String blockText = joinLines(lines, blockStart, blockEnd);
                boolean isScore = TABLE_SCORE_PATTERN.matcher(blockText).find();
                boolean isPriceParam = PRICE_PARAM_PATTERN.matcher(blockText).find();
                // 主评分特征直接收；报价参数块若紧邻已收评分块（块行距 ≤10）也收，
                // 防止 split 导致参数丢失；注：间距 < CONTEXT_AFTER_LINES 时相邻 section
                // 内容会重叠，重复候选由合并期子串判据去重
                if (isScore) {
                    int contextAfter = isPriceParam ? CONTEXT_AFTER_LINES_PRICE : CONTEXT_AFTER_LINES;
                    sections.add(buildSection(lines, blockStart, blockEnd, contextAfter));
                } else if (isPriceParam && !sections.isEmpty()) {
                    ScoreSection last = sections.get(sections.size() - 1);
                    int lastEnd = parseLocationEnd(last.location());
                    int gap = blockStart - lastEnd;
                    if (gap >= 0 && gap <= 10) {
                        sections.add(buildSection(lines, blockStart, blockEnd, CONTEXT_AFTER_LINES_PRICE));
                    } else if (PriceScoringKeywords.countDistinctLongParams(blockText) >= 2) {
                        // 孤立兜底收紧：≥2 个不同长词参数（嵌套词只计一次）才收，
                        // 防止仅含 "L1级" 类单一短词噪声的表被误收为评分区
                        sections.add(buildSection(lines, blockStart, blockEnd, CONTEXT_AFTER_LINES_PRICE));
                    }
                }
            } else {
                i++;
            }
        }

        // 兜底：若文档中没有 Markdown 管道表格（如纯文本），尝试按章节标题定位评分区域
        if (sections.isEmpty()) {
            sections.addAll(locatePlainChapterSections(lines));
        } else {
            // 补充：若已有评分块，再检查是否漏掉紧邻的报价参数纯文本段
            sections.addAll(collectAdjacentPriceParamBlocks(lines, sections));
        }

        return sections;
    }

    private static int parseLocationEnd(String location) {
        try {
            String endPart = location.substring(location.indexOf("-L") + 2);
            return Integer.parseInt(endPart);
        } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
            return -1000;
        }
    }

    private static List<ScoreSection> collectAdjacentPriceParamBlocks(String[] lines, List<ScoreSection> existing) {
        // 若评分表后紧跟的非表格文本含报价参数（下浮率/BP% 等），补充为候选区域
        // 避免参数因未被表格化而丢失
        List<ScoreSection> extra = new ArrayList<>();
        if (existing.isEmpty()) return extra;
        ScoreSection last = existing.get(existing.size() - 1);
        int lastEnd = parseLocationEnd(last.location());
        if (lastEnd < 0 || lastEnd >= lines.length) return extra;
        int scanStart = lastEnd; // lines index (0-based) after last block
        int scanEnd = Math.min(lines.length, scanStart + 12);
        StringBuilder sb = new StringBuilder();
        for (int i = scanStart; i < scanEnd; i++) {
            String line = lines[i] == null ? "" : lines[i].trim();
            if (line.isEmpty()) continue;
            if (isTableLine(lines[i])) break; // 已有表格块会单独处理
            if (PRICE_PARAM_PATTERN.matcher(line).find()) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(lines[i]);
            }
        }
        if (sb.length() >= 20) {
            extra.add(new ScoreSection(sb.toString(), "L" + (scanStart + 1) + "-L" + scanEnd, last.sectionTitle()));
        }
        return extra;
    }

    private static List<ScoreSection> locatePlainChapterSections(String[] lines) {
        List<ScoreSection> sections = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i] == null ? "" : lines[i].trim();
            if (trimmed.length() <= 40 && SCORE_HEADING_PATTERN.matcher(trimmed).find()
                    && !trimmed.contains("详见") && !trimmed.contains("参见")) {
                int start = i;
                int end = i + 1;
                int blankCount = 0;
                while (end < lines.length) {
                    String line = lines[end] == null ? "" : lines[end].trim();
                    if (line.isBlank()) {
                        blankCount++;
                        if (blankCount >= 4) {
                            break;
                        }
                    } else {
                        blankCount = 0;
                        if (end > start && NEXT_CHAPTER_PATTERN.matcher(line).find()
                                && !SCORE_HEADING_PATTERN.matcher(line).find()) {
                            break;
                        }
                    }
                    end++;
                }
                String content = joinLines(lines, start, end);
                if (content.length() >= 50 && TABLE_SCORE_PATTERN.matcher(content).find()) {
                    sections.add(new ScoreSection(content, "L" + (start + 1) + "-L" + end, trimmed));
                    i = end - 1;
                }
            }
        }
        return sections;
    }

    /**
     * 须知前附表判定：检查表格首行，含"编列内容"（须知前附表专有列名）
     * 且不含评分字段名（评分因素/评分标准）时，认定为采购程序说明表并返回 true。
     *
     * <p>须知前附表表头样例：{@code | 条款号 | 条款内容 | 编列内容 |}
     * <p>评分标准表表头样例：{@code | 条款号及名称 | 评审因素 | 评分标准 | 评分标准 |}
     */
    private static boolean isProcedureNoticeTable(String headerLine) {
        if (headerLine == null) return false;
        if (!PROCEDURE_TABLE_MARKER.matcher(headerLine).find()) return false;
        // 若同时含评分列名，可能是与评分信息混排的混合表，保留不跳过
        return !SCORE_COLUMN_MARKER.matcher(headerLine).find();
    }

    private static boolean isTableLine(String line) {
        String trimmed = line == null ? "" : line.trim();
        return trimmed.startsWith("|");
    }

    private static ScoreSection buildSection(String[] lines, int blockStart, int blockEnd, int contextAfter) {
        int from = Math.max(0, blockStart - CONTEXT_BEFORE_LINES);
        int to = Math.min(lines.length, blockEnd + contextAfter);
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
            if (trimmed.length() <= 40 && SCORE_HEADING_PATTERN.matcher(trimmed).find()) {
                return trimmed;
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
