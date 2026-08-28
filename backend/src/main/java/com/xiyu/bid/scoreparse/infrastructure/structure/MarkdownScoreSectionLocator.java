// Input: 招标文件 Markdown 全文（doc-insight 转换产物）
// Output: 评分标准候选区域列表（含前后文与章节标题）
// Pos: scoreparse/infrastructure/structure — 纯静态方法，无框架依赖
// 维护声明: 维护者按项目SOP；spec 041 FR-001 召回二（文档结构解析）

package com.xiyu.bid.scoreparse.infrastructure.structure;

import com.xiyu.bid.biddraftagent.domain.PriceScoringKeywords;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 文档结构召回定位器（spec 041 FR-001 召回二）。
 */
public final class MarkdownScoreSectionLocator {

    private static final Pattern SCORE_HEADING_PATTERN = Pattern.compile(
            "评分办法|评标办法|评分标准|评分细则|评分规则|评审办法|评审标准|详细评审表|详细评审");
    private static final Pattern CHAPTER_START_PATTERN = Pattern.compile(
            "(?:第[一二三四五六七八九十百千万\\d]+[章节篇]|[#]{1,4}\\s*|\\|\\s*(?:第[一二三四五六七八九十百千万\\d]+[章节篇]|[0-9]+)\\s*\\|\\s*)(?:[^\n]*?)(?:评分办法|评标办法|评分标准|评分细则|评分规则|评审办法|评审标准|详细评审表|详细评审)");
    private static final Pattern NEXT_MAJOR_CHAPTER_PATTERN = Pattern.compile(
            "^(?:第[一二三四五六七八九十百千万\\d]+[章节篇]|[#]{1,3}\\s+|\\|\\s*第[一二三四五六七八九十百千万\\d]+[章节篇]\\s*\\|)\\s*(?:合同|采购清单|技术规范|技术标准|技术要求|响应文件|响应函|投标文件|投标函|通用合同|专用合同|发包人要求|采购人要求|图纸)");
    private static final Pattern TABLE_SCORE_PATTERN = Pattern.compile("分值|满分|得分|评分");
    private static final Pattern PRICE_PARAM_PATTERN = PriceScoringKeywords.PRICE_PARAM_BLOCK;
    private static final Pattern PROCEDURE_TABLE_MARKER = Pattern.compile("编列内容");
    private static final Pattern SCORE_COLUMN_MARKER = Pattern.compile("评分因素|评分标准");

    private static final int CONTEXT_BEFORE_LINES = 2;
    private static final int CONTEXT_AFTER_LINES = 3;
    private static final int CONTEXT_AFTER_LINES_PRICE = 8;
    private static final int MAX_CHAPTER_LINES = 800;

    private MarkdownScoreSectionLocator() {
    }

    public record ScoreSection(String content, String location, String sectionTitle) {
    }

    /** 定位全部评分标准候选区域 */
    public static List<ScoreSection> locate(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }
        String[] lines = markdown.split("\n", -1);
        List<SectionRange> ranges = new ArrayList<>();
        scanChapterSections(lines, ranges);
        scanTableClusters(lines, ranges);
        collectAdjacentPriceParams(lines, ranges);
        return buildMergedSections(lines, ranges);
    }

    private record SectionRange(int start, int end, String title, int priority) {
    }

    private static void scanChapterSections(String[] lines, List<SectionRange> ranges) {
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i] == null ? "" : lines[i].trim();
            if (line.isEmpty() || line.contains("详见") || line.contains("参见")) continue;
            boolean isHeading = CHAPTER_START_PATTERN.matcher(line).find()
                    || (line.length() <= 50 && SCORE_HEADING_PATTERN.matcher(line).find() && !line.startsWith("|"));
            if (isHeading) {
                if (line.endsWith("...") || line.matches(".*\\.{4,}\\s*\\d+$")) continue;
                int start = i;
                int end = i + 1;
                int blankCount = 0;
                int nonScoreLineCount = 0;
                while (end < lines.length && (end - start) < MAX_CHAPTER_LINES) {
                    String curr = lines[end] == null ? "" : lines[end].trim();
                    if (curr.isBlank()) {
                        blankCount++;
                        if (blankCount >= 10) break;
                    } else {
                        blankCount = 0;
                        if (TABLE_SCORE_PATTERN.matcher(curr).find() || isTableLine(curr) || curr.contains("%")) {
                            nonScoreLineCount = 0;
                        } else if (++nonScoreLineCount >= 10 && end > i + 4) {
                            break;
                        }
                        if (end > i + 2) {
                            if (NEXT_MAJOR_CHAPTER_PATTERN.matcher(curr).find()) break;
                            if (curr.startsWith("#") && SCORE_HEADING_PATTERN.matcher(curr).find()) break;
                            if (CHAPTER_START_PATTERN.matcher(curr).find()
                                    && (curr.contains("标包") || curr.contains("标段") || curr.contains("经评审") || curr.contains("综合评分"))) {
                                break;
                            }
                        }
                    }
                    end++;
                }
                String content = joinLines(lines, start, end);
                if (content.length() >= 30 && (TABLE_SCORE_PATTERN.matcher(content).find() || content.contains("%"))) {
                    ranges.add(new SectionRange(start, end, cleanHeadingTitle(line), 10));
                    i = end - 1;
                }
            }
        }
    }

    private static void scanTableClusters(String[] lines, List<SectionRange> ranges) {
        int i = 0;
        while (i < lines.length) {
            if (isTableLine(lines[i])) {
                int blockStart = i;
                int blockEnd = i + 1;
                while (blockEnd < lines.length) {
                    if (isTableLine(lines[blockEnd])) {
                        blockEnd++;
                    } else {
                        int lookAhead = blockEnd;
                        boolean foundNextTable = false;
                        while (lookAhead < lines.length && (lookAhead - blockEnd) <= 4) {
                            if (isTableLine(lines[lookAhead])) {
                                foundNextTable = true;
                                break;
                            }
                            String lookText = lines[lookAhead] == null ? "" : lines[lookAhead].trim();
                            if (NEXT_MAJOR_CHAPTER_PATTERN.matcher(lookText).find() || CHAPTER_START_PATTERN.matcher(lookText).find()) break;
                            lookAhead++;
                        }
                        if (foundNextTable) blockEnd = lookAhead + 1;
                        else break;
                    }
                }
                if (!isProcedureNoticeTable(lines[blockStart]) && !isCoveredByChapter(ranges, blockStart, blockEnd)) {
                    String blockText = joinLines(lines, blockStart, blockEnd);
                    boolean isScore = TABLE_SCORE_PATTERN.matcher(blockText).find();
                    boolean isPriceParam = PRICE_PARAM_PATTERN.matcher(blockText).find()
                            && PriceScoringKeywords.countDistinctLongParams(blockText) >= 2;
                    if (isScore || isPriceParam) {
                        ranges.add(new SectionRange(
                                Math.max(0, blockStart - CONTEXT_BEFORE_LINES),
                                Math.min(lines.length, blockEnd + (isPriceParam ? CONTEXT_AFTER_LINES_PRICE : CONTEXT_AFTER_LINES)),
                                findSectionTitle(lines, blockStart),
                                5
                        ));
                    }
                }
                i = blockEnd;
            } else {
                i++;
            }
        }
    }

    private static boolean isCoveredByChapter(List<SectionRange> ranges, int blockStart, int blockEnd) {
        for (SectionRange r : ranges) {
            if (r.priority == 10 && blockStart >= r.start && blockEnd <= r.end) return true;
        }
        return false;
    }

    private static void collectAdjacentPriceParams(String[] lines, List<SectionRange> ranges) {
        if (ranges.isEmpty()) return;
        List<SectionRange> extras = new ArrayList<>();
        for (SectionRange r : ranges) {
            int scanStart = r.end;
            int scanEnd = Math.min(lines.length, scanStart + 12);
            StringBuilder sb = new StringBuilder();
            for (int i = scanStart; i < scanEnd; i++) {
                String line = lines[i] == null ? "" : lines[i].trim();
                if (line.isEmpty()) continue;
                if (isTableLine(lines[i])) break;
                if (PRICE_PARAM_PATTERN.matcher(line).find()) {
                    if (sb.length() > 0) sb.append('\n');
                    sb.append(lines[i]);
                }
            }
            if (sb.length() >= 20) extras.add(new SectionRange(scanStart, scanEnd, r.title, 3));
        }
        ranges.addAll(extras);
    }

    private static List<ScoreSection> buildMergedSections(String[] lines, List<SectionRange> ranges) {
        if (ranges.isEmpty()) return List.of();
        ranges.sort(Comparator.comparingInt(SectionRange::start).thenComparingInt((SectionRange r) -> -r.priority));
        List<SectionRange> merged = new ArrayList<>();
        for (SectionRange current : ranges) {
            if (merged.isEmpty()) {
                merged.add(current);
                continue;
            }
            SectionRange last = merged.get(merged.size() - 1);
            if (current.start < last.end) {
                int newEnd = Math.max(last.end, current.end);
                String title = last.title != null && !last.title.isBlank() ? last.title : current.title;
                merged.set(merged.size() - 1, new SectionRange(last.start, newEnd, title, Math.max(last.priority, current.priority)));
            } else {
                merged.add(current);
            }
        }
        List<ScoreSection> result = new ArrayList<>();
        for (SectionRange r : merged) {
            String content = joinLines(lines, r.start, r.end);
            if (!content.isBlank()) {
                result.add(new ScoreSection(content, "L" + (r.start + 1) + "-L" + r.end, r.title));
            }
        }
        return result;
    }

    private static String cleanHeadingTitle(String raw) {
        if (raw == null) return null;
        String clean = raw.replaceAll("[#|\\-_]", " ").trim().replaceAll("\\s{2,}", " ");
        return clean.length() > 60 ? clean.substring(0, 60) : clean;
    }

    private static boolean isProcedureNoticeTable(String headerLine) {
        if (headerLine == null) return false;
        if (!PROCEDURE_TABLE_MARKER.matcher(headerLine).find()) return false;
        return !SCORE_COLUMN_MARKER.matcher(headerLine).find();
    }

    private static boolean isTableLine(String line) {
        return line != null && line.trim().startsWith("|");
    }

    private static String findSectionTitle(String[] lines, int blockStart) {
        for (int i = blockStart - 1; i >= 0; i--) {
            String trimmed = lines[i] == null ? "" : lines[i].trim();
            if (trimmed.startsWith("#")) {
                String title = trimmed.replaceFirst("^#+\\s*", "");
                return SCORE_HEADING_PATTERN.matcher(title).find() ? title : null;
            }
            if (trimmed.length() <= 50 && SCORE_HEADING_PATTERN.matcher(trimmed).find()) {
                return cleanHeadingTitle(trimmed);
            }
        }
        return null;
    }

    private static String joinLines(String[] lines, int from, int to) {
        StringBuilder sb = new StringBuilder();
        int end = Math.min(lines.length, to);
        for (int i = Math.max(0, from); i < end; i++) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(lines[i] == null ? "" : lines[i]);
        }
        return sb.toString();
    }
}
